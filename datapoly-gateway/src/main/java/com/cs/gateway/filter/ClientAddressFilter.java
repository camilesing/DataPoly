// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.gateway.filter;

import cn.hutool.json.JSONUtil;
import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.util.*;
import com.cs.core.gateway.FirewallFilterService;
import com.cs.core.util.LocaleUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.server.reactive.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ClientAddressFilter implements GlobalFilter, Ordered {

    private static final String TOKEN_PATH = "/token/generate";
    private static final long RATE_WINDOW_MILLIS = 60 * 1000L;
    private static final int RATE_TRACK_MAX_SIZE = 10000;
    private static final MediaType JSON_CONTENT_TYPE =
            MediaType.parseMediaType("application/json;charset=UTF-8");

    /**
     * Firewall rule refresh cron (A6 fix): every 30 seconds. The old default {@code 0/30 0 * * * ?} (six-field)
     * actually fired only at second 0/30 of minute 0 each hour (twice an hour), leaving rules up to ~30 minutes stale.
     */
    public static final String DEFAULT_FIREWALL_CRON = "*/30 * * * * ?";

    @Resource
    private FirewallFilterService firewallFilterService;

    /**
     * Per-IP per-minute rate limit for /token/generate (S4), non-blocking fixed-window implementation.
     */
    @Value("${datapoly.gateway.token-rate-limit-per-minute:20}")
    private int tokenRateLimitPerMinute;

    private final ConcurrentHashMap<String, TokenRateWindow> tokenRateWindows = new ConcurrentHashMap<>();

    /* Runs every 30 seconds */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${cron.firewall.expression:" + DEFAULT_FIREWALL_CRON + "}")
    public void loadFlowRules() {
        try {
            firewallFilterService.refresh();
        } catch (Exception e) {
            log.error("load firewall rules failed:{}", e.getMessage(), e);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (firewallFilterService.isLoaded()) {
            // Fast path: rules loaded, canAccess is pure in-memory check, run directly on the event loop
            return doFilter(exchange, chain, firewallFilterService.canAccess(
                    exchange.getRequest().getRemoteAddress().getHostString()));
        }
        // Cold path: rules not yet loaded; blocking JDBC inside canAccess is offloaded to boundedElastic, never the event loop (A6)
        String clientHostAddr = exchange.getRequest().getRemoteAddress().getHostString();
        return Mono.fromCallable(() -> firewallFilterService.canAccess(clientHostAddr))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(allowed -> doFilter(exchange, chain, allowed))
                .onErrorResume(t -> {
                    // fail-closed: reject when rules unavailable (503); exception details go to logs only (H1 style)
                    log.error("Firewall rules unavailable for client : {}, path : {}", clientHostAddr,
                            exchange.getRequest().getURI().getPath(), t);
                    Locale locale = LocaleUtils.resolveLocale(exchange.getRequest());
                    ResultEntity data = ResultEntity.failed(ResponseErrorCode.ERROR_INTERNAL_ERROR,
                            I18nUtils.getMessage("exception.ERROR_INTERNAL_ERROR", locale));
                    return writeJson(exchange.getResponse(), data, HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    private Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain, boolean accessible) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        String clientHostAddr = request.getRemoteAddress().getHostString();
        if (!accessible) {
            log.info("Forbidden access for client : {}, path : {}, method : {}", clientHostAddr, path, method);
            Locale locale = LocaleUtils.resolveLocale(request);
            String message = I18nUtils.getMessage("exception.ERROR_CLIENT_FORBIDDEN", locale) + ", " + clientHostAddr;
            ResultEntity data = ResultEntity.failed(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, message);
            return writeJson(response, data, HttpStatus.FORBIDDEN);
        }

        if (TOKEN_PATH.equals(path) && !isTokenRequestAllowed(clientHostAddr)) {
            log.warn("Token generate rate limited for client : {}", clientHostAddr);
            Locale locale = LocaleUtils.resolveLocale(request);
            ResultEntity data = ResultEntity.failed(
                    ResponseErrorCode.ERROR_TOO_MANY_REQUESTS,
                    I18nUtils.getMessage("token.rate.limited", locale));
            return writeJson(response, data, HttpStatus.TOO_MANY_REQUESTS);
        }

        log.info("access api from client : {}, path : {}, method : {}", clientHostAddr, path, method);
        ServerHttpRequest newRequest = request.mutate()
                .header(Constants.REQUEST_HEADER_GATEWAY_IP, InetUtils.getLocalIpStr())
                .build();
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    private boolean isTokenRequestAllowed(String clientHostAddr) {
        if (tokenRateLimitPerMinute <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        pruneStaleWindows(now);
        TokenRateWindow window = tokenRateWindows.compute(clientHostAddr, (k, w) -> {
            if (null == w || !w.isInWindow(now)) {
                return new TokenRateWindow(now);
            }
            return w;
        });
        return window.incrementAndGet() <= tokenRateLimitPerMinute;
    }

    private void pruneStaleWindows(long now) {
        if (tokenRateWindows.size() <= RATE_TRACK_MAX_SIZE) {
            return;
        }
        for (Iterator<Map.Entry<String, TokenRateWindow>> it = tokenRateWindows.entrySet().iterator();
             it.hasNext(); ) {
            if (!it.next().getValue().isInWindow(now)) {
                it.remove();
            }
        }
    }

    private Mono<Void> writeJson(ServerHttpResponse response, ResultEntity data, HttpStatus status) {
        if (null != status) {
            response.setStatusCode(status);
        }
        response.getHeaders().setContentType(JSON_CONTENT_TYPE);
        String json = JSONUtil.toJsonStr(data);
        DataBuffer wrap = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(wrap));
    }

    private static final class TokenRateWindow {

        private final long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private TokenRateWindow(long now) {
            this.windowStart = now;
        }

        private boolean isInWindow(long now) {
            return now - windowStart < RATE_WINDOW_MILLIS;
        }

        private int incrementAndGet() {
            return count.incrementAndGet();
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }


}
