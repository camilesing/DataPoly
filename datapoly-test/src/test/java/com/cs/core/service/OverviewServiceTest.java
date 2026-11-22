// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.dto.DateCount;
import com.cs.common.dto.HourCount;
import com.cs.common.dto.NameCount;
import com.cs.common.dto.PageResult;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.ApiAccessLogBasicResponse;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.dao.AppClientDao;
import com.cs.persistence.entity.AccessRecordEntity;
import com.cs.persistence.entity.AppClientEntity;
import com.cs.persistence.mapper.AccessRecordMapper;
import com.cs.persistence.mapper.ApiAssignmentMapper;
import com.cs.persistence.mapper.DataSourceMapper;
import com.github.pagehelper.PageHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Overview statistics delegation, day/topN clamping and the access-log page mapping.
 * The "last N days" UI value is inclusive and clamped to [1,365] before being converted
 * to the exclusive SQL offset; topN is clamped to [1,100] with default 6.
 */
public class OverviewServiceTest {

    private static class Recorder {
        final Map<String, List<Object[]>> calls = new LinkedHashMap<>();
        private final Map<String, Queue<Object>> stubs = new HashMap<>();

        @SuppressWarnings("unchecked")
        <M> M create(Class<M> type) {
            return (M) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (p, m, a) -> {
                        calls.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(a);
                        Queue<Object> queue = stubs.get(m.getName());
                        if (queue != null && !queue.isEmpty()) {
                            Object result = queue.poll();
                            if (result instanceof RuntimeException) {
                                throw (RuntimeException) result;
                            }
                            return result;
                        }
                        Class<?> rt = m.getReturnType();
                        if (rt == boolean.class) {
                            return false;
                        }
                        if (rt == int.class) {
                            return 0;
                        }
                        if (rt == long.class) {
                            return 0L;
                        }
                        return null;
                    });
        }

        void stub(String method, Object... results) {
            Queue<Object> queue = stubs.computeIfAbsent(method, k -> new LinkedList<>());
            for (Object result : results) {
                queue.add(result);
            }
        }

        int count(String method) {
            return calls.getOrDefault(method, Collections.emptyList()).size();
        }

        Object[] argsOf(String method) {
            return calls.get(method).get(0);
        }

