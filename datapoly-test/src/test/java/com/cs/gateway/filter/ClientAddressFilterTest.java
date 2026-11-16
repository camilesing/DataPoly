// Use of this source code is governed by a BSD-style license
package com.cs.gateway.filter;

import com.cs.common.consts.Constants;
import com.cs.core.gateway.FirewallFilterService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Hooks;

import org.reactivestreams.Publisher;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/**
 * ClientAddressFilter behavior without a Spring container: firewall allow/deny on both the
 * hot (rules loaded) and cold (lazy load offloaded to boundedElastic) paths, fail-closed 503
 * when rules are unavailable, the /token/generate per-IP rate limit, and rule refresh error handling.
 * Hand-written reactive fakes stand in for ServerWebExchange/ServerHttpRequest/ServerHttpResponse
 * (spring-test is not on the test classpath).
 */
public class ClientAddressFilterTest {

    private ClientAddressFilter filter;
    private FakeFirewallService firewall;
    private RecordingChain chain;

    @Before
    public void setUp() throws Exception {
        filter = new ClientAddressFilter();
        firewall = new FakeFirewallService();
        chain = new RecordingChain();
        inject("firewallFilterService", firewall);
        // rate limit disabled by default so only the tests that care about it turn it on
        inject("tokenRateLimitPerMinute", 0);
        Hooks.onErrorDropped(e -> {
        });
    }

    /* --------------------------- hot path: rules loaded --------------------------- */

