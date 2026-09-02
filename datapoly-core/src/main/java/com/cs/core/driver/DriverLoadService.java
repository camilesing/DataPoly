// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.driver;

import cn.hutool.core.io.FileUtil;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.*;
import com.cs.core.dto.DatabaseTypeDriverResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DriverLoadService {

    // package-visible for test injection (same precedent as GatewaySourceFilter.matches)
    Map<ProductTypeEnum, Map<String, File>> drivers = new EnumMap<>(ProductTypeEnum.class);

    @Value("${datasource.driver.base-path}")
    private String driversBasePath;

    @EventListener(ApplicationReadyEvent.class)
    public void loadDrivers() {
        try {
            doLoadDrivers();
            log.info("Finish load jdbc drivers from local path: {}", driversBasePath);
        } catch (Exception e) {
            log.error("load drivers failed:{}", e.getMessage(), e);
            throw e;
        }
    }

    private void doLoadDrivers() {
        File file = new File(driversBasePath);
        File[] types = file.listFiles();
        if (ArrayUtils.isEmpty(types)) {
            throw new IllegalArgumentException(
                    "No drivers type found from path:" + driversBasePath);
        }
        for (File type : types) {
            if (!ProductTypeEnum.exists(type.getName())) {
                continue;
            }
            // Sync tools and macOS sidecars litter the tree with non-directory artifacts
            // (._*, .DS_Store); only real subdirectories count as driver versions.
            File[] driverVersions = type.listFiles(
                    f -> null != f && f.isDirectory() && !f.getName().startsWith("."));
            if (ArrayUtils.isEmpty(driverVersions)) {
                throw new IllegalArgumentException(
                        "No driver version found from path:" + type.getAbsolutePath());
            }
            for (File driverVersion : driverVersions) {
                if (ArrayUtils.isEmpty(driverVersion.listFiles())) {
                    throw new IllegalArgumentException(
                            "No driver version jar file found from path:" + driverVersion.getAbsolutePath());
                }
                ProductTypeEnum typeEnum = ProductTypeEnum.of(type.getName());
                Map<String, File> versionMap = drivers.computeIfAbsent(typeEnum, k -> new HashMap<>());
                versionMap.put(driverVersion.getName(), driverVersion);
                log.info("Load driver for {} ,version:{},path:{}",
                        typeEnum.getName(), driverVersion.getName(), driverVersion.getAbsolutePath());
            }
        }
    }

    public List<String> getDriverVersion(ProductTypeEnum dbTypeEnum) {
        return Optional.ofNullable(drivers.get(dbTypeEnum)).orElseGet(HashMap::new)
                .keySet().stream().collect(Collectors.toList());
    }

    public Map<String, File> getDriverVersionWithPath(ProductTypeEnum dbTypeEnum) {
        return Optional.ofNullable(drivers.get(dbTypeEnum)).orElse(new HashMap<>());
    }

    /**
     * Get the driver directory for the given type and version (H3): contract — never returns null.
     * Throws a business exception (ERROR_RESOURCE_NOT_EXISTS) when the type has no directory
     * or the version is missing (datasource config drifts from the drivers directory), replacing the former NPE.
     */
    public File getVersionDriverFile(ProductTypeEnum dbTypeEnum, String driverVersion) {
        Map<String, File> versionMap = drivers.get(dbTypeEnum);
        File driverFile = (null == versionMap) ? null : versionMap.get(driverVersion);
        if (null == driverFile) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS,
                    "datasource.driver.not.found", dbTypeEnum.getName(), driverVersion);
        }
        return driverFile;
    }

    public List<DatabaseTypeDriverResponse> getDrivers(ProductTypeEnum dbTypeEnum) {
        List<DatabaseTypeDriverResponse> lists = new ArrayList<>();
        getDriverVersionWithPath(dbTypeEnum)
                .forEach(
                        (k, v) ->
                                lists.add(
                                        DatabaseTypeDriverResponse.builder()
                                                .driverVersion(k)
                                                .driverClass(dbTypeEnum.getDriver())
                                                .driverPath(v.getAbsolutePath())
                                                .jarFiles(
                                                        FileUtil.listFileNames(v.getAbsolutePath()).stream()
                                                                .filter(n -> null != n && !n.startsWith("."))
                                                                .collect(Collectors.toList())
                                                )
                                                .build()
                                )
                );
        return lists;
    }
}
