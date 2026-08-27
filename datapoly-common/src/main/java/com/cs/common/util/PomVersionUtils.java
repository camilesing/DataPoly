// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.URLUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Paths;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.*;

/**
 * Utility for reading the project version
 */
@Slf4j
@UtilityClass
public final class PomVersionUtils {

    private static final String PREFIX = "version=";

    private static final Map<String, String> persistCache = new ConcurrentHashMap<>();

    public static String getCachedProjectVersion() {
        String versionStr = persistCache.computeIfAbsent(PREFIX, key -> getProjectVersion());
        return Optional.ofNullable(versionStr).orElse("1.0.0");
    }

    private static String getProjectVersion() {
        Class<?> clazz = PomVersionUtils.class;
        String implementationVersion = clazz.getPackage().getImplementationVersion();
        if (implementationVersion != null) {
            return implementationVersion;
        }
        String resourcePath = clazz.getResource("").toString();
        if (resourcePath.startsWith("file:")) {
            return getProjectVersionFromFile(resourcePath);
        } else if (resourcePath.startsWith("jar:")) {
            return getProjectVersionFromJar(clazz);
        } else {
            return null;
        }
    }

    private static String getProjectVersionFromFile(String classPath) {
        String basePath = classPath.substring(0, classPath.indexOf("/classes/"));
        basePath = URLUtil.decode(FileUtil.normalize(basePath));
        File propertiesFile = Paths.get(basePath, "maven-archiver", "pom.properties").toFile();
        if (propertiesFile.exists()) {
            return extractPomVersion(FileUtil.getInputStream(propertiesFile));
        }
        return null;
    }

    private static String getProjectVersionFromJar(Class<?> clazz) {
        ProtectionDomain protectionDomain = clazz.getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();
        try (JarFile jarFile = new JarFile(codeSource.getLocation().getPath())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith("META-INF/maven/") && entry.getName().endsWith("/pom.properties")) {
                    return extractPomVersion(jarFile.getInputStream(entry));
                }
            }
            return jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        } catch (IOException e) {
            log.warn("Get project version from jar failed:{}", e.getMessage(), e);
        }
        return null;
    }

    private static String extractPomVersion(InputStream inputStream) {
        String line;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            while ((line = bufferedReader.readLine()) != null) {
                if (line.startsWith(PREFIX)) {
                    return line.substring(PREFIX.length());
                }
            }
        } catch (IOException e) {
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println(getProjectVersion());
    }
}
