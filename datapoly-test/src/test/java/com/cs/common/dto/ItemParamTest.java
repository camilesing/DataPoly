// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.cs.common.enums.HttpMethodEnum;
import com.cs.common.enums.ParamTypeEnum;
import com.cs.common.exception.CommonException;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class ItemParamTest {

    private ItemParam param(String name, ParamTypeEnum type) {
        ItemParam param = new ItemParam();
        param.setName(name);
        param.setType(type);
        return param;
    }

    @Test
    public void testBlankNameRejected() {
        try {
            param(" ", ParamTypeEnum.STRING).checkValid(HttpMethodEnum.POST);
            fail("blank name must be rejected");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testObjectInputRequiresBodyMethod() {
        try {
            param("obj", ParamTypeEnum.OBJECT).checkValid(HttpMethodEnum.GET);
            fail("object input on a bodyless method must be rejected");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testObjectInputWithoutChildrenRejected() {
        try {
            param("obj", ParamTypeEnum.OBJECT).checkValid(HttpMethodEnum.POST);
            fail("object input without children must be rejected");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testObjectChildWithBlankNameRejected() {
        ItemParam parent = param("obj", ParamTypeEnum.OBJECT);
        parent.setChildren(Collections.singletonList(param(null, ParamTypeEnum.STRING)));
        try {
            parent.checkValid(HttpMethodEnum.POST);
            fail("child with blank name must be rejected");
        } catch (CommonException expected) {
        }
    }

    @Test
    public void testObjectInputWithChildrenPassesOnBodyMethod() {
        ItemParam parent = param("obj", ParamTypeEnum.OBJECT);
        parent.setChildren(Collections.singletonList(param("field", ParamTypeEnum.STRING)));
        parent.checkValid(HttpMethodEnum.POST);
        assertEquals(1, parent.getChildren().size());
    }

    @Test
    public void testNonObjectInputResetsChildrenToEmpty() {
        ItemParam plain = param("plain", ParamTypeEnum.STRING);
        plain.setChildren(null);
        plain.checkValid(HttpMethodEnum.GET);
        assertNotNull(plain.getChildren());
        assertTrue(plain.getChildren().isEmpty());
    }
}
