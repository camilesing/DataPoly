// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import org.junit.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Script-facing {@code http} module against a minimal loopback HTTP server: builder state
 * (params/data/headers/body/content type), the GET/POST/PUT/DELETE entry points, response
 * type expectations, query-string assembly rules and error-status propagation. The module
 * is intentionally not a Spring bean; each instance is per-execution state.
 */
public class HttpModuleTest {

    /** Captured request line, headers and body of one incoming request. */
    static final class Captured {
        String method;
        String target; // path + query string
        final Map<String, String> headers = new LinkedHashMap<>();
        String body = "";
    }

    /** Single-connection-at-a-time loopback HTTP/1.1 server with canned responses. */
    static final class LocalHttpServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread acceptor;
        private final List<Captured> requests = new CopyOnWriteArrayList<>();
        volatile String status = "200 OK";
        volatile String contentType = "text/plain; charset=UTF-8";
        volatile byte[] body = "ok".getBytes(StandardCharsets.UTF_8);

        LocalHttpServer() throws IOException {
            serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            acceptor = new Thread(this::acceptLoop, "http-module-test-server");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String url() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/svc";
        }

        List<Captured> requests() {
            return requests;
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (SocketException e) {
                    return; // closed by shutdown()
                } catch (IOException e) {
                    // tolerate client-side aborts
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            socket.setSoTimeout(10000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            Captured captured = new Captured();
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            captured.method = parts[0];
            captured.target = parts.length > 1 ? parts[1] : "";
            int contentLength = 0;
            boolean chunked = false;
            String line;
            while (null != (line = readLine(in)) && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    captured.headers.put(line.substring(0, idx).trim().toLowerCase(),
                            line.substring(idx + 1).trim());
                }
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } else if (lower.startsWith("transfer-encoding:") && lower.contains("chunked")) {
                    chunked = true;
                }
            }
            if (chunked) {
                captured.body = readChunked(in);
            } else if (contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = in.read(bodyBytes, read, contentLength - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                captured.body = new String(bodyBytes, 0, read, StandardCharsets.UTF_8);
            }
            requests.add(captured);
            byte[] payload = body;
            OutputStream out = socket.getOutputStream();
            String head = "HTTP/1.1 " + status + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + payload.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
        }

        /** Minimal chunked-transfer decoding: hex size CRLF data CRLF, terminated by a zero chunk. */
        private static String readChunked(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (true) {
                String sizeLine = readLine(in);
                if (null == sizeLine || sizeLine.isEmpty()) {
                    break;
                }
                int semi = sizeLine.indexOf(';');
                int size = Integer.parseInt(semi < 0 ? sizeLine.trim() : sizeLine.substring(0, semi).trim(), 16);
                if (0 == size) {
                    break;
                }
                byte[] chunk = new byte[size];
                int read = 0;
                while (read < size) {
                    int n = in.read(chunk, read, size - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                out.write(chunk, 0, read);
                readLine(in); // trailing CRLF after each chunk
            }
            return out.toString(StandardCharsets.UTF_8);
        }

        private static String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int b;
            while (-1 != (b = in.read())) {
                if (b == '\n') {
                    int len = sb.length();
                    if (len > 0 && sb.charAt(len - 1) == '\r') {
                        sb.setLength(len - 1);
                    }
                    return sb.toString();
                }
                sb.append((char) b);
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }

    private LocalHttpServer server;

    @Before
    public void setUp() throws IOException {
        server = new LocalHttpServer();
    }

    @After
    public void tearDown() throws IOException {
        if (null != server) {
            server.close();
        }
    }

    @Test
    public void varModuleNameIsHttp() {
        Assert.assertEquals("http", new HttpModule().getVarModuleName());
    }

    @Test
    public void getBuildsQueryStringAndSendsHeaders() {
        ResponseEntity<?> entity = new HttpModule(server.url())
                .param("a", 1)
                .param("b", "xy")
                .header("X-Trace", "t1")
                .expectString()
                .get();
        Assert.assertEquals(200, entity.getStatusCodeValue());
        Assert.assertEquals("ok", entity.getBody());
        Captured captured = server.requests().get(0);
        Assert.assertEquals("GET", captured.method);
        Assert.assertTrue("query string must carry both params: " + captured.target,
                captured.target.endsWith("/svc?a=1&b=xy"));
        Assert.assertEquals("t1", captured.headers.get("x-trace"));
    }

    @Test
    public void urlConstructorAndConnectTargetTheSameAddress() {
        HttpModule viaConnect = new HttpModule().connect(server.url());
        Assert.assertNotSame("connect returns a fresh per-request module",
                viaConnect, new HttpModule());
        viaConnect.expectString().get();
        Assert.assertTrue(server.requests().get(0).target.startsWith("/svc"));
    }

    @Test
    public void mapHelpersPopulateParamsDataAndHeaders() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("k1", "v1");
        values.put("k2", null);
        ResponseEntity<?> entity = new HttpModule(server.url())
                .param(values)
                .header(values) // null value must be filtered out
                .contentType(MediaType.TEXT_PLAIN)
                .expectString()
                .get();
        Assert.assertEquals(200, entity.getStatusCodeValue());
        Captured captured = server.requests().get(0);
        Assert.assertTrue(captured.target.endsWith("/svc?k1=v1&k2="));
        Assert.assertEquals("headers with a value must be sent", "v1", captured.headers.get("k1"));
        Assert.assertFalse("header with null value must not be sent", captured.headers.containsKey("k2"));
        Assert.assertEquals("text/plain", captured.headers.get("content-type"));
    }

    @Test
    public void paramWithNullVarargsIsNoOp() {
        HttpModule module = new HttpModule(server.url());
        module.param("ignored", (Object[]) null);
        module.expectString().get();
        Assert.assertTrue("no query string expected: " + server.requests().get(0).target,
                server.requests().get(0).target.endsWith("/svc"));
    }

    @Test
    public void postJsonBodySetsJsonContentType() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "datapoly");
        ResponseEntity<?> entity = new HttpModule(server.url())
                .body(payload)
                .expectString()
                .post();
        Assert.assertEquals(200, entity.getStatusCodeValue());
        Captured captured = server.requests().get(0);
        Assert.assertEquals("POST", captured.method);
        Assert.assertTrue("content type must be JSON: " + captured.headers,
                captured.headers.get("content-type").startsWith("application/json"));
        Assert.assertTrue("serialized body must carry the field: " + captured.body,
                captured.body.contains("\"name\":\"datapoly\""));
    }

