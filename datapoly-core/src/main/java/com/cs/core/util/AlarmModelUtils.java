// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.core.date.DateUtil;
import com.cs.persistence.entity.*;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;

@UtilityClass
public class AlarmModelUtils {

    private static final ThreadLocal<SimpleDateFormat> threadLocal = ThreadLocal
            .withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public static Map<String, String> getExampleModel() {
        Map<String, String> dataModel = new HashMap<>(8);
        dataModel.put("path", "/api/test/create");
        dataModel.put("method", "POST");
        dataModel.put("contentType", "application/json");
        dataModel.put("name", "test interface for create");
        dataModel.put("description", "this is description!");
        dataModel.put("open", "false");
        dataModel.put("clientKey", "test");
        dataModel.put("ipAddr", "127.0.0.1");
        dataModel.put("userAgent", "datapoly");
        dataModel.put("exception", "this is test alarm message");
        return dataModel;
    }

    public static void setBeforeTestAlarm(Map<String, String> dataModel) {
        dataModel.put("accessTime", DateUtil.now());
    }

    public static Map<String, String> getBusinessModel(
            ApiAssignmentEntity apiConfigEntity, AccessRecordEntity accessRecord, long accessTimestamp) {
        Map<String, String> dataModel = new HashMap<>(8);
        dataModel.put("path", accessRecord.getPath());
        dataModel.put("method", apiConfigEntity.getMethod().name());
        dataModel.put("contentType", apiConfigEntity.getContentType());
        dataModel.put("name", apiConfigEntity.getName());
        dataModel.put("description", StringUtils.defaultString(apiConfigEntity.getDescription()));
        dataModel.put("open", apiConfigEntity.getOpen().toString());
        dataModel.put("clientKey", StringUtils.defaultString(accessRecord.getClientKey()));
        dataModel.put("ipAddr", StringUtils.defaultString(accessRecord.getIpAddr()));
        dataModel.put("userAgent", StringUtils.defaultString(accessRecord.getUserAgent()));
        dataModel.put("exception", StringUtils.defaultString(accessRecord.getException()));
        dataModel.put("accessTime", threadLocal.get().format(new Date(accessTimestamp)));
        return dataModel;
    }

}
