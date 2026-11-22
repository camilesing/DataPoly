// Use of this source code is governed by a BSD-style license
package com.cs.core.driver;

import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.*;
import com.cs.core.dto.DatabaseTypeDriverResponse;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Driver directory lookup contract (H3): missing type or version throws a controlled business exception instead of an NPE; a hit returns the directory file.
 * Directory scanning ({@code loadDrivers}) is exercised against real temp trees: non-type folders, macOS sidecars
 * and empty version folders must not break or pollute the resulting driver registry.
 */
public class DriverLoadServiceTest {

    private DriverLoadService service;
    private File mysqlDriverFile;

    @Before
    public void setUp() {
        service = new DriverLoadService();
        mysqlDriverFile = new File("/tmp/drivers/mysql/8.0.33");
        Map<String, File> versions = new HashMap<>();
        versions.put("8.0.33", mysqlDriverFile);
        service.drivers.put(ProductTypeEnum.MYSQL, versions);
    }

    @Test
    public void returnsFileWhenTypeAndVersionExist() {
        assertSame(mysqlDriverFile,
                service.getVersionDriverFile(ProductTypeEnum.MYSQL, "8.0.33"));
    }

    @Test
    public void missingTypeThrowsInsteadOfNpe() {
        try {
            service.getVersionDriverFile(ProductTypeEnum.ORACLE, "11g");
            fail("should throw for missing type");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void missingVersionThrowsInsteadOfNull() {
        try {
            service.getVersionDriverFile(ProductTypeEnum.MYSQL, "5.1.47");
            fail("should throw for missing version");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void loadDriversScansTypeFoldersWithJars() throws Exception {
        Path base = newBase();
        Path version = Files.createDirectories(base.resolve("mysql").resolve("8.0.33"));
        touch(version.resolve("mysql-connector-java-8.0.33.jar"));

        loadFrom(base);

        assertEquals(Collections.singletonList("8.0.33"),
                service.getDriverVersion(ProductTypeEnum.MYSQL));
        File loaded = service.getVersionDriverFile(ProductTypeEnum.MYSQL, "8.0.33");
        assertEquals(version.toFile().getAbsolutePath(), loaded.getAbsolutePath());
    }

    @Test
    public void loadDriversIgnoresUnknownTypeFoldersAndMacOsSidecars() throws Exception {
        Path base = newBase();
        // folder name is not a ProductTypeEnum (match happens on the enum name, case-insensitively)
        Files.createDirectories(base.resolve("not-a-db").resolve("1.0"));
        // a stray file next to the type folders (e.g. .DS_Store) must be skipped by the directory filter
        touch(base.resolve(".DS_Store"));
        // hidden version folders are filtered out by name
        Path typeDir = Files.createDirectories(base.resolve("mysql"));
        Files.createDirectories(typeDir.resolve(".hidden"));
        Path version = Files.createDirectories(typeDir.resolve("8.0.33"));
        touch(version.resolve("driver.jar"));

        loadFrom(base);

        assertEquals(Collections.singletonList("8.0.33"),
                service.getDriverVersion(ProductTypeEnum.MYSQL));
        assertTrue(service.getDriverVersionWithPath(ProductTypeEnum.POSTGRESQL).isEmpty());
    }

    @Test
    public void loadDriversMatchesTypeFolderCaseInsensitively() throws Exception {
        Path base = newBase();
        Path version = Files.createDirectories(base.resolve("MYSQL").resolve("8.0.33"));
        touch(version.resolve("driver.jar"));

        loadFrom(base);

        assertEquals(Collections.singletonList("8.0.33"),
                service.getDriverVersion(ProductTypeEnum.MYSQL));
    }

    @Test
    public void loadDriversKeepsSeveralVersionsPerType() throws Exception {
        Path base = newBase();
        Path v1 = Files.createDirectories(base.resolve("oracle").resolve("11g"));
        touch(v1.resolve("ojdbc.jar"));
        Path v2 = Files.createDirectories(base.resolve("oracle").resolve("12c"));
        touch(v2.resolve("ojdbc.jar"));

        loadFrom(base);

        Set<String> versions = new HashSet<>(service.getDriverVersion(ProductTypeEnum.ORACLE));
        assertEquals(new HashSet<>(Arrays.asList("11g", "12c")), versions);
    }

    @Test
    public void loadDriversFailsWhenBasePathMissing() throws Exception {
        setBasePath(service, Files.createTempDirectory("no-drivers").resolve("absent").toString());
        try {
            service.loadDrivers();
            fail("missing base path must fail startup");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No drivers type found"));
        }
    }

    @Test
    public void loadDriversFailsWhenTypeFolderHasNoVersionDirectory() throws Exception {
        Path base = newBase();
        // only a loose jar file, no version subdirectory
        touch(Files.createDirectories(base.resolve("mysql")).resolve("loose.jar"));

        setBasePath(service, base.toString());
        try {
            service.loadDrivers();
            fail("type without version directories must fail startup");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No driver version found"));
        }
    }

    @Test
    public void loadDriversFailsWhenVersionDirectoryEmpty() throws Exception {
        Path base = newBase();
        Files.createDirectories(base.resolve("mysql").resolve("8.0.33"));

        setBasePath(service, base.toString());
        try {
            service.loadDrivers();
            fail("empty version directory must fail startup");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No driver version jar file found"));
        }
    }

    @Test
    public void getDriverVersionForUnknownTypeIsEmpty() {
        assertTrue(service.getDriverVersion(ProductTypeEnum.ODPS).isEmpty());
    }

    @Test
    public void getDriverVersionWithPathForUnknownTypeIsEmptyMap() {
        Map<String, File> map = service.getDriverVersionWithPath(ProductTypeEnum.ODPS);
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    public void getDriversListsJarFilesAndSkipsDotFiles() throws Exception {
        Path dir = Files.createTempDirectory("driver-version");
        touch(dir.resolve("a.jar"));
        touch(dir.resolve("b.jar"));
        touch(dir.resolve(".DS_Store"));
        touch(dir.resolve("._b.jar"));

        service.drivers.put(ProductTypeEnum.ORACLE, Collections.singletonMap("11g", dir.toFile()));

        List<DatabaseTypeDriverResponse> drivers = service.getDrivers(ProductTypeEnum.ORACLE);

        assertEquals(1, drivers.size());
        DatabaseTypeDriverResponse response = drivers.get(0);
        assertEquals("11g", response.getDriverVersion());
        assertEquals(ProductTypeEnum.ORACLE.getDriver(), response.getDriverClass());
        assertEquals(dir.toFile().getAbsolutePath(), response.getDriverPath());
        // dot-prefixed sidecars stay out of the visible jar list
        assertEquals(new HashSet<>(Arrays.asList("a.jar", "b.jar")), new HashSet<>(response.getJarFiles()));
    }

    @Test
    public void getDriversForUnknownTypeIsEmptyList() {
        assertTrue(service.getDrivers(ProductTypeEnum.HIVE).isEmpty());
    }

    private static Path newBase() throws IOException {
        return Files.createTempDirectory("dp-drivers");
    }

    private static void touch(Path file) throws IOException {
        Files.write(file, new byte[]{0});
    }

    private void loadFrom(Path base) throws Exception {
        setBasePath(service, base.toString());
        service.loadDrivers();
    }

    private static void setBasePath(DriverLoadService target, String path) throws Exception {
        Field field = DriverLoadService.class.getDeclaredField("driversBasePath");
        field.setAccessible(true);
        field.set(target, path);
    }

}