    @Test
    public void putAndDeleteUseTheirMethods() {
        new HttpModule(server.url()).expectString().put();
        new HttpModule(server.url()).expectString().delete();
        Assert.assertEquals("PUT", server.requests().get(0).method);
        Assert.assertEquals("DELETE", server.requests().get(1).method);
    }

    @Test
    public void formDataIsPostedUrlEncoded() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("field", "value");
        new HttpModule(server.url())
                .data(form)
                .expectString()
                .post();
        Captured captured = server.requests().get(0);
        Assert.assertEquals("field=value", captured.body);
        Assert.assertTrue(captured.headers.get("content-type").startsWith("application/x-www-form-urlencoded"));
    }

    @Test
    public void dataVarargsAppendMultipleValues() {
        new HttpModule(server.url())
                .data("tags", "a", "b")
                .expectString()
                .post();
        Captured captured = server.requests().get(0);
        Assert.assertEquals("tags=a&tags=b", captured.body);
    }

    @Test
    public void expectBytesReturnsRawPayload() {
        server.contentType = "application/octet-stream";
        server.body = new byte[]{1, 2, 3, (byte) 0xFF};
        ResponseEntity<?> entity = new HttpModule(server.url()).expectBytes().get();
        Assert.assertArrayEquals(server.body, (byte[]) entity.getBody());
    }

    @Test
    public void existingQuestionMarkGetsAmpersandAppended() {
        new HttpModule(server.url() + "?z=9")
                .param("a", 1)
                .expectString()
                .get();
        Assert.assertTrue(server.requests().get(0).target.endsWith("/svc?z=9&a=1"));
    }

    @Test
    public void repeatedExecuteDoesNotStackQueryString() {
        HttpModule module = new HttpModule(server.url()).param("a", 1);
        module.expectString().execute();
        module.expectString().execute();
        Assert.assertEquals("/svc?a=1", server.requests().get(0).target);
        Assert.assertEquals("/svc?a=1", server.requests().get(1).target);
    }

    @Test
    public void errorStatusPropagatesAsException() {
        server.status = "500 Internal Server Error";
        try {
            new HttpModule(server.url()).expectString().get();
            Assert.fail("5xx must surface as HttpServerErrorException");
        } catch (HttpServerErrorException expected) {
            Assert.assertEquals(500, expected.getStatusCode().value());
        }
    }

    @Test
    public void methodAndContentTypeStringBuilders() {
        new HttpModule(server.url())
                .method(HttpMethod.POST)
                .contentType("application/xml")
                .expectString()
                .execute();
        Captured captured = server.requests().get(0);
        Assert.assertEquals("POST", captured.method);
        Assert.assertTrue(captured.headers.get("content-type").startsWith("application/xml"));
    }
}
