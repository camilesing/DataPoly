// Use of this source code is governed by a BSD-style license
package com.cs.executor.flowcontrol;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.common.service.FlowControlLease;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Sentinel flow-control rule loading and the request gate: QPS/thread grade mapping, skipping
 * non-configured rows, full-list reload clearing removed rules, lease close semantics, and the
 * 429 response written when a rule blocks.
 */
public class SentinelFlowControlManagerTest {

    private SentinelFlowControlManager manager;
    private FakeApiOnlineDao apiOnlineDao;
    private StringWriter responseBody;
    private final Integer[] statusRef = new Integer[]{null};
    private final Map<String, String> responseHeaders = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        manager = new SentinelFlowControlManager();
        apiOnlineDao = new FakeApiOnlineDao();
        responseBody = new StringWriter();
        responseHeaders.clear();
        statusRef[0] = null;
        inject("apiOnlineDao", apiOnlineDao);
        FlowRuleManager.loadRules(new ArrayList<>());
    }

    @After
    public void tearDown() {
        // Sentinel state is JVM-global; do not leak rules into other tests
        FlowRuleManager.loadRules(new ArrayList<>());
    }

    @Test
    public void loadFlowRulesMapsQpsAndThreadGrades() {
        apiOnlineDao.rows.add(assignment(HttpMethodEnum.GET, "orders", RuleConstant.FLOW_GRADE_QPS, 50));
        apiOnlineDao.rows.add(assignment(HttpMethodEnum.POST, "orders", RuleConstant.FLOW_GRADE_THREAD, 8));
        // flowCount <= 0 means "not configured" — must not produce a rule
        apiOnlineDao.rows.add(assignment(HttpMethodEnum.DELETE, "orders", RuleConstant.FLOW_GRADE_QPS, 0));

        manager.loadFlowRules();

        List<FlowRule> rules = new ArrayList<>(FlowRuleManager.getRules());
        assertEquals(2, rules.size());
        FlowRule qps = ruleForResource(rules, "/api/orders[GET]");
        FlowRule thread = ruleForResource(rules, "/api/orders[POST]");
        assertNotNull(qps);
        assertNotNull(thread);
        assertEquals(RuleConstant.FLOW_GRADE_QPS, qps.getGrade());
        assertEquals(50.0, qps.getCount(), 0.0);
        assertEquals(RuleConstant.FLOW_GRADE_THREAD, thread.getGrade());
        assertEquals(8.0, thread.getCount(), 0.0);
    }

    @Test
    public void loadFlowRulesSurvivesRowsWithoutAGradeOrACount() {
        // Regression: a null flowGrade / flowCount unboxed to an NPE that aborted the whole reload, so
        // one such row froze every interface's rules at their previous state
        apiOnlineDao.rows.add(row(HttpMethodEnum.GET, "first", RuleConstant.FLOW_GRADE_QPS, 5));
        apiOnlineDao.rows.add(row(HttpMethodEnum.PUT, "no-grade", null, 7));
        apiOnlineDao.rows.add(row(HttpMethodEnum.POST, "no-count", RuleConstant.FLOW_GRADE_QPS, null));
        apiOnlineDao.rows.add(row(HttpMethodEnum.DELETE, "last", RuleConstant.FLOW_GRADE_THREAD, 3));

        manager.loadFlowRules();

        List<FlowRule> rules = new ArrayList<>(FlowRuleManager.getRules());
        // the rows after the incomplete ones are still loaded
        assertNotNull(ruleForResource(rules, "/api/first[GET]"));
        assertNotNull(ruleForResource(rules, "/api/last[DELETE]"));
        // a missing grade means the default (QPS); a missing threshold means "not configured"
        FlowRule defaulted = ruleForResource(rules, "/api/no-grade[PUT]");
        assertNotNull(defaulted);
        assertEquals(RuleConstant.FLOW_GRADE_QPS, defaulted.getGrade());
        assertNull(ruleForResource(rules, "/api/no-count[POST]"));
    }

    private ApiAssignmentEntity row(HttpMethodEnum method, String path, Integer grade, Integer count) {
        ApiAssignmentEntity entity = new ApiAssignmentEntity();
        entity.setMethod(method);
        entity.setPath(path);
        entity.setFlowGrade(grade);
        entity.setFlowCount(count);
        return entity;
    }

    @Test
    public void loadFlowRulesReloadClearsDeletedRules() {
        apiOnlineDao.rows.add(assignment(HttpMethodEnum.GET, "orders", RuleConstant.FLOW_GRADE_QPS, 50));
        manager.loadFlowRules();
        assertEquals(1, FlowRuleManager.getRules().size());

        // the API went offline / flow control disabled — an empty reload must drop the rule
        apiOnlineDao.rows.clear();
        manager.loadFlowRules();
        assertEquals(0, FlowRuleManager.getRules().size());
    }

    @Test
    public void loadFlowRulesSwallowsDaoFailure() {
        apiOnlineDao.failure = new IllegalStateException("db down");
        manager.loadFlowRules();
        assertEquals(1, apiOnlineDao.listCalls.get());
    }

    @Test
    public void loadFlowRulesLogsDebugCountWhenEnabled() {
        // the debug log statement only runs when the logger level allows it
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SentinelFlowControlManager.class);
        ch.qos.logback.classic.Level original = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            apiOnlineDao.rows.add(assignment(HttpMethodEnum.GET, "logged", RuleConstant.FLOW_GRADE_QPS, 5));
            manager.loadFlowRules();
            assertEquals(1, FlowRuleManager.getRules().size());
        } finally {
            logger.setLevel(original);
        }
    }

    @Test
    public void checkFlowControlPassesUnderLimitAndLeaseCloseReleasesSlot() throws Exception {
        apiOnlineDao.rows.add(assignment(HttpMethodEnum.GET, "orders", RuleConstant.FLOW_GRADE_QPS, 100));
        manager.loadFlowRules();

        FlowControlLease lease = manager.checkFlowControl("/api/orders[GET]", response());
        assertNotNull("limit 100 must admit the request", lease);
        lease.close();
        // after closing, another entry must still be admitted
        FlowControlLease second = manager.checkFlowControl("/api/orders[GET]", response());
        assertNotNull(second);
        second.close();
        assertEquals(0, statusRef[0] == null ? 0 : statusRef[0]);
        assertTrue(responseBody.toString().isEmpty());
    }

    @Test
    public void checkFlowControlBlockedWrites429AndReturnsNull() throws Exception {
        // QPS 1 on a resource unique to this test (sentinel statistics are JVM-global):
        // the first request passes, the immediate second one within the same window blocks
        apiOnlineDao.rows.add(assignment(HttpMethodEnum.GET, "blocked", RuleConstant.FLOW_GRADE_QPS, 1));
        manager.loadFlowRules();

        FlowControlLease first = manager.checkFlowControl("/api/blocked[GET]", response());
        assertNotNull("QPS 1 must admit the first request", first);
        first.close();

        FlowControlLease lease = manager.checkFlowControl("/api/blocked[GET]", response());
        assertNull("the second request inside the same window must be blocked", lease);
        assertEquals(Integer.valueOf(429), statusRef[0]);
        assertEquals("application/json", responseHeaders.get("Content-Type"));
        assertEquals("UTF-8", responseHeaders.get("Character-Encoding"));
        assertTrue("body must be a 429 ResultEntity carrying the resource name, was: " + responseBody,
                responseBody.toString().contains("\"code\":429")
                        && responseBody.toString().contains("/api/blocked[GET]"));
    }

    @Test
    public void checkFlowControlWithoutRulesAlwaysPasses() throws Exception {
        manager.loadFlowRules();
        FlowControlLease lease = manager.checkFlowControl("/api/unconfigured[GET]", response());
        assertNotNull(lease);
        lease.close();
    }

    private FlowRule ruleForResource(List<FlowRule> rules, String resource) {
        for (FlowRule rule : rules) {
            if (resource.equals(rule.getResource())) {
                return rule;
            }
        }
        return null;
    }

    private void inject(String name, Object value) throws Exception {
        Field field = SentinelFlowControlManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getWriter":
                            return new PrintWriter(responseBody, true);
                        case "setStatus":
                            statusRef[0] = (Integer) a[0];
                            return null;
                        case "setContentType":
                            responseHeaders.put("Content-Type", String.valueOf(a[0]));
                            return null;
                        case "setCharacterEncoding":
                            responseHeaders.put("Character-Encoding", String.valueOf(a[0]));
                            return null;
                        default:
                            return null;
                    }
                });
    }

    private ApiAssignmentEntity assignment(HttpMethodEnum method, String path, int grade, int count) {
        ApiAssignmentEntity entity = new ApiAssignmentEntity();
        entity.setMethod(method);
        entity.setPath(path);
        entity.setFlowGrade(grade);
        entity.setFlowCount(count);
        return entity;
    }

    private static class FakeApiOnlineDao extends ApiOnlineDao {
        final List<ApiAssignmentEntity> rows = new ArrayList<>();
        final AtomicInteger listCalls = new AtomicInteger();
        RuntimeException failure;

        @Override
        public List<ApiAssignmentEntity> listFlowControlAll() {
            listCalls.incrementAndGet();
            if (null != failure) {
                throw failure;
            }
            return rows;
        }
    }
}
