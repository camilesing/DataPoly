// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.enums.HttpMethodEnum;
import com.cs.persistence.entity.AccessRecordEntity;
import com.cs.persistence.entity.ApiAssignmentEntity;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.*;

public class AlarmModelUtilsTest {

    @Test
    public void testExampleModelCoversAllFields() {
        Map<String, String> model = AlarmModelUtils.getExampleModel();
        assertEquals("/api/test/create", model.get("path"));
        assertEquals("POST", model.get("method"));
        assertEquals("application/json", model.get("contentType"));
        assertEquals("127.0.0.1", model.get("ipAddr"));
        assertTrue(model.containsKey("exception"));
    }

    @Test
    public void testBeforeTestAlarmAddsAccessTime() {
        Map<String, String> model = AlarmModelUtils.getExampleModel();
        AlarmModelUtils.setBeforeTestAlarm(model);
        assertNotNull(model.get("accessTime"));
        assertTrue(model.get("accessTime").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    public void testBusinessModelMapsEntityAndRecord() {
        ApiAssignmentEntity api = new ApiAssignmentEntity();
        api.setMethod(HttpMethodEnum.POST);
        api.setName("create-user");
        api.setContentType("application/json");
        api.setDescription("desc");
        api.setOpen(Boolean.TRUE);

        AccessRecordEntity record = new AccessRecordEntity();
        record.setPath("/api/user/create");
        record.setClientKey("client-1");
        record.setIpAddr("10.0.0.9");
        record.setUserAgent("ua");
        record.setException("boom");

        long accessTimestamp = 1760000000000L;
        Map<String, String> model = AlarmModelUtils.getBusinessModel(api, record, accessTimestamp);
        assertEquals("POST", model.get("method"));
        assertEquals("create-user", model.get("name"));
        assertEquals("true", model.get("open"));
        assertEquals("client-1", model.get("clientKey"));
        assertEquals("boom", model.get("exception"));
        assertEquals(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(accessTimestamp)),
                model.get("accessTime"));
    }

    @Test
    public void testBusinessModelNullablesBecomeEmptyStrings() {
        ApiAssignmentEntity api = new ApiAssignmentEntity();
        api.setMethod(HttpMethodEnum.GET);
        api.setOpen(Boolean.FALSE);
        AccessRecordEntity record = new AccessRecordEntity();
        Map<String, String> model = AlarmModelUtils.getBusinessModel(api, record, 0L);
        assertEquals("", model.get("description"));
        assertEquals("", model.get("clientKey"));
        assertEquals("", model.get("ipAddr"));
        assertEquals("", model.get("userAgent"));
        assertEquals("", model.get("exception"));
        assertEquals("false", model.get("open"));
    }
}