    @Test
    public void allowedRequestPassesChainAndStampsGatewayHeader() throws Exception {
        firewall.loaded = true;
        firewall.accessible = true;

        FakeExchange exchange = exchange("GET", "/api/demo", "10.1.2.3");
        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(1, chain.calls.size());
        ServerHttpRequest seen = chain.calls.get(0).getRequest();
        assertNotNull(seen.getHeaders().getFirst(Constants.REQUEST_HEADER_GATEWAY_IP));
        assertNotNull("gateway IP header must be a real address",
                java.net.InetAddress.getByName(seen.getHeaders().getFirst(Constants.REQUEST_HEADER_GATEWAY_IP)));
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    public void blockedClientGets403JsonAndStopsChain() {
        firewall.loaded = true;
        firewall.accessible = false;

        FakeExchange exchange = exchange("POST", "/api/x", "10.9.9.9");
        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals("blocked request must not reach the chain", 0, chain.calls.size());
        FakeResponse response = exchange.getResponse();
        assertEquals(403, response.getStatusCode().value());
        assertEquals("application/json;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertTrue("body must carry the 403 code, was: " + response.getBody(),
                response.getBody().contains("\"code\":403"));
        assertTrue("body must mention the denied client address", response.getBody().contains("10.9.9.9"));
    }

    @Test
    public void getOrderIsZero() {
        assertEquals(0, filter.getOrder());
    }

    /* ---------------------- /token/generate per-IP rate limit ---------------------- */

    @Test
    public void tokenGenerateRateLimitedAfterConfiguredPerMinute() throws Exception {
        firewall.loaded = true;
        firewall.accessible = true;
        inject("tokenRateLimitPerMinute", 2);

        for (int i = 0; i < 2; i++) {
            FakeExchange exchange = exchange("POST", "/token/generate", "203.0.113.7");
            filter.filter(exchange, chain).block(Duration.ofSeconds(5));
            assertNull("request " + (i + 1) + " within the limit must pass",
                    exchange.getResponse().getStatusCode());
        }
        assertEquals(2, chain.calls.size());

        FakeExchange third = exchange("POST", "/token/generate", "203.0.113.7");
        filter.filter(third, chain).block(Duration.ofSeconds(5));
        assertEquals(429, third.getResponse().getStatusCode().value());
        assertTrue(third.getResponse().getBody().contains("\"code\":429"));
        assertEquals("the limited request must not reach the chain", 2, chain.calls.size());

        // other paths from the same client are unaffected by the token-path window
        FakeExchange otherPath = exchange("GET", "/api/other", "203.0.113.7");
        filter.filter(otherPath, chain).block(Duration.ofSeconds(5));
        assertNull(otherPath.getResponse().getStatusCode());
        assertEquals(3, chain.calls.size());

        // a different client has its own window
        FakeExchange otherClient = exchange("POST", "/token/generate", "198.51.100.9");
        filter.filter(otherClient, chain).block(Duration.ofSeconds(5));
        assertNull(otherClient.getResponse().getStatusCode());
        assertEquals(4, chain.calls.size());
    }

    @Test
    public void zeroRateLimitDisablesWindow() throws Exception {
        firewall.loaded = true;
        firewall.accessible = true;
        inject("tokenRateLimitPerMinute", 0);

        for (int i = 0; i < 12; i++) {
            FakeExchange exchange = exchange("POST", "/token/generate", "203.0.113.8");
            filter.filter(exchange, chain).block(Duration.ofSeconds(5));
            assertNull(exchange.getResponse().getStatusCode());
        }
        assertEquals(12, chain.calls.size());
    }

    /* ---------------- cold path: rules not loaded, lazy evaluation ---------------- */

    @Test
    public void coldPathOffloadsCheckAndAllows() {
        firewall.loaded = false;
        firewall.accessible = true;

        FakeExchange exchange = exchange("GET", "/api/cold", "10.0.0.1");
        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertTrue("lazy evaluation must consult the firewall service", firewall.canAccessCalls.contains("10.0.0.1"));
        assertEquals(1, chain.calls.size());
    }

    @Test
    public void coldPathBlockedWrites403() {
        firewall.loaded = false;
        firewall.accessible = false;

        FakeExchange exchange = exchange("GET", "/api/cold", "10.0.0.2");
        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(403, exchange.getResponse().getStatusCode().value());
        assertEquals(0, chain.calls.size());
    }

    @Test
    public void coldPathRulesUnavailableFailsClosedWith503() {
        firewall.loaded = false;
        firewall.accessible = false;
        firewall.throwOnCanAccess = new IllegalStateException("db down");

        FakeExchange exchange = exchange("GET", "/api/cold", "10.0.0.3");
        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals("fail-closed: unavailable rules reject with 503",
                503, exchange.getResponse().getStatusCode().value());
        assertTrue(exchange.getResponse().getBody().contains("\"code\":1"));
        assertEquals(0, chain.calls.size());
        assertEquals("application/json;charset=UTF-8",
                exchange.getResponse().getHeaders().getContentType().toString());
    }

    @Test
    public void staleRateWindowsArePrunedAboveTrackLimit() throws Exception {
        inject("tokenRateLimitPerMinute", 1);
        // fill the tracker past RATE_TRACK_MAX_SIZE with windows that expired 2 minutes ago
        Field windowsField = ClientAddressFilter.class.getDeclaredField("tokenRateWindows");
        windowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, Object> windows =
                (java.util.concurrent.ConcurrentHashMap<String, Object>) windowsField.get(filter);
        Class<?> windowClass = Class.forName(
                "com.cs.gateway.filter.ClientAddressFilter$TokenRateWindow");
        java.lang.reflect.Constructor<?> ctor = windowClass.getDeclaredConstructor(long.class);
        ctor.setAccessible(true);
        long stale = System.currentTimeMillis() - 120_000L;
        for (int i = 0; i <= 10000; i++) {
            windows.put("stale-" + i, ctor.newInstance(stale));
        }

        java.lang.reflect.Method allowed = ClientAddressFilter.class
                .getDeclaredMethod("isTokenRequestAllowed", String.class);
        allowed.setAccessible(true);
        Boolean result = (Boolean) allowed.invoke(filter, "fresh-ip");
        assertTrue(result);
        assertEquals("only the fresh window must survive the prune", 1, windows.size());
        assertTrue(windows.containsKey("fresh-ip"));
    }

    @Test
    public void staleWindowIsReplacedByAFreshOne() throws Exception {
        inject("tokenRateLimitPerMinute", 2);
        Field windowsField = ClientAddressFilter.class.getDeclaredField("tokenRateWindows");
        windowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, Object> windows =
                (java.util.concurrent.ConcurrentHashMap<String, Object>) windowsField.get(filter);
        Class<?> windowClass = Class.forName(
                "com.cs.gateway.filter.ClientAddressFilter$TokenRateWindow");
        java.lang.reflect.Constructor<?> ctor = windowClass.getDeclaredConstructor(long.class);
        ctor.setAccessible(true);
        // below the prune threshold, so the stale entry survives until the compute sees it
        windows.put("stale-ip", ctor.newInstance(System.currentTimeMillis() - 120_000L));

        java.lang.reflect.Method allowed = ClientAddressFilter.class
                .getDeclaredMethod("isTokenRequestAllowed", String.class);
        allowed.setAccessible(true);
        assertTrue((Boolean) allowed.invoke(filter, "stale-ip"));
        // the stale window was replaced by a fresh counter starting from 1
        Object window = windows.get("stale-ip");
        java.lang.reflect.Method count = windowClass.getDeclaredMethod("incrementAndGet");
        count.setAccessible(true);
        assertEquals(2, ((Integer) count.invoke(window)).intValue());
    }

    @Test
    public void pruneKeepsFreshWindowsAndDropsStaleOnes() throws Exception {
        inject("tokenRateLimitPerMinute", 1);
        Field windowsField = ClientAddressFilter.class.getDeclaredField("tokenRateWindows");
        windowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, Object> windows =
                (java.util.concurrent.ConcurrentHashMap<String, Object>) windowsField.get(filter);
        Class<?> windowClass = Class.forName(
                "com.cs.gateway.filter.ClientAddressFilter$TokenRateWindow");
        java.lang.reflect.Constructor<?> ctor = windowClass.getDeclaredConstructor(long.class);
        ctor.setAccessible(true);
        long now = System.currentTimeMillis();
        long stale = now - 120_000L;
        for (int i = 0; i < 10000; i++) {
            windows.put("stale-" + i, ctor.newInstance(stale));
        }
        for (int i = 0; i < 10; i++) {
            windows.put("fresh-" + i, ctor.newInstance(now));
        }

        java.lang.reflect.Method allowed = ClientAddressFilter.class
                .getDeclaredMethod("isTokenRequestAllowed", String.class);
        allowed.setAccessible(true);
        assertTrue((Boolean) allowed.invoke(filter, "prune-test"));
        // the 10 fresh windows survive; every stale one is dropped; plus the new window
        assertEquals(11, windows.size());
    }

    /* ------------------------------ rule refresh cron ------------------------------ */

    @Test
    public void loadFlowRulesRefreshesRules() {
        firewall.loaded = true;
        filter.loadFlowRules();
        assertEquals(1, firewall.refreshCalls);
    }

    @Test
    public void loadFlowRulesSwallowsRefreshFailure() {
        firewall.throwOnRefresh = new IllegalStateException("dao down");
        filter.loadFlowRules();
        assertEquals(1, firewall.refreshCalls);
    }

    /* ---------------------------------- helpers ---------------------------------- */

    private void inject(String name, Object value) throws Exception {
        Field field = ClientAddressFilter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(filter, value);
    }

    private FakeExchange exchange(String method, String path, String remoteIp) {
        return new FakeExchange(method, path, remoteIp);
    }

    private static class FakeFirewallService extends FirewallFilterService {
        boolean loaded;
        boolean accessible;
        int refreshCalls;
        final List<String> canAccessCalls = new CopyOnWriteArrayList<>();
        RuntimeException throwOnCanAccess;
        RuntimeException throwOnRefresh;

        @Override
        public boolean isLoaded() {
            return loaded;
        }

        @Override
        public boolean canAccess(String address) {
            canAccessCalls.add(address);
            if (null != throwOnCanAccess) {
                throw throwOnCanAccess;
            }
            return accessible;
        }

        @Override
        public void refresh() {
            refreshCalls++;
            if (null != throwOnRefresh) {
                throw throwOnRefresh;
            }
        }
    }

    private static class RecordingChain implements GatewayFilterChain {
        final List<ServerWebExchange> calls = new CopyOnWriteArrayList<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            calls.add(exchange);
            return Mono.empty();
        }
    }

