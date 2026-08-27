// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.dto.BaseParam;
import com.cs.common.dto.ItemParam;
import com.cs.common.enums.ParamTypeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class DataTaskParamBinderTest {

    private ItemParam simple(String name, ParamTypeEnum type, boolean isArray, boolean required, String defaultValue) {
        ItemParam param = new ItemParam();
        param.setName(name);
        param.setType(type);
        param.setIsArray(isArray);
        param.setRequired(required);
        param.setDefaultValue(defaultValue);
        if (ParamTypeEnum.OBJECT == type) {
            BaseParam child = new BaseParam();
            child.setName("sub");
            child.setType(ParamTypeEnum.LONG);
            child.setIsArray(false);
            child.setRequired(false);
            param.setChildren(new ArrayList<>(Collections.singletonList(child)));
        } else {
            param.setChildren(Collections.emptyList());
        }
        return param;
    }

    @Test
    public void bindsDeclaredScalarsAndIgnoresUnknownKeys() {
        Map<String, Object> body = new HashMap<>();
        body.put("limit", "7");
        body.put("extra", true);

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("limit", ParamTypeEnum.LONG, false, true, null)), body);

        Assert.assertEquals(Collections.singletonMap("limit", 7L), bound);
    }

    @Test
    public void missingRequiredThrowsCommonException() {
        try {
            DataTaskParamBinder.bind(
                    Collections.singletonList(simple("name", ParamTypeEnum.STRING, false, true, null)),
                    Collections.emptyMap());
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void defaultValueAppliesWhenAbsent() {
        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("offset", ParamTypeEnum.LONG, false, false, "3")),
                Collections.emptyMap());
        Assert.assertEquals(3L, bound.get("offset"));
    }

    @Test
    public void nativeJsonTypesAreStringifiedThenConverted() {
        Map<String, Object> body = new HashMap<>();
        body.put("flag", Boolean.TRUE);

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("flag", ParamTypeEnum.BOOLEAN, false, false, null)), body);
        Assert.assertEquals(Boolean.TRUE, bound.get("flag"));
    }

    @Test
    public void scalarArraysConvertElementwise() {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", Arrays.asList("1", "2"));

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("ids", ParamTypeEnum.LONG, true, false, null)), body);
        Assert.assertEquals(Arrays.asList(1L, 2L), bound.get("ids"));
    }

    @Test
    public void objectParamsAcceptNestedAndDottedForms() {
        ItemParam decl = simple("obj", ParamTypeEnum.OBJECT, false, false, null);

        Map<String, Object> nested = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("sub", "9");
        nested.put("obj", inner);
        Assert.assertEquals(Collections.singletonMap("sub", 9L),
                DataTaskParamBinder.bind(Collections.singletonList(decl), nested).get("obj"));

        Map<String, Object> dotted = new HashMap<>();
        dotted.put("obj.sub", "9");
        Assert.assertEquals(Collections.singletonMap("sub", 9L),
                DataTaskParamBinder.bind(Collections.singletonList(decl), dotted).get("obj"));
    }

    @Test
    public void objectWithArrayFlagIsRejected() {
        ItemParam decl = simple("obj", ParamTypeEnum.OBJECT, false, false, null);
        decl.setIsArray(true);

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("sub", "9");
        body.put("obj", inner);

        try {
            DataTaskParamBinder.bind(Collections.singletonList(decl), body);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }
}
