// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.executor;

import cn.hutool.json.JSONUtil;
import com.cs.common.enums.OnOffEnum;
import com.cs.core.dto.*;
import com.cs.core.util.AlarmModelUtils;
import com.cs.persistence.dao.UnifyAlarmDao;
import com.cs.persistence.entity.UnifyAlarmEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.text.StrSubstitutor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UnifyAlarmOpsService {

    @Resource
    private UnifyAlarmDao unifyAlarmDao;
    private final RestTemplate restTemplate;

    public UnifyAlarmOpsService() {
        this.restTemplate = new RestTemplate(new AlarmHttpRequestFactory());
    }

    public UnifyAlarmEntity getUnifyAlarmConfig() {
        return unifyAlarmDao.getUnifyAlarmConfig();
    }

    public List<NameValueBaseResponse> getExampleDataModel() {
        Map<String, String> dataModel = AlarmModelUtils.getExampleModel();
        List<NameValueBaseResponse> lists = new ArrayList<>();
        dataModel.forEach((k, v) -> lists.add(NameValueBaseResponse.builder().key(k).value(v).build()));
        return lists;
    }

    public void updateUnifyAlarmConfig(UpdateAlarmConfigRequest request) {
        unifyAlarmDao.update(
                request.getStatus(),
                request.getEndpoint(),
                request.getContentType(),
                request.getInputTemplate());
    }

    public void testUnifyAlarmConfig(TestAlarmConfigRequest request) {
        UnifyAlarmEntity config = UnifyAlarmEntity.builder()
                .status(OnOffEnum.ON)
                .endpoint(request.getEndpoint())
                .contentType(request.getContentType())
                .inputTemplate(request.getInputTemplate())
                .build();
        Map<String, String> dataModel = Optional.ofNullable(request.getDataModel())
                .orElseGet(ArrayList::new).stream()
                .collect(
                        Collectors.toMap(
                                NameValueBaseResponse::getKey,
                                NameValueBaseResponse::getValue,
                                (a, b) -> b));
        AlarmModelUtils.setBeforeTestAlarm(dataModel);
        handleAlarm(config, dataModel);
    }

    public void triggerAlarm(Map<String, String> dataModel) {
        UnifyAlarmEntity config = unifyAlarmDao.getUnifyAlarmConfig();
        if (OnOffEnum.OFF == config.getStatus()) {
            return;
        }

        try {
            handleAlarm(config, dataModel);
        } catch (Exception e) {
            log.error("Send alarm message failed,{}", e.getMessage(), e);
        }
    }

    private ResponseEntity<String> handleAlarm(UnifyAlarmEntity config, Map<String, String> dataModel) {
        StrSubstitutor strSubstitutor = new StrSubstitutor(escape(dataModel));
        String bodyStr = strSubstitutor.replace(config.getInputTemplate());
        if (log.isDebugEnabled()) {
            log.debug("Send alarm message body:\n{}", bodyStr);
        }
        ResponseEntity<String> ret = sentAlarm(config, bodyStr);
        if (ret.getStatusCodeValue() != HttpStatus.OK.value()) {
            log.warn("Error when send alarm message http status: {} ,body: {}", ret.getStatusCode(), ret.getBody());
        } else {
            log.info("Send alarm message http status: {} ,body: {}", ret.getStatusCode(), ret.getBody());
        }
        return ret;
    }

    private ResponseEntity<String> sentAlarm(UnifyAlarmEntity config, String bodyStr) {
        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.parseMediaType(config.getContentType().replace(";", "") + "; charset=UTF-8");
        headers.setContentType(type);
        headers.add("Accept", MediaType.ALL_VALUE);
        HttpEntity<String> httpEntity = new HttpEntity<>(bodyStr, headers);
        return restTemplate.exchange(config.getEndpoint(), HttpMethod.POST, httpEntity, String.class);
    }

    private Map<String, String> escape(Map<String, String> dataModel) {
        Map<String, String> newDataModel = new HashMap<>(dataModel.size());
        for (Map.Entry<String, String> entry : dataModel.entrySet()) {
            newDataModel.put(entry.getKey(), JSONUtil.escape(entry.getValue()));
        }
        return newDataModel;
    }
}
