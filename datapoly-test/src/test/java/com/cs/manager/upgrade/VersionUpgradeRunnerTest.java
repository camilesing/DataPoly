// Use of this source code is governed by a BSD-style license
package com.cs.manager.upgrade;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.dao.VersionCommitDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.ApiOnlineEntity;
import com.cs.persistence.entity.VersionCommitEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Drives the startup VCS upgrade with fake DAOs (subclasses) and a SpringUtil proxy that
 * resolves the runner itself, covering the poison-record isolation, skip paths and the
 * transactional snapshot method.
 */
public class VersionUpgradeRunnerTest {

    private static class FakeApiAssignmentDao extends ApiAssignmentDao {
        List<Long> upgradeIds = new ArrayList<>();
        List<Long> resetIds;
        Map<Long, ApiAssignmentEntity> byId = new HashMap<>();
        Set<Long> failGetById = new HashSet<>();

        @Override
        public List<Long> getUpgradeOnlineAssignments() {
            return upgradeIds;
        }

        @Override
        public void resetUpgradeOnlineAssignments(List<Long> ids) {
            this.resetIds = ids;
        }

        @Override
        public ApiAssignmentEntity getById(Long id, boolean withSql) {
            if (failGetById.contains(id)) {
                throw new IllegalStateException("poison " + id);
            }
            return byId.get(id);
        }
    }

    private static class FakeVersionCommitDao extends VersionCommitDao {
        Map<Long, VersionCommitEntity> latest = new HashMap<>();
        List<Long> createdBizIds = new ArrayList<>();
        List<String> createdDescriptions = new ArrayList<>();
        List<String> createdContents = new ArrayList<>();
        Set<Long> failLatest = new HashSet<>();
        int seq;

        @Override
        public VersionCommitEntity getLatestVersion(Long bizId) {
            if (failLatest.contains(bizId)) {
                throw new IllegalStateException("latest lookup failed for " + bizId);
            }
            return latest.get(bizId);
        }

        @Override
        public VersionCommitEntity createVersion(Long bizId, String description, String content) {
            createdBizIds.add(bizId);
            createdDescriptions.add(description);
            createdContents.add(content);
            seq++;
            return VersionCommitEntity.builder()
                    .id(100L + seq)
                    .bizId(bizId)
                    .version(seq)
                    .description(description)
                    .content(content)
                    .build();
        }
    }

    private static class FakeApiOnlineDao extends ApiOnlineDao {
        List<ApiOnlineEntity> upserts = new ArrayList<>();

        @Override
        public void upsert(ApiOnlineEntity entity) {
            upserts.add(entity);
        }
    }

    private VersionUpgradeRunner runner;
    private FakeApiAssignmentDao assignmentDao;
    private FakeVersionCommitDao versionCommitDao;
    private FakeApiOnlineDao apiOnlineDao;

    @Before
    public void setUp() throws Exception {
        runner = new VersionUpgradeRunner();
        assignmentDao = new FakeApiAssignmentDao();
        versionCommitDao = new FakeVersionCommitDao();
        apiOnlineDao = new FakeApiOnlineDao();
        inject("apiAssignmentDao", assignmentDao);
        inject("versionCommitDao", versionCommitDao);
        inject("apiOnlineDao", apiOnlineDao);
        installSpringUtil(runner);
    }