        Object[] lastArgsOf(String method) {
            List<Object[]> invocations = calls.get(method);
            return invocations.get(invocations.size() - 1);
        }
    }

    private Recorder accessRecorder;
    private Recorder dataSourceRecorder;
    private Recorder assignmentRecorder;
    private OverviewService service;

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(AccessRecordEntity.class);
    }

    @Before
    public void setUp() {
        accessRecorder = new Recorder();
        dataSourceRecorder = new Recorder();
        assignmentRecorder = new Recorder();
        service = new OverviewService();
        DataTaskTestSupport.setField(service, "accessRecordMapper",
                accessRecorder.create(AccessRecordMapper.class));
        DataTaskTestSupport.setField(service, "dataSourceMapper",
                dataSourceRecorder.create(DataSourceMapper.class));
        DataTaskTestSupport.setField(service, "apiAssignmentMapper",
                assignmentRecorder.create(ApiAssignmentMapper.class));
    }

    @After
    public void tearDown() throws Exception {
        PageHelper.clearPage();
        Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    public void countDelegatesToAccessRecords() {
        Map<String, Integer> expected = new HashMap<>();
        expected.put("totalCount", 3);
        accessRecorder.stub("selectCount", expected);
        assertSame(expected, service.count());
    }

    @Test
    public void dayWindowIsClampedAndConvertedToExclusiveOffset() {
        accessRecorder.stub("getDailyTrend", Collections.<DateCount>emptyList());

        service.trend(null);
        assertEquals(6, accessRecorder.argsOf("getDailyTrend")[0]);

        service.trend(1);
        assertEquals("1 inclusive day is a zero-day window", 0, accessRecorder.lastArgsOf("getDailyTrend")[0]);

        service.trend(0);
        assertEquals("values below 1 clamp to 1", 0, accessRecorder.lastArgsOf("getDailyTrend")[0]);

        service.trend(-5);
        assertEquals("negative days must never become a future interval", 0,
                accessRecorder.lastArgsOf("getDailyTrend")[0]);

        service.trend(7);
        assertEquals(6, accessRecorder.lastArgsOf("getDailyTrend")[0]);

        service.trend(365);
        assertEquals(364, accessRecorder.lastArgsOf("getDailyTrend")[0]);

        service.trend(400);
        assertEquals("values above 365 clamp to 365", 364, accessRecorder.lastArgsOf("getDailyTrend")[0]);
    }

    @Test
    public void topNIsClampedWithDefaultSix() {
        accessRecorder.stub("getTopPathAccess", Collections.<NameCount>emptyList());

        service.topPath(null, null);
        assertEquals(6, accessRecorder.argsOf("getTopPathAccess")[0]);
        assertEquals(6, accessRecorder.argsOf("getTopPathAccess")[1]);

        service.topPath(7, 0);
        assertEquals(1, accessRecorder.lastArgsOf("getTopPathAccess")[1]);

        service.topPath(7, 999);
        assertEquals(100, accessRecorder.lastArgsOf("getTopPathAccess")[1]);
    }

    @Test
    public void statusAndTopDelegationCarriesTheSameWindow() {
        List<NameCount> statuses = Collections.singletonList(NameCount.builder().name("HTTP(200)").count(5L).build());
        accessRecorder.stub("getHttpStatusCount", statuses);
        assertSame(statuses, service.httpStatus(7));
        assertEquals(6, accessRecorder.argsOf("getHttpStatusCount")[0]);

        List<NameCount> addresses = Collections.singletonList(NameCount.builder().name("10.0.0.1").count(2L).build());
        accessRecorder.stub("getTopIpAddrAccess", addresses);
        assertSame(addresses, service.topAddr(7, 10));
        assertEquals(6, accessRecorder.argsOf("getTopIpAddrAccess")[0]);
        assertEquals(10, accessRecorder.argsOf("getTopIpAddrAccess")[1]);

        List<NameCount> clients = Collections.singletonList(NameCount.builder().name("web").count(1L).build());
        accessRecorder.stub("getTopAppClientAccess", clients);
        assertSame(clients, service.topClient(null, null));
        assertEquals(6, accessRecorder.argsOf("getTopAppClientAccess")[0]);
        assertEquals(6, accessRecorder.argsOf("getTopAppClientAccess")[1]);
    }

    @Test
    public void assignmentAndDatasourceRatiosDelegate() {
        List<NameCount> dsRatio = Collections.singletonList(NameCount.builder().name("MYSQL").count(4L).build());
        dataSourceRecorder.stub("getDatasourceTypeRatio", dsRatio);
        assertSame(dsRatio, service.datasourceTypeRatio());

        List<NameCount> engineRatio = Collections.singletonList(NameCount.builder().name("SQL").count(9L).build());
        assignmentRecorder.stub("getEngineRatio", engineRatio);
        assertSame(engineRatio, service.engineRatio());

        List<NameCount> dsApiCount = Collections.singletonList(NameCount.builder().name("mysql-ds").count(8L).build());
        assignmentRecorder.stub("getDatasourceApiCount", dsApiCount);
        assertSame(dsApiCount, service.datasourceApiCount());

        List<NameCount> methodRatio = Collections.singletonList(NameCount.builder().name("POST").count(7L).build());
        assignmentRecorder.stub("getMethodRatio", methodRatio);
        assertSame(methodRatio, service.methodRatio());

        List<NameCount> moduleApiCount = Collections.singletonList(NameCount.builder().name("orders").count(3L).build());
        assignmentRecorder.stub("getModuleApiCount", moduleApiCount);
        assertSame(moduleApiCount, service.moduleApiCount());
        assertEquals("module widget caps at a fixed top 15", 15,
                assignmentRecorder.argsOf("getModuleApiCount")[0]);
    }

    @Test
    public void apiScopedStatisticsDelegateWithClampedWindow() {
        List<NameCount> statuses = Collections.<NameCount>emptyList();
        accessRecorder.stub("getApiHttpStatusCount", statuses);
        assertSame(statuses, service.apiStatusRatio(1L, -3));
        assertEquals(Long.valueOf(1L), accessRecorder.argsOf("getApiHttpStatusCount")[0]);
        assertEquals(0, accessRecorder.argsOf("getApiHttpStatusCount")[1]);

        List<DateCount> trend = Collections.<DateCount>emptyList();
        accessRecorder.stub("getApiDailyTrend", trend);
        assertSame(trend, service.apiDailyTrend(1L, 400));
        assertEquals(364, accessRecorder.argsOf("getApiDailyTrend")[1]);

        List<HourCount> hourly = Collections.<HourCount>emptyList();
        accessRecorder.stub("getApiHourlyTrend", hourly);
        assertSame(hourly, service.apiHourlyTrend(1L, "2026-11-16"));
        assertEquals(Long.valueOf(1L), accessRecorder.argsOf("getApiHourlyTrend")[0]);
        assertEquals("2026-11-16", accessRecorder.argsOf("getApiHourlyTrend")[1]);
    }

    @Test
    public void pageByApiIdJoinsClientNamesAndMapsRecords() throws Exception {
        AppClientEntity client = AppClientEntity.builder()
                .id(3L).name("web").appKey("ak-1").build();
        AppClientDao appClientDao = new AppClientDao() {
            @Override
            public List<AppClientEntity> listAll() {
                return Collections.singletonList(client);
            }
        };
        installBeanFactory(appClientDao);
        try {
            AccessRecordEntity record = AccessRecordEntity.builder()
                    .id(11L).apiId(1L).status(200).duration(25L)
                    .ipAddr("10.0.0.1").userAgent("curl/8").clientKey("ak-1")
                    .exception(null).createTime(null)
                    .executorAddr("executor:8092").gatewayAddr("gateway:8091")
                    .build();
            accessRecorder.stub("selectList", Collections.singletonList(record));

            PageResult<ApiAccessLogBasicResponse> page;
            try {
                page = service.pageByApiId(1L, 1, 10, 200, "2026-11-01 00:00:00", "2026-11-16 00:00:00");
            } finally {
                PageHelper.clearPage();
            }

            assertEquals(1, page.getData().size());
            ApiAccessLogBasicResponse row = page.getData().get(0);
            assertEquals(Long.valueOf(11L), row.getId());
            assertEquals(Integer.valueOf(200), row.getStatus());
            assertEquals(Long.valueOf(25L), row.getDuration());
            assertEquals("10.0.0.1", row.getIpAddr());
            assertEquals("[3]web(ak-1)", row.getClientApp());
            assertEquals("executor:8092", row.getExecutorAddr());
            assertEquals("gateway:8091", row.getGatewayAddr());

            // the query wrapper must keep the optional filters and the api scope
            Object wrapper = accessRecorder.argsOf("selectList")[0];
            String sql = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper) wrapper)
                    .getSqlSegment().toLowerCase();
            assertTrue(sql, sql.contains("api_id"));
            assertTrue(sql.contains("status"));
            assertTrue(sql.contains("create_time"));
        } finally {
            Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
            field.setAccessible(true);
            field.set(null, null);
        }
    }

    @Test
    public void pageByApiIdOmitsBlankFilters() throws Exception {
        AppClientDao emptyDao = new AppClientDao() {
            @Override
            public List<AppClientEntity> listAll() {
                return Collections.emptyList();
            }
        };
        installBeanFactory(emptyDao);
        try {
            accessRecorder.stub("selectList", Collections.<AccessRecordEntity>emptyList());
            try {
                service.pageByApiId(2L, null, null, null, "", "");
            } finally {
                PageHelper.clearPage();
            }
            Object wrapper = accessRecorder.argsOf("selectList")[0];
            String sql = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper) wrapper)
                    .getSqlSegment().toLowerCase();
            assertTrue("blank strings must not become create_time predicates", !sql.contains("create_time"));
            assertTrue(sql.contains("api_id"));
        } finally {
            Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
            field.setAccessible(true);
            field.set(null, null);
        }
    }

    private static void installBeanFactory(AppClientDao dao) throws Exception {
        Object factory = Proxy.newProxyInstance(OverviewServiceTest.class.getClassLoader(),
                new Class<?>[]{org.springframework.beans.factory.config.ConfigurableListableBeanFactory.class},
                (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && a != null && a.length > 0 && a[0] == AppClientDao.class) {
                        return dao;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    if ("toString".equals(m.getName())) {
                        return "fakeBeanFactory";
                    }
                    Class<?> rt = m.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    return null;
                });
        Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, factory);
    }
}
