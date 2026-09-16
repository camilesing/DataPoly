// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.executor;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * HTTP client for the alarm webhook. Uses the JVM default TLS trust store and hostname
 * verification — alarm payloads carry business data and must not be interceptable via a
 * trust-all socket factory.
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
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(maxConnectionSize);
        manager.setDefaultMaxPerRoute(maxPerRoute);
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(
                        RequestConfig.custom()
                                .setConnectTimeout(connectTimeout)
                                .setSocketTimeout(socketTimeout)
                                .setConnectionRequestTimeout(connectionRequestTimeout)
                                .build())
                .build();
        setHttpClient(httpClient);
    }

}
