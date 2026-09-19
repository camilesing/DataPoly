// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import com.cs.common.util.PasswordUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * 出厂口令三处一致性守卫：v1.0.0 基线种历史口令 123456（changeset 已发布，哈希不可回改）、
 * v1.4.1 迁移把未改密码的部署迁到新出厂口令、AdminPasswordInitializer 常量识别新出厂口令——
 * 任一处漂移即失败。
 */
public class AdminSeedPasswordTest {

    private static final String NEW_FACTORY_PASSWORD = "DataPoly@123456";
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2a\\$10\\$[A-Za-z0-9./]{53}");

    @Test
    public void baselineSeedsHistoricalFactoryPassword() throws IOException {
        for (String dialect : new String[]{"db", "pg"}) {
            List<String> hashes = readBcryptHashes(dialect + "/migration/V1_0_0__system-dml.sql");
            assertEquals("baseline should seed exactly one admin bcrypt hash: " + dialect, 1, hashes.size());
            String hash = hashes.get(0);
            assertEquals("baseline admin hash must stay 123456 (checksum-stable seed): " + dialect,
                    hash, PasswordUtils.encryptPassword("123456", saltOf(hash)));
        }
    }

    @Test
    public void migrationAndInitializerCarryNewFactoryPassword() throws IOException {
        for (String dialect : new String[]{"db", "pg"}) {
            List<String> hashes = readBcryptHashes(dialect + "/migration/V1_4_1__admin-password-dml.sql");
            assertEquals("migration should contain target hash plus legacy guard hash: " + dialect,
                    2, hashes.size());
            String target = hashes.get(0);
            assertEquals("migration must only touch the historical factory hash: " + dialect,
                    baselineHash(dialect), hashes.get(1));
            assertEquals("migration target hash must encode the new factory password: " + dialect,
                    target, PasswordUtils.encryptPassword(NEW_FACTORY_PASSWORD, saltOf(target)));
            assertEquals("AdminPasswordInitializer constant drifted from the migration target: " + dialect,
                    target, AdminPasswordInitializer.DEFAULT_ADMIN_BCRYPT_HASH);
        }
    }

    private static String baselineHash(String dialect) throws IOException {
        return readBcryptHashes(dialect + "/migration/V1_0_0__system-dml.sql").get(0);
    }

    /** bcrypt 哈希前 29 字符即盐串（$2a$10$ + 22 字符），与 login/changePassword 的校验路径一致。 */
    private static String saltOf(String hash) {
        return hash.substring(0, 29);
    }

    private static List<String> readBcryptHashes(String classpathLocation) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classpathLocation)) {
            if (null == in) {
                throw new IOException("classpath resource not found: " + classpathLocation);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> hashes = new ArrayList<>();
            Matcher matcher = BCRYPT_HASH.matcher(sql);
            while (matcher.find()) {
                hashes.add(matcher.group());
            }
            return hashes;
        }
    }
}
