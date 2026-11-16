// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import org.junit.Test;

import java.sql.Timestamp;

import static org.junit.Assert.*;

/**
 * DataSourceEntity.isSame decides whether a cached pool can be reused: every field that
 * participates in a live connection (version/driver/url/username/password/poolConfig)
 * must be compared, and any null argument makes the comparison fail fast.
 */
public class DataSourceEntityTest {

    private static DataSourceEntity full(long id, String suffix) {
        PoolConfig pool = PoolConfig.builder()
                .maximumPoolSize(5)
                .minimumIdle(2)
                .maxLifetime(1234L)
                .connectionTimeout(567L)
                .idleTimeout(89)
                .build();
        return DataSourceEntity.builder()
                .id(id)
                .name("ds-" + id)
                .version("8.0" + suffix)
                .driver("com.mysql.jdbc.Driver" + suffix)
                .url("jdbc:mysql://host:3306/db" + suffix)
                .username("user" + suffix)
                .password("secret" + suffix)
                .createTime(new Timestamp(0L))
                .updateTime(new Timestamp(0L))
                .poolConfig(pool)
                .build();
    }

    @Test
    public void nullArgumentsAreNeverSame() {
        DataSourceEntity entity = full(1, "");
        assertFalse(DataSourceEntity.isSame(null, null));
        assertFalse(DataSourceEntity.isSame(entity, null));
        assertFalse(DataSourceEntity.isSame(null, entity));
    }

    @Test
    public void identicalDefinitionsAreSame() {
        assertTrue(DataSourceEntity.isSame(full(1, ""), full(1, "")));
        // both sides null in every field
        assertTrue(DataSourceEntity.isSame(new DataSourceEntity(), new DataSourceEntity()));
    }

    @Test
    public void differingFieldFailsTheComparison() {
        DataSourceEntity lhs = full(1, "");
        assertFalse(DataSourceEntity.isSame(lhs, mutated(d -> d.setVersion("9.0"))));
        assertFalse(DataSourceEntity.isSame(lhs, mutated(d -> d.setDriver("other.Driver"))));
        assertFalse(DataSourceEntity.isSame(lhs, mutated(d -> d.setUrl("jdbc:mysql://other:3306/db"))));
        assertFalse(DataSourceEntity.isSame(lhs, mutated(d -> d.setUsername("other"))));
        assertFalse(DataSourceEntity.isSame(lhs, mutated(d -> d.setPassword("other"))));
        assertFalse(DataSourceEntity.isSame(lhs, mutated(d -> d.setPoolConfig(null))));
        assertFalse(DataSourceEntity.isSame(lhs, mutated(d ->
                d.setPoolConfig(PoolConfig.builder().maximumPoolSize(6).build()))));
    }

    @Test
    public void nullVersusValueFailsTheComparison() {
        assertFalse(DataSourceEntity.isSame(full(1, ""), new DataSourceEntity()));
        assertFalse(DataSourceEntity.isSame(new DataSourceEntity(), full(1, "")));
        // single-field null vs value
        DataSourceEntity lhs = full(1, "");
        DataSourceEntity rhs = full(1, "");
        rhs.setVersion(null);
        assertFalse(DataSourceEntity.isSame(lhs, rhs));
        assertFalse(DataSourceEntity.isSame(rhs, lhs));
    }

    @Test
    public void fieldsOutsideTheLiveConnectionDoNotMatter() {
        // name/type/createTime/updateTime are metadata: changing them must not recycle pools
        DataSourceEntity lhs = full(1, "");
        DataSourceEntity rhs = full(1, "");
        rhs.setName("renamed");
        rhs.setCreateTime(new Timestamp(42_000L));
        assertTrue(DataSourceEntity.isSame(lhs, rhs));
    }

    private interface Mutator {
        void apply(DataSourceEntity entity);
    }

    private DataSourceEntity mutated(Mutator mutator) {
        DataSourceEntity entity = full(1, "");
        mutator.apply(entity);
        return entity;
    }
}