    /* Hand-written reactive request: only what ClientAddressFilter touches. */
    private static class FakeRequest implements ServerHttpRequest {
        private final HttpMethod method;
        private final URI uri;
        private final HttpHeaders headers = new HttpHeaders();
        private final InetSocketAddress remoteAddress;

        FakeRequest(HttpMethod method, URI uri, InetSocketAddress remoteAddress) {
            this.method = method;
            this.uri = uri;
            this.remoteAddress = remoteAddress;
        }

        @Override
        public String getId() {
            return "test-1";
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return new HashMap<>();
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.empty();
        }

        @Override
        public RequestPath getPath() {
            return RequestPath.parse(uri, "");
        }

        @Override
        public MultiValueMap<String, String> getQueryParams() {
            return new LinkedMultiValueMap<>();
        }

        @Override
        public MultiValueMap<String, org.springframework.http.HttpCookie> getCookies() {
            return new LinkedMultiValueMap<>();
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public Builder mutate() {
            return new FakeRequestBuilder(this);
        }
    }

    private static class FakeRequestBuilder implements ServerHttpRequest.Builder {
        private final FakeRequest delegate;

        FakeRequestBuilder(FakeRequest delegate) {
            this.delegate = delegate;
        }

        @Override
        public ServerHttpRequest.Builder method(HttpMethod httpMethod) {
            return this;
        }