    @After
    public void tearDown() throws Exception {
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    private void inject(String name, Object value) throws Exception {
        Field field = VersionUpgradeRunner.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(runner, value);
    }

    private void installSpringUtil(VersionUpgradeRunner target) throws Exception {
        Object beanFactory = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConfigurableListableBeanFactory.class},
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] == VersionUpgradeRunner.class) {
                        return target;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "fake-bean-factory";
                    }
                    return null;
                });
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    private static ApiAssignmentEntity assignment(long id, String name) {
        return ApiAssignmentEntity.builder()
                .id(id)
                .name(name)
                .method(HttpMethodEnum.POST)
                .path("/" + name + "/list")
                .groupId(2L)
                .moduleId(3L)
                .datasourceId(4L)
                .open(false)
                .alarm(true)
                .flowStatus(true)
                .build();
    }

    // ------------------------------------------------------------------
    // run()
    // ------------------------------------------------------------------

    @Test
    public void runUpgradesOnlyAssignmentsWithoutAnyVersion() throws Exception {
        assignmentDao.upgradeIds = new ArrayList<>(Arrays.asList(1L, 2L));
        assignmentDao.byId.put(1L, assignment(1L, "orders"));
        assignmentDao.byId.put(2L, assignment(2L, "users"));
        // id 2 already has a version snapshot: no re-upgrade needed
        versionCommitDao.latest.put(2L, VersionCommitEntity.builder().id(50L).version(1).build());

        runner.run(null);

        assertEquals(Collections.singletonList(1L), versionCommitDao.createdBizIds);
        assertEquals(1, apiOnlineDao.upserts.size());
        // both ids count as handled, so both flags get reset
        assertEquals(Arrays.asList(1L, 2L), assignmentDao.resetIds);
    }

    @Test
    public void runSkipsAssignmentThatDisappearedButStillResetsItsFlag() throws Exception {
        assignmentDao.upgradeIds = new ArrayList<>(Collections.singletonList(3L));
        assignmentDao.byId.remove(3L);

        runner.run(null);

        assertTrue(versionCommitDao.createdBizIds.isEmpty());
        assertTrue(apiOnlineDao.upserts.isEmpty());
        assertEquals(Collections.singletonList(3L), assignmentDao.resetIds);
    }

    @Test
    public void runKeepsPoisonRecordForNextStartupAndContinues() throws Exception {
        assignmentDao.upgradeIds = new ArrayList<>(Arrays.asList(4L, 5L));
        versionCommitDao.failLatest.add(4L);
        assignmentDao.byId.put(5L, assignment(5L, "goods"));

        runner.run(null);

        assertEquals(Collections.singletonList(5L), versionCommitDao.createdBizIds);
        assertEquals(Collections.singletonList(5L), assignmentDao.resetIds);
    }

    @Test
    public void runWithNothingToUpgradeResetsEmptyList() throws Exception {
        runner.run(null);

        assertNotNull(assignmentDao.resetIds);
        assertTrue(assignmentDao.resetIds.isEmpty());
    }

    @Test
    public void runDerivesOnlineRowFromTheSnapshot() throws Exception {
        assignmentDao.upgradeIds = new ArrayList<>(Collections.singletonList(6L));
        assignmentDao.byId.put(6L, assignment(6L, "audit"));

        runner.run(null);

        assertEquals(1, apiOnlineDao.upserts.size());
        ApiOnlineEntity online = apiOnlineDao.upserts.get(0);
        assertEquals(Long.valueOf(6L), online.getApiId());
        assertEquals("audit", online.getName());
        assertEquals(HttpMethodEnum.POST, online.getMethod());
        assertEquals("/audit/list", online.getPath());
        assertEquals(Long.valueOf(101L), online.getCommitId());
        assertEquals(Integer.valueOf(1), online.getVersion());
        // the online row carries the serialized snapshot content
        assertEquals(versionCommitDao.createdContents.get(0), online.getContent());
        assertTrue(online.getContent().contains("/audit/list"));
        assertEquals("First Commit By Upgrade.", versionCommitDao.createdDescriptions.get(0));
    }

    // ------------------------------------------------------------------
    // upgradeApiAssignment()
    // ------------------------------------------------------------------

    @Test
    public void upgradeApiAssignmentSnapshotsDefinitionAndUpsertsOnlineRow() {
        ApiAssignmentEntity entity = assignment(9L, "billing");

        runner.upgradeApiAssignment(entity);

        assertEquals(Collections.singletonList(9L), versionCommitDao.createdBizIds);
        assertEquals(1, apiOnlineDao.upserts.size());
        ApiOnlineEntity online = apiOnlineDao.upserts.get(0);
        assertEquals(Long.valueOf(9L), online.getApiId());
        assertEquals(Long.valueOf(2L), online.getGroupId());
        assertEquals(Long.valueOf(3L), online.getModuleId());
        assertEquals(Long.valueOf(4L), online.getDatasourceId());
        assertFalse(online.getOpen());
        assertTrue(online.getAlarm());
        assertTrue(online.getFlowStatus());
        assertEquals(versionCommitDao.createdContents.get(0), online.getContent());
        assertTrue(online.getContent().contains("billing"));
    }
}
