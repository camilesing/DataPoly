// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.executor;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import jakarta.annotation.PreDestroy;

import com.cs.core.util.AlarmEndpointGuard;

/**
 * HTTP client for the alarm webhook. Uses the JVM default TLS trust store and hostname
 * verification — alarm payloads carry business data and must not be interceptable via a
 * trust-all socket factory. Built on Apache HttpClient 5 (Spring 6 requires client5).
 */
@Slf4j
public class AlarmHttpRequestFactory extends HttpComponentsClientHttpRequestFactory {

    private int connectTimeout = 2 * 1000;
    private int socketTimeout = 60 * 1000;
    private int connectionRequestTimeout = 60 * 1000;
    private int maxConnectionSize = 20;
    private int maxPerRoute = 2;

    public AlarmHttpRequestFactory() {
        super();
        init();
    }

    private void init() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(socketTimeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(maxConnectionSize)
                        .setMaxConnPerRoute(maxPerRoute)
                        .build())
                .setDefaultRequestConfig(requestConfig)
                .setRedirectStrategy(new GuardedRedirectStrategy())
                .build();
        setHttpClient(httpClient);
    }

    /**
     * Follows a redirect only to a host the endpoint guard admits. The guard judges the configured
     * URL once, before the request leaves; a public webhook can still answer "302 → intranet" and the
     * alarm POST (and the response logged afterwards) would follow. A redirect that stays on the very
     * host and port already requested adds nothing to judge.
     */
    private static final class GuardedRedirectStrategy extends DefaultRedirectStrategy {

        @Override
        public boolean isRedirectAllowed(HttpHost redirect, HttpHost target, HttpRequest request,
                                         HttpContext context) {
            if (!redirect.equals(target) && !AlarmEndpointGuard.isEndpointHostAllowed(redirect.getHostName())) {
                log.warn("Alarm webhook redirect to a disallowed host is not followed: {}", redirect.getHostName());
                return false;
            }
            return super.isRedirectAllowed(redirect, target, request, context);
        }
    }

    /**
     * Releases the pooled connections of the wrapped client. The factory is instantiated
     * inline in several places; for Spring-managed instances (and any holder that honors
     * {@code @PreDestroy}) this closes the pool on shutdown instead of leaking it —
     * static holders live until JVM exit either way.
     */
    @PreDestroy
    public void close() {
        if (getHttpClient() instanceof CloseableHttpClient closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Failed to close alarm http client: {}", e.getMessage());
            }
        }
    }

}
