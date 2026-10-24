// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.common.consts.Constants;
import com.cs.common.enums.ParamTypeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.manager.config.DataPolyUrlConfiguration;
import com.cs.manager.model.ServiceUrlItemResponse;
import com.cs.persistence.dao.SystemParamDao;
import com.cs.persistence.entity.SystemParamEntity;
import org.junit.*;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;

/**
 * Priority matrix of {@link ServiceUrlResolver}: system param beats env beats
 * Nacos discovery, invalid stored values are treated as unset, and the config
 * view never throws while a service is unregistered.
 */
public class ServiceUrlResolverTest {

    private StubParamDao paramDao;
    private DataPolyUrlConfiguration urlConfiguration;
    private StubDiscoveryClient discoveryClient;
    private ServiceUrlResolver resolver;

    @Before
    public void setUp() throws Exception {
        paramDao = new StubParamDao();
        urlConfiguration = new DataPolyUrlConfiguration();
        discoveryClient = new StubDiscoveryClient();
        resolver = new ServiceUrlResolver();
        inject("systemParamDao", paramDao);
        inject("datapolyUrlConfiguration", urlConfiguration);
        inject("discoveryClient", discoveryClient);
    }

    @Test
    public void paramBeatsEnvAndDiscoveryAndSkipsTheRegistryLookup() {
        param(Constants.SYS_PARAM_KEY_GATEWAY_URL, "http://gate.example.com:8091/");
        urlConfiguration.setGateway("http://env-gate:8091");
        discoveryClient.instances = Collections.singletonList(new StubInstance("172.28.0.40", 8091));

        Assert.assertEquals("http://gate.example.com:8091", resolver.resolveGateway());
        Assert.assertEquals(0, discoveryClient.calls);

        ServiceUrlItemResponse config = resolver.gatewayConfig();
        Assert.assertEquals("PARAM", config.getSource());
        Assert.assertEquals("http://gate.example.com:8091", config.getEffective());
        Assert.assertEquals("http://gate.example.com:8091/", config.getValue());
    }

    @Test
    public void managerResolvesThroughItsOwnParam() {
        param(Constants.SYS_PARAM_KEY_MANAGER_URL, "https://mgr.example.com");
        urlConfiguration.setManager("http://env-mgr:8090");

        Assert.assertEquals("https://mgr.example.com", resolver.resolveManager());
        Assert.assertEquals("PARAM", resolver.managerConfig().getSource());
    }

    @Test
    public void envIsUsedOnlyWhileTheServiceIsRegistered() {
        urlConfiguration.setGateway("http://env-gate:8091/");

        discoveryClient.instances = Collections.singletonList(new StubInstance("172.28.0.40", 8091));
        Assert.assertEquals("http://env-gate:8091", resolver.resolveGateway());
        Assert.assertEquals("ENV", resolver.gatewayConfig().getSource());

        discoveryClient.instances = Collections.emptyList();
        assertResourceMissing();
    }

    @Test
    public void discoveredInstanceIsTheLastFallback() {
        discoveryClient.instances = Collections.singletonList(new StubInstance("172.28.0.40", 8091));

        Assert.assertEquals("http://172.28.0.40:8091", resolver.resolveGateway());
        Assert.assertEquals("DISCOVERY", resolver.gatewayConfig().getSource());
    }

    @Test
    public void nothingResolvedMeansNotFound() {
        assertResourceMissing();
        try {
            resolver.resolveManager();
            Assert.fail("expected manager.not.found");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void invalidStoredParamIsTreatedAsUnset() {
        discoveryClient.instances = Collections.singletonList(new StubInstance("172.28.0.40", 8091));
        for (String invalid : new String[]{"ftp://gate:8091", "http://", "http://a b.com", "http://a\nb.com"}) {
            paramDao.rows.put(Constants.SYS_PARAM_KEY_GATEWAY_URL,
                    entity(Constants.SYS_PARAM_KEY_GATEWAY_URL, invalid));
            Assert.assertEquals("invalid value [" + invalid + "] should fall back to discovery",
                    "http://172.28.0.40:8091", resolver.resolveGateway());
        }
    }

    @Test
    public void apiPrefixAppendsTheApiPath() {
        param(Constants.SYS_PARAM_KEY_GATEWAY_URL, "http://gate.example.com:8091");

        Assert.assertEquals("http://gate.example.com:8091/api/", resolver.getGatewayApiPrefix());
    }

    @Test
    public void configViewStaysUsableWhenNothingResolves() {
        ServiceUrlItemResponse config = resolver.gatewayConfig();

        Assert.assertEquals("", config.getValue());
        Assert.assertNull(config.getEffective());
        Assert.assertEquals("NONE", config.getSource());
    }

    @Test
    public void normalizeValidatedUrlAcceptsHttpAndHttpsAndClearsOnBlank() {
        Assert.assertEquals("", ServiceUrlResolver.normalizeValidatedUrl(null));
        Assert.assertEquals("", ServiceUrlResolver.normalizeValidatedUrl("   "));
        Assert.assertEquals("http://a.com", ServiceUrlResolver.normalizeValidatedUrl(" http://a.com/ "));
        Assert.assertEquals("https://a.com:8091", ServiceUrlResolver.normalizeValidatedUrl("https://a.com:8091"));
    }

    @Test
    public void normalizeValidatedUrlRejectsGarbage() {
        assertInvalidUrl("ftp://a.com");
        assertInvalidUrl("http://");
        assertInvalidUrl("https://");
        assertInvalidUrl("http://a b.com");
        assertInvalidUrl("http://a.com\nx");
        assertInvalidUrl("www.example.com");
    }

    private void assertResourceMissing() {
        try {
            resolver.resolveGateway();
            Assert.fail("expected gateway.not.found");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    private static void assertInvalidUrl(String url) {
        try {
            ServiceUrlResolver.normalizeValidatedUrl(url);
            Assert.fail("expected node.url.invalid for [" + url + "]");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    private void param(String key, String value) {
        paramDao.rows.put(key, entity(key, value));
    }

    private static SystemParamEntity entity(String key, String value) {
        return SystemParamEntity.builder()
                .paramKey(key)
                .paramType(ParamTypeEnum.STRING)
                .paramValue(value)
                .build();
    }

    private void inject(String name, Object value) throws Exception {
        Field field = ServiceUrlResolver.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(resolver, value);
    }

    private static final class StubParamDao extends SystemParamDao {
        private final Map<String, SystemParamEntity> rows = new HashMap<>();

        @Override
        public SystemParamEntity getByParamKey(String paramKey) {
            return rows.get(paramKey);
        }
    }

    private static final class StubDiscoveryClient implements DiscoveryClient {
        private List<ServiceInstance> instances = Collections.emptyList();
        private int calls;

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            calls++;
            return instances;
        }

        @Override
        public List<String> getServices() {
            return Collections.singletonList("stub-service");
        }
    }

    private static final class StubInstance implements ServiceInstance {
        private final String host;
        private final int port;

        private StubInstance(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public String getServiceId() {
            return "stub-service";
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
    }
}