// Use of this source code is governed by a BSD-style license
package com.cs.core.executor;

import com.cs.common.enums.OnOffEnum;
import com.cs.common.exception.CommonException;
import com.cs.core.dto.NameValueBaseResponse;
import com.cs.core.dto.TestAlarmConfigRequest;
import com.cs.core.dto.UpdateAlarmConfigRequest;
import com.cs.persistence.dao.UnifyAlarmDao;
import com.cs.persistence.entity.UnifyAlarmEntity;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Drives the unified alarm webhook service with a fake dao (subclass) and a recording
 * RestTemplate swapped in through reflection: template substitution with JSON escaping,
 * the SSRF endpoint guard, the send-failure swallow and the disabled shortcut.
 */
public class UnifyAlarmOpsServiceTest {

    private static class FakeUnifyAlarmDao extends UnifyAlarmDao {
        UnifyAlarmEntity config;
        int updateCount;
        OnOffEnum updatedStatus;
        String updatedEndpoint;
        String updatedContentType;
        String updatedTemplate;

        @Override
        public UnifyAlarmEntity getUnifyAlarmConfig() {
            return config;
        }

        @Override
        public void update(OnOffEnum status, String endpoint, String contentType, String inputTemplate) {
            this.updateCount++;
            this.updatedStatus = status;
            this.updatedEndpoint = endpoint;
            this.updatedContentType = contentType;
            this.updatedTemplate = inputTemplate;
        }
    }

    private static class RecordingRestTemplate extends RestTemplate {
        String url;
        HttpMethod method;
        HttpEntity<?> entity;
        ResponseEntity<String> answer = ResponseEntity.ok("delivered");

