// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.common.enums.NodeStatusEnum;
import com.cs.core.dto.TopologyNodeResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Cluster topology assembly for {@link NodeService}. The actuator metrics are
 * served by a local {@link HttpServer} so the parsing and fallback branches of
 * the HTTP probes are exercised for real; the discovery client and the url
 * resolver are stubs.
 */
public class NodeServiceTest {

    private StubDiscoveryClient discoveryClient;
    private HttpServer actuator;
    private String mode = "up";
    private NodeService service;

    @Before
    public void setUp() throws Exception {
        discoveryClient = new StubDiscoveryClient();
        service = new NodeService();
        inject("discoveryClient", discoveryClient);
        inject("serviceUrlResolver", new FixedGatewayResolver());

        actuator = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        actuator.createContext("/", new ActuatorHandler());
        actuator.start();
    }

    @After
    public void tearDown() {
        if (actuator != null) {
            actuator.stop(0);
        }
    }

    private class ActuatorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String path = exchange.getRequestURI().getPath();
                byte[] body;
                if (path.endsWith("/actuator/health")) {
                    body = healthBody();
                } else {
                    body = metricBody(path);
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (body == null) {
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private byte[] healthBody() {
            switch (mode) {
                case "down":
                    return json("{\"status\":\"DOWN\"}");
                case "nocontent":
                    return null;
                default:
                    return json("{\"status\":\"UP\"}");
            }
        }

        private byte[] metricBody(String path) {
            String value = null;
            switch (mode) {
                case "garbage":
                    value = "\"not-a-number\"";
                    break;
                case "empty":
                    return json("{\"measurements\":[]}");
                case "nocontent":
                    return null;
                case "zeromax":
                    if (path.endsWith("jvm.memory.used")) {
                        value = "40";
                    } else if (path.endsWith("jvm.memory.max")) {
                        value = "0";
                    } else if (path.endsWith("system.cpu.usage")) {
                        value = "0.7";
                    } else {
                        value = "0";
                    }
                    break;
                default:
                    if (path.endsWith("jvm.memory.used") || path.endsWith("disk.free")) {
                        value = "25";
                    } else if (path.endsWith("jvm.memory.max") || path.endsWith("disk.total")) {
                        value = "100";
                    } else if (path.endsWith("system.cpu.usage")) {
                        value = "0.5";
                    }
                    break;
            }
            if (value == null) {
                // a payload without a "measurements" key keeps the null-list branch honest
                return json("{}");
            }
            return json("{\"measurements\":[{\"statistic\":\"VALUE\",\"value\":" + value + "}]}");
        }
    }

    private static byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // resolver delegation
    // ------------------------------------------------------------------

    @Test
    public void gatewayAddressAndApiPrefixDelegateToResolver() {
        assertEquals("http://gw.example.com:8091", service.getGatewayAddr());
        assertEquals("http://gw.example.com:8091/api/", service.getApiPrefix());
    }

    // ------------------------------------------------------------------
    // topology
    // ------------------------------------------------------------------

    @Test
    public void emptyServiceListYieldsNoNodes() {
        discoveryClient.services.clear();

        assertTrue(service.getNodesTopology().isEmpty());
    }

    @Test
    public void serviceWithoutInstancesIsSkipped() {
        discoveryClient.services.add("datapoly-manager");
        discoveryClient.instancesByService.put("datapoly-manager", Collections.emptyList());

        assertTrue(service.getNodesTopology().isEmpty());
    }

    @Test
    public void healthyActuatorMetricsAreMappedOntoTheNode() {
        registerLocalInstance("datapoly-executor", "exec-1");

        List<TopologyNodeResponse> nodes = service.getNodesTopology();

        assertEquals(1, nodes.size());
        TopologyNodeResponse node = nodes.get(0);
        assertEquals("datapoly-executor", node.getServiceId());
        assertEquals("exec-1", node.getInstanceId());
        assertEquals("127.0.0.1", node.getHost());
        assertEquals(Integer.valueOf(actuator.getAddress().getPort()), node.getPort());
        // used 25 / max 100, cpu 0.5, (total 100 - free 25) / total 100
        assertEquals(Integer.valueOf(25), node.getMemory());
        assertEquals(Integer.valueOf(50), node.getCpu());
        assertEquals(Integer.valueOf(75), node.getDisk());
        assertEquals(NodeStatusEnum.normal, node.getStatus());
    }

    @Test
    public void downHealthMarksTheNodeWarningButKeepsMetrics() {
        mode = "down";
        registerLocalInstance("datapoly-gateway", "gw-1");

        TopologyNodeResponse node = service.getNodesTopology().get(0);

        assertEquals(NodeStatusEnum.warning, node.getStatus());
        assertEquals(Integer.valueOf(25), node.getMemory());
        assertEquals(Integer.valueOf(50), node.getCpu());
        assertEquals(Integer.valueOf(75), node.getDisk());
    }

    @Test
    public void nonNumericMeasurementValuesFallBackToZero() {
        mode = "garbage";
        registerLocalInstance("datapoly-executor", "exec-1");

        TopologyNodeResponse node = service.getNodesTopology().get(0);

        assertEquals(NodeStatusEnum.normal, node.getStatus());
        assertEquals(Integer.valueOf(0), node.getMemory());
        assertEquals(Integer.valueOf(0), node.getCpu());
        assertEquals(Integer.valueOf(0), node.getDisk());
    }

    @Test
    public void emptyMeasurementListYieldsNullMetricAndZeroUsage() {
        mode = "empty";
        registerLocalInstance("datapoly-executor", "exec-1");

        TopologyNodeResponse node = service.getNodesTopology().get(0);

        assertEquals(NodeStatusEnum.normal, node.getStatus());
        assertEquals(Integer.valueOf(0), node.getMemory());
        assertEquals(Integer.valueOf(0), node.getCpu());
        assertEquals(Integer.valueOf(0), node.getDisk());
    }

    @Test
    public void missingBodiesKeepTheNodeUsableWithZeros() {
        mode = "nocontent";
        registerLocalInstance("datapoly-executor", "exec-1");

        TopologyNodeResponse node = service.getNodesTopology().get(0);

        // a 204 health answer is not "UP": the node is flagged warning
        assertEquals(NodeStatusEnum.warning, node.getStatus());
        assertEquals(Integer.valueOf(0), node.getMemory());
        assertEquals(Integer.valueOf(0), node.getCpu());
        assertEquals(Integer.valueOf(0), node.getDisk());
    }

    @Test
    public void zeroDenominatorIsReportedAsZeroUsage() {
        mode = "zeromax";
        registerLocalInstance("datapoly-executor", "exec-1");

        TopologyNodeResponse node = service.getNodesTopology().get(0);

        assertEquals(NodeStatusEnum.normal, node.getStatus());
        assertEquals(Integer.valueOf(0), node.getMemory());
        assertEquals(Integer.valueOf(0), node.getDisk());
        // cpu usage comes from a plain ratio, not a denominator guard
        assertEquals(Integer.valueOf(70), node.getCpu());
    }

    @Test
    public void unreachableInstanceIsReportedAsWarningWithZeroMetrics() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        discoveryClient.services.add("datapoly-manager");
        discoveryClient.instancesByService.put("datapoly-manager",
                Collections.singletonList(instance("datapoly-manager", "mgr-1", "127.0.0.1", closedPort)));

        TopologyNodeResponse node = service.getNodesTopology().get(0);

        assertEquals(NodeStatusEnum.warning, node.getStatus());
        assertEquals(Integer.valueOf(0), node.getMemory());
        assertEquals(Integer.valueOf(0), node.getCpu());
        assertEquals(Integer.valueOf(0), node.getDisk());
        assertEquals("mgr-1", node.getInstanceId());
    }

