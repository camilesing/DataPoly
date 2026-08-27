// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.executor;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.*;
import org.apache.http.conn.socket.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import javax.net.ssl.SSLContext;
import java.security.*;

@Slf4j
public class AlarmHttpRequestFactory extends HttpComponentsClientHttpRequestFactory {

    private static final String SSL_PROTOCOL = "SSL";
    private static final TrustStrategy trustStrategy = (x509Certificates, authType) -> true;

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
        try {
            SSLContext sslContext = SSLContexts.custom().setProtocol(SSL_PROTOCOL)
                    .loadTrustMaterial(null, trustStrategy).build();
            SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setSSLSocketFactory(csf)
                    .setConnectionManager(poolingConnectionManager(csf))
                    .setDefaultRequestConfig(
                            RequestConfig.custom()
                                    .setConnectTimeout(connectTimeout)
                                    .setSocketTimeout(socketTimeout)
                                    .setConnectionRequestTimeout(connectionRequestTimeout)
                                    .build())
                    .build();
            setHttpClient(httpClient);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            log.warn("Failed to build CloseableHttpClient support https for RestTemplate. message:{}", e.getMessage());
        }
    }

    private PoolingHttpClientConnectionManager poolingConnectionManager(SSLConnectionSocketFactory csf) {
        Registry<ConnectionSocketFactory> registry =
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.INSTANCE)
                        .register("https", csf)
                        .build();
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(registry);
        manager.setMaxTotal(maxConnectionSize);
        manager.setDefaultMaxPerRoute(maxPerRoute);
        return manager;
    }

}