        @Override
        @SuppressWarnings("unchecked")
        public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
                                              Class<T> responseType, Object... uriVariables) {
            this.url = url;
            this.method = method;
            this.entity = requestEntity;
            return (ResponseEntity<T>) answer;
        }
    }

    private FakeUnifyAlarmDao dao;
    private RecordingRestTemplate restTemplate;
    private UnifyAlarmOpsService service;

    @Before
    public void setUp() throws Exception {
        dao = new FakeUnifyAlarmDao();
        restTemplate = new RecordingRestTemplate();
        service = new UnifyAlarmOpsService();
        inject("unifyAlarmDao", dao);
        inject("restTemplate", restTemplate);
    }

    private void inject(String name, Object value) throws Exception {
        Field field = UnifyAlarmOpsService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static UnifyAlarmEntity config(OnOffEnum status, String endpoint, String template) {
        return UnifyAlarmEntity.builder()
                .status(status)
                .endpoint(endpoint)
                .contentType("application/json")
                .inputTemplate(template)
                .build();
    }

    // ------------------------------------------------------------------
    // read paths
    // ------------------------------------------------------------------

    @Test
    public void getUnifyAlarmConfigReturnsDaoRow() {
        dao.config = config(OnOffEnum.ON, "https://hooks.example.com/a", "${name}");
        assertSame(dao.config, service.getUnifyAlarmConfig());
    }

    @Test
    public void getExampleDataModelMirrorsTheExampleModel() {
        List<NameValueBaseResponse> model = service.getExampleDataModel();

        assertFalse(model.isEmpty());
        Map<String, String> asMap = new java.util.HashMap<>();
        model.forEach(item -> asMap.put(item.getKey(), item.getValue()));
        assertEquals("/api/test/create", asMap.get("path"));
        assertEquals("POST", asMap.get("method"));
        assertEquals("this is test alarm message", asMap.get("exception"));
    }

    // ------------------------------------------------------------------
    // config update
    // ------------------------------------------------------------------

    @Test
    public void updateUnifyAlarmConfigPersistsValidatedRow() {
        UpdateAlarmConfigRequest request = new UpdateAlarmConfigRequest();
        request.setStatus(OnOffEnum.ON);
        request.setEndpoint("https://hooks.example.com/alarm");
        request.setContentType("application/json");
        request.setInputTemplate("{\"text\": \"${name}\"}");

        service.updateUnifyAlarmConfig(request);

        assertEquals(1, dao.updateCount);
        assertEquals(OnOffEnum.ON, dao.updatedStatus);
        assertEquals("https://hooks.example.com/alarm", dao.updatedEndpoint);
        assertEquals("application/json", dao.updatedContentType);
        assertEquals("{\"text\": \"${name}\"}", dao.updatedTemplate);
    }

    @Test
    public void updateUnifyAlarmConfigRejectsPrivateEndpointWithoutPersisting() {
        UpdateAlarmConfigRequest request = new UpdateAlarmConfigRequest();
        request.setStatus(OnOffEnum.ON);
        request.setEndpoint("http://127.0.0.1:8080/hook");
        request.setContentType("application/json");
        request.setInputTemplate("t");

        try {
            service.updateUnifyAlarmConfig(request);
            fail("loopback endpoint must be rejected");
        } catch (CommonException expected) {
            // ERROR_INVALID_ARGUMENT per the SSRF guard
        }
        assertEquals(0, dao.updateCount);
    }

    // ------------------------------------------------------------------
    // test send
    // ------------------------------------------------------------------

    @Test
    public void testUnifyAlarmConfigSendsEscapedTemplatedBodyAsPost() {
        TestAlarmConfigRequest request = new TestAlarmConfigRequest();
        request.setEndpoint("http://alarm.example.test/hook");
        request.setContentType("application/json");
        request.setInputTemplate("{\"text\": \"${name} said\"}");
        NameValueBaseResponse pair = new NameValueBaseResponse();
        pair.setKey("name");
        pair.setValue("line1\nline2");
        request.setDataModel(new java.util.ArrayList<>(Collections.singletonList(pair)));

        service.testUnifyAlarmConfig(request);

        assertEquals("http://alarm.example.test/hook", restTemplate.url);
        assertEquals(HttpMethod.POST, restTemplate.method);
        assertNotNull(restTemplate.entity);
        // control characters in the model value are escaped before substitution
        assertEquals("{\"text\": \"line1\\nline2 said\"}", restTemplate.entity.getBody());
        assertEquals("application/json;charset=UTF-8",
                restTemplate.entity.getHeaders().getContentType().toString());
        assertEquals("*/*", restTemplate.entity.getHeaders().getFirst("Accept"));
    }

    @Test
    public void testUnifyAlarmConfigToleratesNon2xxAnswer() {
        restTemplate.answer = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("err");
        TestAlarmConfigRequest request = new TestAlarmConfigRequest();
        request.setEndpoint("http://alarm.example.test/hook");
        request.setContentType("application/json");
        request.setInputTemplate("${name}");
        NameValueBaseResponse pair = new NameValueBaseResponse();
        pair.setKey("name");
        pair.setValue("n");
        request.setDataModel(new java.util.ArrayList<>(Collections.singletonList(pair)));

        // only logged, never thrown
        service.testUnifyAlarmConfig(request);
        assertNotNull(restTemplate.url);
    }

    @Test
    public void duplicateDataModelKeysKeepTheLastValue() {
        TestAlarmConfigRequest request = new TestAlarmConfigRequest();
        request.setEndpoint("http://alarm.example.test/hook");
        request.setContentType("application/json");
        request.setInputTemplate("${k}");
        List<NameValueBaseResponse> model = new java.util.ArrayList<>();
        NameValueBaseResponse first = new NameValueBaseResponse();
        first.setKey("k");
        first.setValue("first");
        NameValueBaseResponse second = new NameValueBaseResponse();
        second.setKey("k");
        second.setValue("second");
        model.add(first);
        model.add(second);
        request.setDataModel(model);

        service.testUnifyAlarmConfig(request);

        assertEquals("second", restTemplate.entity.getBody());
    }

    @Test
    public void debugLoggingRendersTheAlarmBody() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UnifyAlarmOpsService.class);
        ch.qos.logback.classic.Level original = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            dao.config = config(OnOffEnum.ON, "https://hooks.example.com/a", "body-${name}");
            service.triggerAlarm(Collections.singletonMap("name", "x"));
            assertNotNull(restTemplate.url);
        } finally {
            logger.setLevel(original);
        }
    }

    @Test
    public void testUnifyAlarmConfigTreatsNullDataModelAsEmpty() {
        TestAlarmConfigRequest request = new TestAlarmConfigRequest();
        request.setEndpoint("http://alarm.example.test/hook");
        request.setContentType("text/plain");
        request.setInputTemplate("static body");
        request.setDataModel(null);

        service.testUnifyAlarmConfig(request);

        assertEquals("static body", restTemplate.entity.getBody());
        assertEquals("text/plain;charset=UTF-8",
                restTemplate.entity.getHeaders().getContentType().toString());
    }

    @Test
    public void testUnifyAlarmConfigRejectsNonHttpSchemeBeforeSending() {
        TestAlarmConfigRequest request = new TestAlarmConfigRequest();
        request.setEndpoint("ftp://alarm.example.test/hook");
        request.setContentType("application/json");
        request.setInputTemplate("t");
        request.setDataModel(new java.util.ArrayList<>());

        try {
            service.testUnifyAlarmConfig(request);
            fail("non-http scheme must be rejected");
        } catch (CommonException expected) {
        }
        assertNull("no request must hit the wire", restTemplate.url);
    }

    @Test
    public void testUnifyAlarmConfigRejectsNullEndpointBeforeSending() {
        TestAlarmConfigRequest request = new TestAlarmConfigRequest();
        request.setEndpoint(null);
        request.setContentType("application/json");
        request.setInputTemplate("t");
        request.setDataModel(new java.util.ArrayList<>());

        try {
            service.testUnifyAlarmConfig(request);
            fail("null endpoint must be rejected");
        } catch (CommonException expected) {
        }
        assertNull(restTemplate.url);
    }

    // ------------------------------------------------------------------
    // trigger path
    // ------------------------------------------------------------------

    @Test
    public void triggerAlarmSkipsSendWhenDisabled() {
        dao.config = config(OnOffEnum.OFF, "https://hooks.example.com/a", "${name}");

        service.triggerAlarm(Collections.singletonMap("name", "x"));

        assertNull(restTemplate.url);
    }

    @Test
    public void triggerAlarmSendsWhenEnabled() {
        dao.config = config(OnOffEnum.ON, "https://hooks.example.com/a", "alert ${name}");

        service.triggerAlarm(Collections.singletonMap("name", "boom"));

        assertEquals("https://hooks.example.com/a", restTemplate.url);
        assertEquals("alert boom", restTemplate.entity.getBody());
    }

    @Test
    public void triggerAlarmSwallowsSendFailure() {
        dao.config = config(OnOffEnum.ON, "https://hooks.example.com/a", "${name}");
        RestTemplate failing = new RestTemplate() {
            @Override
            public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
                                                  Class<T> responseType, Object... uriVariables) {
                throw new org.springframework.web.client.ResourceAccessException("connection refused");
            }
        };
        try {
            Field field = UnifyAlarmOpsService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(service, failing);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        // failure is caught and logged: the alert path must never break the API call
        service.triggerAlarm(Collections.singletonMap("name", "x"));
    }

    @Test
    public void triggerAlarmSwallowsGuardRejection() {
        dao.config = config(OnOffEnum.ON, "http://192.168.1.5/hook", "${name}");

        service.triggerAlarm(Collections.singletonMap("name", "x"));

        // guard rejects the private endpoint before any wire traffic; nothing propagates
    }

    @Test
    public void triggerAlarmSupportsSemicolonStrippedContentType() {
        dao.config = UnifyAlarmEntity.builder()
                .status(OnOffEnum.ON)
                .endpoint("https://hooks.example.com/a")
                .contentType("application/json;")
                .inputTemplate("${name}")
                .build();

        service.triggerAlarm(Collections.singletonMap("name", "v"));

        assertEquals("application/json;charset=UTF-8",
                restTemplate.entity.getHeaders().getContentType().toString());
        assertEquals("v", restTemplate.entity.getBody());
    }

    @Test
    public void templateWithoutPlaceholdersIsSentVerbatim() {
        dao.config = config(OnOffEnum.ON, "https://hooks.example.com/a", "plain");

        service.triggerAlarm(Collections.emptyMap());

        assertEquals("plain", restTemplate.entity.getBody());
    }

    @Test
    public void dataModelValuesAreControlCharacterEscapedBeforeSubstitution() {
        dao.config = config(OnOffEnum.ON, "https://hooks.example.com/a", "${a}/${b}");

        service.triggerAlarm(new java.util.LinkedHashMap<String, String>() {{
            put("a", "x\ty");
            put("b", "plain");
        }});

        // tab escaped, plain value untouched
        assertEquals("x\\ty/plain", restTemplate.entity.getBody());
    }
}