    @Test
    public void everyDiscoveredInstanceBecomesANode() {
        discoveryClient.services.addAll(Arrays.asList("datapoly-gateway", "datapoly-manager"));
        registerLocalInstance("datapoly-gateway", "gw-1");
        registerLocalInstance("datapoly-gateway", "gw-2");
        registerLocalInstance("datapoly-manager", "mgr-1");

        List<TopologyNodeResponse> nodes = service.getNodesTopology();

        assertEquals(3, nodes.size());
        Set<String> instanceIds = new HashSet<>();
        for (TopologyNodeResponse node : nodes) {
            instanceIds.add(node.getServiceId() + "/" + node.getInstanceId());
            assertEquals(NodeStatusEnum.normal, node.getStatus());
        }
        assertEquals(new HashSet<>(Arrays.asList(
                "datapoly-gateway/gw-1", "datapoly-gateway/gw-2", "datapoly-manager/mgr-1")), instanceIds);
    }

    // ------------------------------------------------------------------
    // fakes & helpers
    // ------------------------------------------------------------------

    private void registerLocalInstance(String serviceId, String instanceId) {
        if (!discoveryClient.services.contains(serviceId)) {
            discoveryClient.services.add(serviceId);
        }
        discoveryClient.instancesByService
                .computeIfAbsent(serviceId, key -> new ArrayList<>())
                .add(instance(serviceId, instanceId, "127.0.0.1", actuator.getAddress().getPort()));
    }

    private void inject(String name, Object value) throws Exception {
        Field field = NodeService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static ServiceInstance instance(String serviceId, String instanceId, String host, int port) {
        return new ServiceInstance() {
            @Override
            public String getServiceId() {
                return serviceId;
            }

            @Override
            public String getInstanceId() {
                return instanceId;
            }

            @Override
            public String getHost() {
                return host;
            }

            @Override
            public int getPort() {
                return port;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public URI getUri() {
                return URI.create("http://" + host + ":" + port);
            }

            @Override
            public Map<String, String> getMetadata() {
                return Collections.emptyMap();
            }
        };
    }

    private static final class StubDiscoveryClient implements DiscoveryClient {
        private final List<String> services = new ArrayList<>();
        private final Map<String, List<ServiceInstance>> instancesByService = new HashMap<>();

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return instancesByService.getOrDefault(serviceId, Collections.emptyList());
        }

        @Override
        public List<String> getServices() {
            return services;
        }
    }

    private static final class FixedGatewayResolver extends ServiceUrlResolver {
        @Override
        public String resolveGateway() {
            return "http://gw.example.com:8091";
        }

        @Override
        public String getGatewayApiPrefix() {
            return "http://gw.example.com:8091/api/";
        }
    }
}
