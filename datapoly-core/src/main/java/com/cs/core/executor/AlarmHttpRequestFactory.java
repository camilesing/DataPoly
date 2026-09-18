// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.executor;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

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
                .build();
        setHttpClient(httpClient);
    }

}
