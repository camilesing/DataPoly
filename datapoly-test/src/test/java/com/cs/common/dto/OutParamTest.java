// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.cs.common.enums.ParamTypeEnum;
import com.cs.common.exception.CommonException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class OutParamTest {

    @Test
    public void testBlankNameRejected() {
        OutParam param = new OutParam();
        param.setType(ParamTypeEnum.STRING);
        try {
            param.checkValid();
            fail("blank name must be rejected");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testNullTypeRejected() {
        OutParam param = new OutParam();
        param.setName("name-only");
        try {
            param.checkValid();
            fail("null type must be rejected");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testObjectWithoutChildrenRejected() {
        OutParam param = new OutParam();
        param.setName("obj");
        param.setType(ParamTypeEnum.OBJECT);
        try {
            param.checkValid();
            fail("object output without children must be rejected");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testObjectChildWithBlankNameRejected() {
        OutParam child = new OutParam();
        child.setType(ParamTypeEnum.STRING);
        OutParam param = new OutParam();
        param.setName("obj");
        param.setType(ParamTypeEnum.OBJECT);
        param.setChildren(Arrays.asList(child));
        try {
            param.checkValid();
            fail("object child with blank name must be rejected");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testObjectWithValidChildrenPasses() {
        OutParam child = new OutParam();
        child.setName("field");
        child.setType(ParamTypeEnum.STRING);
        OutParam param = new OutParam();
        param.setName("obj");
        param.setType(ParamTypeEnum.OBJECT);
        param.setChildren(Collections.singletonList(child));
        param.checkValid();
        assertEquals(1, param.getChildren().size());
    }

    @Test
    public void testNonObjectResetsChildrenToEmpty() {
        OutParam param = new OutParam();
        param.setName("plain");
        param.setType(ParamTypeEnum.LONG);
        param.setChildren(null);
        param.checkValid();
        assertNotNull(param.getChildren());
        assertTrue(param.getChildren().isEmpty());
    }
}
