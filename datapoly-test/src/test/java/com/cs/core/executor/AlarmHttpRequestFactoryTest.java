// Use of this source code is governed by a BSD-style license
package com.cs.core.executor;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * The alarm webhook client must not turn a redirect into a request the endpoint guard never saw:
 * the guard judges the configured URL once, a public webhook can still answer "302 → intranet".
 */
public class AlarmHttpRequestFactoryTest {

    private HttpServer webhook;
    private HttpServer intranet;
    private final AtomicInteger intranetHits = new AtomicInteger();
    private AlarmHttpRequestFactory factory;

    @Before
    public void setUp() throws IOException {
        intranet = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        intranet.createContext("/latest/meta-data", exchange -> {
            intranetHits.incrementAndGet();
            byte[] body = "secret".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpStatus.OK.value(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        intranet.start();

        webhook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // the webhook answers with a redirect into the intranet / cloud metadata service
        webhook.createContext("/hook", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "http://127.0.0.1:" + intranet.getAddress().getPort() + "/latest/meta-data");
            exchange.sendResponseHeaders(HttpStatus.FOUND.value(), -1);
            exchange.close();
        });
        // and with a redirect that stays on its own host
        webhook.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/hook-final");
            exchange.sendResponseHeaders(HttpStatus.TEMPORARY_REDIRECT.value(), -1);
            exchange.close();
        });
        webhook.createContext("/hook-final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpStatus.OK.value(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        webhook.start();

        factory = new AlarmHttpRequestFactory();
    }

    @After
    public void tearDown() {
        webhook.stop(0);
        intranet.stop(0);
        factory.close();
    }

    private ResponseEntity<String> post(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<byte[]> entity = new HttpEntity<>("{}".getBytes(StandardCharsets.UTF_8), headers);
        return new RestTemplate(factory).postForEntity(
                "http://127.0.0.1:" + webhook.getAddress().getPort() + path, entity, String.class);
    }

    @Test
    public void aRedirectToADisallowedHostIsNotFollowed() {
        ResponseEntity<String> response = post("/hook");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("the client must never reach the redirect target", 0, intranetHits.get());
    }

    @Test
    public void aRedirectThatStaysOnTheWebhookHostIsStillFollowed() {
        ResponseEntity<String> response = post("/moved");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ok", response.getBody());
    }
}
