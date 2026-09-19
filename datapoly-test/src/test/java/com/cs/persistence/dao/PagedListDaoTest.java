// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.persistence.PersistenceTestSupport;
import com.cs.persistence.mapper.ApiAssignmentMapper;
import com.cs.persistence.mapper.ApiGroupMapper;
import com.cs.persistence.mapper.ApiModuleMapper;
import com.cs.persistence.mapper.ApiOnlineMapper;
import com.cs.persistence.mapper.AppClientMapper;
import com.cs.persistence.mapper.DataSourceMapper;
import com.cs.persistence.mapper.McpClientMapper;
import com.cs.persistence.mapper.McpToolMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Paged admin list queries (PageUtils.getPage / PageHelper) must not carry their own
 * trailing LIMIT: PageHelper appends "LIMIT ?" without merging, so "LIMIT 10000 LIMIT ?"
 * fails to parse in MySQL. Only the unpaginated full scans keep ListGuard.LIMIT_SQL.
 */
public class PagedListDaoTest {

    private static class Recorder {
        final Map<String, List<Object[]>> calls = new LinkedHashMap<>();
        private final Map<String, Queue<Object>> stubs = new HashMap<>();

        @SuppressWarnings("unchecked")
        <M> M create(Class<M> type) {
            return (M) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (p, m, a) -> {
                        calls.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(a);
                        Class<?> rt = m.getReturnType();
                        Queue<Object> queue = stubs.get(m.getName());
                        if (queue != null && !queue.isEmpty()) {
                            Object result = queue.poll();
                            if (result instanceof RuntimeException) {
                                throw (RuntimeException) result;
                            }
                            return result;
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

        Object[] argsOf(String method) {
            return calls.get(method).get(0);
        }
    }

    @BeforeClass
    public static void initEntityMetadata() {
        PersistenceTestSupport.initTableInfo(
                com.cs.persistence.entity.ApiGroupEntity.class,
                com.cs.persistence.entity.ApiModuleEntity.class,
                com.cs.persistence.entity.DataSourceEntity.class,
                com.cs.persistence.entity.AppClientEntity.class,
                com.cs.persistence.entity.McpClientEntity.class,
                com.cs.persistence.entity.McpToolEntity.class,
                com.cs.persistence.entity.ApiAssignmentEntity.class,
                com.cs.persistence.entity.ApiOnlineEntity.class);
    }

    private Recorder groupRecorder;
    private Recorder moduleRecorder;
    private Recorder dataSourceRecorder;
    private Recorder appClientRecorder;
    private Recorder mcpClientRecorder;
    private Recorder mcpToolRecorder;
    private Recorder assignmentRecorder;
    private Recorder onlineRecorder;

    @Before
    public void setUp() {
        groupRecorder = new Recorder();
        moduleRecorder = new Recorder();
        dataSourceRecorder = new Recorder();
        appClientRecorder = new Recorder();
        mcpClientRecorder = new Recorder();
        mcpToolRecorder = new Recorder();
        assignmentRecorder = new Recorder();
        onlineRecorder = new Recorder();
    }

    private String sqlSegmentOf(Recorder recorder) {
        Object[] args = recorder.argsOf("selectList");
        assertEquals("single-arg selectList expected", 1, args.length);
        return ((Wrapper<?>) args[0]).getSqlSegment();
    }

    @Test
    public void testPagedListQueriesLeaveLimitToPageHelper() {
        ApiGroupDao apiGroupDao = new ApiGroupDao();
        DataTaskTestSupport.setField(apiGroupDao, "apiGroupMapper", groupRecorder.create(ApiGroupMapper.class));
        ApiModuleDao apiModuleDao = new ApiModuleDao();
        DataTaskTestSupport.setField(apiModuleDao, "apiModuleMapper", moduleRecorder.create(ApiModuleMapper.class));
        DataSourceDao dataSourceDao = new DataSourceDao();
        DataTaskTestSupport.setField(dataSourceDao, "dataSourceMapper", dataSourceRecorder.create(DataSourceMapper.class));
        AppClientDao appClientDao = new AppClientDao();
        DataTaskTestSupport.setField(appClientDao, "appClientMapper", appClientRecorder.create(AppClientMapper.class));
        McpClientDao mcpClientDao = new McpClientDao();
        DataTaskTestSupport.setField(mcpClientDao, "mcpClientMapper", mcpClientRecorder.create(McpClientMapper.class));
        McpToolDao mcpToolDao = new McpToolDao();
        DataTaskTestSupport.setField(mcpToolDao, "mcpToolMapper", mcpToolRecorder.create(McpToolMapper.class));

        groupRecorder.stub("selectList", Collections.emptyList(), Collections.emptyList());
        apiGroupDao.listAll(null);
        apiGroupDao.listAll("text");
        moduleRecorder.stub("selectList", Collections.emptyList(), Collections.emptyList());
        apiModuleDao.listAll(null);
        apiModuleDao.listAll("text");
        dataSourceRecorder.stub("selectList", Collections.emptyList(), Collections.emptyList());
        dataSourceDao.listAll(null);
        dataSourceDao.listAll("text");
        appClientRecorder.stub("selectList", Collections.emptyList(), Collections.emptyList());
        appClientDao.listAll(null);
        appClientDao.listAll("text");
        mcpClientRecorder.stub("selectList", Collections.emptyList());
        mcpClientDao.listAll(null);
        mcpToolRecorder.stub("selectList", Collections.emptyList());
        mcpToolDao.listAll(null);

        for (Recorder recorder : Arrays.asList(groupRecorder, moduleRecorder, dataSourceRecorder,
                appClientRecorder, mcpClientRecorder, mcpToolRecorder)) {
            String segment = sqlSegmentOf(recorder);
            assertFalse("paged query must not carry its own LIMIT, got: " + segment,
                    segment.contains("LIMIT"));
        }
    }

    @Test
    public void testUnpaginatedRegistryScansKeepGuardLimit() {
        ApiAssignmentDao apiAssignmentDao = new ApiAssignmentDao();
        DataTaskTestSupport.setField(apiAssignmentDao, "apiAssignmentMapper",
                assignmentRecorder.create(ApiAssignmentMapper.class));
        ApiOnlineDao apiOnlineDao = new ApiOnlineDao();
        DataTaskTestSupport.setField(apiOnlineDao, "apiOnlineMapper",
                onlineRecorder.create(ApiOnlineMapper.class));

        assignmentRecorder.stub("selectList", Collections.emptyList());
        apiAssignmentDao.listAll();
        onlineRecorder.stub("selectList", Collections.emptyList());
        apiOnlineDao.listAll();

        assertTrue(sqlSegmentOf(assignmentRecorder).contains(ListGuard.LIMIT_SQL));
        assertTrue(sqlSegmentOf(onlineRecorder).contains(ListGuard.LIMIT_SQL));
    }
}
