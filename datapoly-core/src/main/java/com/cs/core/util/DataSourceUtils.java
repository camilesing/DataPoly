// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.core.util.ClassLoaderUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.model.*;
import com.cs.persistence.entity.*;
import com.zaxxer.hikari.HikariDataSource;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.env.Environment;

import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
@UtilityClass
public final class DataSourceUtils {

    public static final int MAX_THREAD_COUNT = 10;
    public static final int MAX_TIMEOUT_MS = 60000;
    public static final long MAX_LIFE_TIME_MS = TimeUnit.MINUTES.toMillis(60);
    public static final long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(60);
    private static final String DEFAULT_AES_KEY = "6635BC05BC357FEC7A85FDB9C972AD01";
    private static volatile AES toolAES;
    private static final Map<String, URLClassLoader> classLoaderMap = new ConcurrentHashMap<>();
    private static final Map<Long, Pair<DataSourceEntity, HikariDataSource>> datasourceMap = new ConcurrentHashMap<>();
    private static final Map<Long, Object> dataSourceLocks = new ConcurrentHashMap<>();

    /**
     * Get (or create on demand) the datasource connection pool (H3): per-id lock, single-flight — concurrent first accesses to the same id create only one pool,
     * eliminating the "double-created pool that overwrites another and is never closed" leak caused by the containsKey→create→put race.
     * Order is create→validate→swap-in→close old: on validation failure the new pool is closed before throwing; on config change the old pool is closed after the swap.
     */
    public static HikariDataSource getHikariDataSource(DataSourceEntity entity, String driverPath) {
        if (null == entity.getId()) {
            throw new IllegalArgumentException("DataSource entity id can not be null!");
        }
        decrypt(entity);
        Object lock = dataSourceLocks.computeIfAbsent(entity.getId(), k -> new Object());
        synchronized (lock) {
            Pair<DataSourceEntity, HikariDataSource> current = datasourceMap.get(entity.getId());
            if (null != current && DataSourceEntity.isSame(entity, current.getKey())) {
                return current.getRight();
            }

            HikariDataSource ds = createDataSource(entity, driverPath);
            try (Connection connection = ds.getConnection()) {
                if (StringUtils.isNotBlank(entity.getType().getSql())) {
                    try (Statement statement = connection.createStatement()) {
                        statement.setQueryTimeout(2);
                        statement.execute(entity.getType().getSql());
                    }
                } else {
                    if (!connection.isValid(2)) {
                        throw new RuntimeException("Connection is invalid!");
                    }
                }
            } catch (RuntimeException e) {
                closeHikariDataSource(ds);
                throw e;
            } catch (Exception e) {
                closeHikariDataSource(ds);
                throw new RuntimeException(e);
            }

            Pair<DataSourceEntity, HikariDataSource> previous =
                    datasourceMap.put(entity.getId(), Pair.of(entity, ds));
            if (null != previous && previous.getRight() != ds) {
                closeHikariDataSource(previous.getRight());
            }
            return ds;
        }
    }

    public static void dropHikariDataSource(Long dataSourceId) {
        if (null == dataSourceId) {
            return;
        }
        Object lock = dataSourceLocks.computeIfAbsent(dataSourceId, k -> new Object());
        synchronized (lock) {
            Pair<DataSourceEntity, HikariDataSource> dsPair = datasourceMap.remove(dataSourceId);
            if (null != dsPair) {
                closeHikariDataSource(dsPair.getRight());
            }
        }
    }

    public static void closeHikariDataSource(HikariDataSource ds) {
        try {
            ds.close();
        } catch (Exception e) {
            log.warn("Error when close HikariDataSource:{}", e.getMessage());
        }
    }

    public static Set<Long> getAllDataSourceIdSet() {
        return new HashSet<>(datasourceMap.keySet());
    }

    public static HikariDataSource createDataSource(DataSourceEntity properties, String driverPath) {
        Properties parameters = new Properties();
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("The_JDBC_Connection_" + properties.getType() + "_" + properties.getName());
        ds.setJdbcUrl(properties.getUrl());
        if (ProductTypeEnum.ORACLE == properties.getType()) {
            ds.setConnectionTestQuery(properties.getType().getTestSql());
            // https://blog.csdn.net/qq_20960159/article/details/78593936
            System.getProperties().setProperty("oracle.jdbc.J2EE13Compliant", "true");
            // Oracle jdbc connections need an extra parameter to control whether remarks are fetched
            parameters.put("remarksReporting", "true");
        } else if (StringUtils.isNotBlank(properties.getType().getTestSql())) {
            ds.setConnectionTestQuery(properties.getType().getTestSql());
        }
        ds.setMaximumPoolSize(getPoolConfigValue(properties, PoolConfig::getMaximumPoolSize, MAX_THREAD_COUNT));
        ds.setMinimumIdle(getPoolConfigValue(properties, PoolConfig::getMinimumIdle, MAX_THREAD_COUNT));
        ds.setMaxLifetime(getPoolConfigValue(properties, PoolConfig::getMaxLifetime, MAX_LIFE_TIME_MS));
        ds.setConnectionTimeout(getPoolConfigValue(properties, PoolConfig::getConnectionTimeout, CONNECTION_TIMEOUT));
        ds.setIdleTimeout(getPoolConfigValue(properties, PoolConfig::getIdleTimeout, MAX_TIMEOUT_MS));
        SimpleDataSource dataSource = new SimpleDataSource(
                createURLClassLoader(driverPath, properties.getDriver()),
                properties.getUrl(),
                properties.getDriver(),
                properties.getUsername(),
                properties.getPassword(),
                parameters
        );
        ds.setDataSource(dataSource);

        log.info("Create HikariDataSource for {} with pool config: {}", ds.getPoolName(), properties.getPoolConfig());
        return ds;
    }