        @Override
        public ServerHttpRequest.Builder uri(URI uri) {
            return this;
        }

        @Override
        public ServerHttpRequest.Builder path(String path) {
            return this;
        }

        @Override
        public ServerHttpRequest.Builder contextPath(String contextPath) {
            return this;
        }

        @Override
        public ServerHttpRequest.Builder header(String headerName, String... headerValues) {
            delegate.getHeaders().put(headerName, new java.util.ArrayList<>(java.util.Arrays.asList(headerValues)));
            return this;
        }

        @Override
        public ServerHttpRequest.Builder headers(Consumer<HttpHeaders> headersConsumer) {
            headersConsumer.accept(delegate.getHeaders());
            return this;
        }

        @Override
        public ServerHttpRequest.Builder sslInfo(org.springframework.http.server.reactive.SslInfo sslInfo) {
            return this;
        }

        @Override
        public ServerHttpRequest.Builder remoteAddress(InetSocketAddress remoteAddress) {
            return this;
        }

        @Override
        public ServerHttpRequest build() {
            // the filter only mutates headers; hand back the same instance holding the new headers
            return delegate;
        }
    }

    private static class FakeResponse implements ServerHttpResponse {
        private HttpStatusCode statusCode;
        private final HttpHeaders headers = new HttpHeaders();
        private final StringBuilder body = new StringBuilder();
        private boolean committed;

        @Override
        public boolean setStatusCode(HttpStatusCode status) {
            this.statusCode = status;
            return true;
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return statusCode;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public MultiValueMap<String, ResponseCookie> getCookies() {
            return new LinkedMultiValueMap<>();
        }

        @Override
        public void addCookie(ResponseCookie cookie) {
        }

        @Override
        public DefaultDataBufferFactory bufferFactory() {
            return DefaultDataBufferFactory.sharedInstance;
        }

        @Override
        public void beforeCommit(Supplier<? extends Mono<Void>> action) {
        }

        @Override
        public boolean isCommitted() {
            return committed;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> bodyPublisher) {
            return Flux.from(bodyPublisher).doOnNext(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                body.append(new String(bytes, StandardCharsets.UTF_8));
            }).doOnComplete(() -> committed = true).then();
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> bodyWithFlush) {
            return Flux.from(bodyWithFlush).flatMap(this::writeWith).then();
        }

