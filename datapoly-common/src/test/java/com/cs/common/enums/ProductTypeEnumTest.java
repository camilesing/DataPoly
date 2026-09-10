// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import com.cs.common.util.JdbcUrlUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

public class ProductTypeEnumTest {

    @Test
    public void testEnumUiNameInvariant() {
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            assertEquals("name must uppercase to its enum constant", type.name(), type.getName().toUpperCase());
        }
    }

    @Test
    public void testEnumIdsAreUnique() {
        Set<Integer> ids = new HashSet<>();
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            assertTrue("duplicated id " + type.getId() + " for " + type.name(), ids.add(type.getId()));
        }
    }

    @Test
    public void testOdpsLookup() {
        assertTrue(ProductTypeEnum.exists("odps"));
        assertTrue(ProductTypeEnum.exists("ODPS"));
        assertEquals(ProductTypeEnum.ODPS, ProductTypeEnum.of("odps"));
        assertEquals("com.aliyun.odps.jdbc.OdpsDriver", ProductTypeEnum.ODPS.getDriver());
    }

    @Test
    public void testOdpsContextSwitches() {
        assertFalse(ProductTypeEnum.ODPS.hasDatabaseName());
        assertFalse(ProductTypeEnum.ODPS.hasFilePath());
        assertFalse(ProductTypeEnum.ODPS.isNoViewTables());
        assertTrue(ProductTypeEnum.ODPS.offTransactional());
        assertNull(ProductTypeEnum.ODPS.getSql());
        assertNotNull(ProductTypeEnum.ODPS.getPageConsumer());
    }

    @Test
    public void testOdpsUrlPatternMatchesOfficialSample() {
        String httpUrl = "jdbc:odps:http://service.cn-hangzhou.maxcompute.aliyun.com/api?project=test_project&useProjectTimeZone=true";
        String httpsUrl = "jdbc:odps:https://service.cn-hangzhou.maxcompute.aliyun.com/api?project=test_project";
        assertTrue(matchesAny(ProductTypeEnum.ODPS.getUrl(), httpUrl));
        assertTrue(matchesAny(ProductTypeEnum.ODPS.getUrl(), httpsUrl));
        assertFalse(matchesAny(ProductTypeEnum.ODPS.getUrl(), "jdbc:odps:http://bad"));
        assertFalse(matchesAny(ProductTypeEnum.ODPS.getUrl(),
                "jdbc:odps:http://service.cn-hangzhou.maxcompute.aliyun.com/other?project=test_project"));
        assertTrue(httpUrl.startsWith(ProductTypeEnum.ODPS.getUrlPrefix()));

        Matcher matcher = JdbcUrlUtils.getPattern(ProductTypeEnum.ODPS.getUrl()[0]).matcher(httpUrl);
        assertTrue(matcher.matches());
        assertEquals("service.cn-hangzhou.maxcompute.aliyun.com", matcher.group("host"));
        assertEquals("project=test_project&useProjectTimeZone=true", matcher.group("params"));
    }

    private boolean matchesAny(String[] templates, String url) {
        return Arrays.stream(templates)
                .map(JdbcUrlUtils::getPattern)
                .map(pattern -> pattern.matcher(url))
                .anyMatch(Matcher::matches);
    }
}