    private static URLClassLoader createURLClassLoader(String driverPath, String driverClass) {
        if (StringUtils.isBlank(driverPath)) {
            throw new RuntimeException("Invalid driver path,can not be empty!");
        }
        if (StringUtils.isBlank(driverClass)) {
            throw new RuntimeException("Invalid driver class,can not be empty!");
        }
        ClassLoader parent = ClassLoaderUtil.getSystemClassLoader().getParent();
        URLClassLoader loader = getOrCreateClassLoader(driverPath, parent);
        try {
            Class<?> clazz = loader.loadClass(driverClass);
            clazz.getConstructor().newInstance();
            return loader;
        } catch (Exception e) {
            log.error("Could not load class : {} from driver path: {}", driverClass, driverPath, e);
            throw new RuntimeException(e);
        }
    }

    private static URLClassLoader getOrCreateClassLoader(String path, ClassLoader parent) {
        URLClassLoader urlClassLoader = classLoaderMap.get(path);
        if (null == urlClassLoader) {
            synchronized (DataSourceUtils.class) {
                urlClassLoader = classLoaderMap.get(path);
                if (null == urlClassLoader) {
                    log.info("Create jar classLoader from path: {}", path);
                    urlClassLoader = new JarFileClassLoader(path, parent);
                    classLoaderMap.put(path, urlClassLoader);
                }
            }
        }
        return urlClassLoader;
    }

    private static boolean isUseDataSourceUserPassEncrypt() {
        String KEY = "datapoly.datasource.encrypt";
        return "true".equalsIgnoreCase(SpringUtil.getBean(Environment.class).getProperty(KEY));
    }

    /**
     * Datasource password AES key (S5): prefer config {@code datapoly.datasource.aes-key} (overridable via
     * the DATAPOLY_DS_AES_KEY environment variable); the original built-in key is kept as default for compatibility with existing ciphertext.
     */
    private static AES getToolAES() {
        if (null == toolAES) {
            synchronized (DataSourceUtils.class) {
                if (null == toolAES) {
                    String aesKey = DEFAULT_AES_KEY;
                    try {
                        String configured = SpringUtil.getBean(Environment.class)
                                .getProperty("datapoly.datasource.aes-key");
                        if (StringUtils.isNotBlank(configured)) {
                            aesKey = configured;
                        }
                    } catch (Exception e) {
                        // Fall back to the default key when the Spring context is unavailable (e.g. utility unit tests)
                    }
                    toolAES = SecureUtil.aes(aesKey.getBytes());
                }
            }
        }
        return toolAES;
    }

    public static void encrypt(DataSourceEntity dataSourceEntity) {
        if (isUseDataSourceUserPassEncrypt()) {
            try {
                String encryptUsername = encryptStr(dataSourceEntity.getUsername());
                String encryptPassword = encryptStr(dataSourceEntity.getPassword());
                dataSourceEntity.setUsername(encryptUsername);
                dataSourceEntity.setPassword(encryptPassword);
            } catch (Exception e) {
                // Ignore the exception; tolerates upgrade artifacts
                log.error("Encrypt used by AES error: {}", e.getMessage(), e);
            }
        }
    }

    public static void decrypt(DataSourceEntity dataSourceEntity) {
        if (isUseDataSourceUserPassEncrypt()) {
            try {
                String decryptUsername = decryptStr(dataSourceEntity.getUsername());
                String decryptPassword = decryptStr(dataSourceEntity.getPassword());
                dataSourceEntity.setUsername(decryptUsername);
                dataSourceEntity.setPassword(decryptPassword);
            } catch (Exception e) {
                // Ignore the exception; tolerates upgrade artifacts (double decryption of plaintext passwords lands here by design — logged at debug to avoid hot-path spam)
                log.debug("Decrypt used by AES error: {}", e.getMessage());
            }
        }
    }

    private static String encryptStr(String str) {
        if (null == str) {
            return null;
        }
        return getToolAES().encryptHex(str);
    }

    private static String decryptStr(String str) {
        if (null == str) {
            return null;
        }
        return getToolAES().decryptStr(str);
    }

    private static <T> T getPoolConfigValue(DataSourceEntity entity, Function<PoolConfig, T> getter, T defaultValue) {
        if (entity.getPoolConfig() == null) {
            return defaultValue;
        }
        T value = getter.apply(entity.getPoolConfig());
        return value != null ? value : defaultValue;
    }
}
