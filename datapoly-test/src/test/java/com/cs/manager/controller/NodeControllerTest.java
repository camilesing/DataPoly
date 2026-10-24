// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.service.SystemParamService;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Partial-update semantics of {@code POST /node/url-config}: an omitted parameter must
 * leave that setting untouched (a request carrying only one of the two used to fail as a
 * missing-parameter internal error), an empty string clears it, and a rejected value
 * must not write anything.
 */
public class NodeControllerTest {

    private RecordingSystemParamService systemParamService;
    private NodeController controller;

    @Before
    public void setUp() throws Exception {
        systemParamService = new RecordingSystemParamService();
        controller = new NodeController();
        Field field = NodeController.class.getDeclaredField("systemParamService");
        field.setAccessible(true);
        field.set(controller, systemParamService);
    }

    @Test
    public void onlyManagerUrlLeavesGatewayUntouched() {
        ResultEntity result = controller.updateUrlConfig(null, "https://www.test.com");

        Assert.assertEquals(Integer.valueOf(0), result.getCode());
        Assert.assertEquals(1, systemParamService.updates.size());
        Assert.assertEquals(Constants.SYS_PARAM_KEY_MANAGER_URL + "=https://www.test.com",
                systemParamService.updates.get(0));
    }

    @Test
    public void onlyGatewayUrlLeavesManagerUntouched() {
        controller.updateUrlConfig("http://gate.example.com:8091", null);

        Assert.assertEquals(1, systemParamService.updates.size());
        Assert.assertEquals(Constants.SYS_PARAM_KEY_GATEWAY_URL + "=http://gate.example.com:8091",
                systemParamService.updates.get(0));
    }

    @Test
    public void bothValuesAreNormalizedBeforeWritten() {
        controller.updateUrlConfig(" http://gate.example.com:8091/ ", "https://mgr.example.com/");

        Assert.assertEquals(2, systemParamService.updates.size());
        Assert.assertEquals(Constants.SYS_PARAM_KEY_GATEWAY_URL + "=http://gate.example.com:8091",
                systemParamService.updates.get(0));
        Assert.assertEquals(Constants.SYS_PARAM_KEY_MANAGER_URL + "=https://mgr.example.com",
                systemParamService.updates.get(1));
    }

    @Test
    public void emptyValuesClearBothSettings() {
        controller.updateUrlConfig("", "");

        Assert.assertEquals(2, systemParamService.updates.size());
        Assert.assertEquals(Constants.SYS_PARAM_KEY_GATEWAY_URL + "=", systemParamService.updates.get(0));
        Assert.assertEquals(Constants.SYS_PARAM_KEY_MANAGER_URL + "=", systemParamService.updates.get(1));
    }

    @Test
    public void neitherParameterWritesNothing() {
        ResultEntity result = controller.updateUrlConfig(null, null);

        Assert.assertEquals(Integer.valueOf(0), result.getCode());
        Assert.assertTrue(systemParamService.updates.isEmpty());
    }

    @Test
    public void rejectedValueWritesNothing() {
        try {
            controller.updateUrlConfig("ftp://gate:8091", "https://mgr.example.com");
            Assert.fail("expected node.url.invalid");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        Assert.assertTrue(systemParamService.updates.isEmpty());
    }

    @Test
    public void validValueIsNotWrittenWhenTheOtherOneIsRejected() {
        try {
            controller.updateUrlConfig("http://gate.example.com:8091", "not-a-url");
            Assert.fail("expected node.url.invalid");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        Assert.assertTrue(systemParamService.updates.isEmpty());
    }

    private static final class RecordingSystemParamService extends SystemParamService {
        private final List<String> updates = new ArrayList<>();

        @Override
        public void updateByParamKey(String key, String value) {
            updates.add(key + "=" + value);
        }
    }
}