        @Override
        public Mono<Void> setComplete() {
            committed = true;
            return Mono.empty();
        }

        String getBody() {
            return body.toString();
        }
    }

    private static class FakeExchange implements ServerWebExchange {
        private final FakeRequest request;
        private final FakeResponse response = new FakeResponse();

        FakeExchange(String method, String path, String remoteIp) {
            URI uri = URI.create("http://" + remoteIp + ":8080" + path);
            this.request = new FakeRequest(HttpMethod.valueOf(method), uri,
                    new InetSocketAddress(remoteIp, 34567));
        }

        FakeExchange(FakeRequest request) {
            this.request = request;
        }

        @Override
        public ServerHttpRequest getRequest() {
            return request;
        }

        @Override
        public FakeResponse getResponse() {
            return response;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return new HashMap<>();
        }

        @Override
        public Mono<org.springframework.web.server.WebSession> getSession() {
            return Mono.empty();
        }

        @Override
        public <T extends Principal> Mono<T> getPrincipal() {
            return Mono.empty();
        }

        @Override
        public Mono<MultiValueMap<String, String>> getFormData() {
            return Mono.empty();
        }

        @Override
        public Mono<MultiValueMap<String, org.springframework.http.codec.multipart.Part>> getMultipartData() {
            return Mono.empty();
        }

        @Override
        public LocaleContext getLocaleContext() {
            return () -> java.util.Locale.US;
        }

        @Override
        public org.springframework.context.ApplicationContext getApplicationContext() {
            return null;
        }

        @Override
        public boolean isNotModified() {
            return false;
        }

        @Override
        public boolean checkNotModified(java.time.Instant lastModified) {
            return false;
        }

        @Override
        public boolean checkNotModified(String etag) {
            return false;
        }

        @Override
        public boolean checkNotModified(String etag, java.time.Instant lastModified) {
            return false;
        }

        @Override
        public String transformUrl(String url) {
            return url;
        }

        @Override
        public void addUrlTransformer(Function<String, String> transformer) {
        }

        @Override
        public String getLogPrefix() {
            return "";
        }

        @Override
        public Builder mutate() {
            return new FakeExchangeBuilder(this);
        }
    }

    private static class FakeExchangeBuilder implements ServerWebExchange.Builder {
        private final FakeExchange delegate;
        private ServerHttpRequest request;

        FakeExchangeBuilder(FakeExchange delegate) {
            this.delegate = delegate;
        }

        @Override
        public ServerWebExchange.Builder request(Consumer<ServerHttpRequest.Builder> requestBuilderConsumer) {
            ServerHttpRequest.Builder builder = delegate.getRequest().mutate();
            requestBuilderConsumer.accept(builder);
            this.request = builder.build();
            return this;
        }

        @Override
        public ServerWebExchange.Builder request(ServerHttpRequest serverHttpRequest) {
            this.request = serverHttpRequest;
            return this;
        }

        @Override
        public ServerWebExchange.Builder response(ServerHttpResponse serverHttpResponse) {
            return this;
        }

        @Override
        public ServerWebExchange.Builder principal(Mono<Principal> principalMono) {
            return this;
        }

        @Override
        public ServerWebExchange build() {
            FakeExchange mutated = new FakeExchange(delegate.request);
            mutated.request.headers.putAll(null != request ? request.getHeaders()
                    : delegate.getRequest().getHeaders());
            return mutated;
        }
    }
}
