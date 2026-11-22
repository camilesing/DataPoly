// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.NameValueBaseResponse;
import com.cs.core.dto.TestAlarmConfigRequest;
import com.cs.core.dto.UpdateAlarmConfigRequest;
import com.cs.core.executor.UnifyAlarmOpsService;
import com.cs.persistence.entity.UnifyAlarmEntity;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Alarm config endpoints are thin delegations over {@link UnifyAlarmOpsService}: reads wrap
 * the service value, writes return plain success once the service call went through.
 */
public class UnifyAlarmControllerTest {

    private StubUnifyAlarmOpsService service;
    private UnifyAlarmController controller;

    @Before
    public void setUp() {
        service = new StubUnifyAlarmOpsService();
        controller = new UnifyAlarmController();
        DataTaskTestSupport.setField(controller, "unifyAlarmOpsService", service);
    }

    @Test
    public void detailReturnsStoredConfig() {
        UnifyAlarmEntity entity = new UnifyAlarmEntity();
        service.configResult = entity;
        ResultEntity<UnifyAlarmEntity> result = controller.getUnifyAlarmConfig();
        assertSame(entity, result.getData());
    }

    @Test
    public void exampleDataModelReturnsServiceList() {
        NameValueBaseResponse row = NameValueBaseResponse.builder().key("path").value("/api/x").build();
        service.exampleResult = Collections.singletonList(row);
        ResultEntity<List<NameValueBaseResponse>> result = controller.getExampleDataModel();
        assertSame(row, result.getData().get(0));
    }

    @Test
    public void testConfigDelegatesRequest() {
        TestAlarmConfigRequest request = new TestAlarmConfigRequest();
        ResultEntity result = controller.testUnifyAlarmConfig(request);
        assertSame(request, service.testRequest);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void saveConfigDelegatesRequest() {
        UpdateAlarmConfigRequest request = new UpdateAlarmConfigRequest();
        ResultEntity result = controller.saveUnifyAlarmConfig(request);
        assertSame(request, service.updateRequest);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    private static class StubUnifyAlarmOpsService extends UnifyAlarmOpsService {
        UnifyAlarmEntity configResult;
        List<NameValueBaseResponse> exampleResult = Collections.emptyList();

        TestAlarmConfigRequest testRequest;
        UpdateAlarmConfigRequest updateRequest;

        @Override
        public UnifyAlarmEntity getUnifyAlarmConfig() {
            return configResult;
        }

        @Override
        public List<NameValueBaseResponse> getExampleDataModel() {
            return exampleResult;
        }

        @Override
        public void testUnifyAlarmConfig(TestAlarmConfigRequest request) {
            testRequest = request;
        }

        @Override
        public void updateUnifyAlarmConfig(UpdateAlarmConfigRequest request) {
            updateRequest = request;
        }
    }
}
