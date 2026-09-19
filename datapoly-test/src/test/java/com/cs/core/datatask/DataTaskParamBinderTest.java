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
    public void requiredScalarWithBlankValueIsStillMissing() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "  ");

        try {
            DataTaskParamBinder.bind(
                    Collections.singletonList(simple("name", ParamTypeEnum.STRING, false, true, null)), body);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void optionalScalarWithBlankValueIsOmittedNotMissing() {
        Map<String, Object> body = new HashMap<>();
        body.put("arrivalAtStart", "");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("arrivalAtStart", ParamTypeEnum.STRING, false, false, null)), body);

        Assert.assertTrue("optional blank scalar must not be reported missing", bound.isEmpty());
    }

    @Test
    public void optionalScalarAbsentIsOmitted() {
        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("arrivalAtStart", ParamTypeEnum.STRING, false, false, null)),
                Collections.emptyMap());

        Assert.assertTrue(bound.isEmpty());
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
    public void arrayDeclaredParamAcceptsJsonArrayString() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "[\"燕文\",\"顺友\"]");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        Assert.assertEquals(Arrays.asList("燕文", "顺友"), bound.get("names"));
    }

    @Test
    public void rawWireJsonWithStringifiedArrayBindsToList() {
        String wire = "{\"params\":{\"logisticsProviderNameList\":\"[\\\"燕文\\\",\\\"顺友\\\"]\"}}";
        Map<String, Object> body = com.cs.persistence.util.JsonUtils.toBeanType(wire,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("logisticsProviderNameList", ParamTypeEnum.STRING, true, false, null)),
                params);
        Assert.assertEquals(Arrays.asList("燕文", "顺友"), bound.get("logisticsProviderNameList"));
    }

    @Test
    public void arrayDeclaredParamAcceptsCommaSeparatedString() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", " 燕文 , 顺友 ");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        Assert.assertEquals(Arrays.asList("燕文", "顺友"), bound.get("names"));
    }

    @Test
    public void arrayDeclaredParamAcceptsSingleScalarValue() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "燕文");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        Assert.assertEquals(Collections.singletonList("燕文"), bound.get("names"));
    }

    @Test
    public void jsonArrayStringElementsKeepEmbeddedCommas() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "[\"a,b\",\"c\"]");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        Assert.assertEquals(Arrays.asList("a,b", "c"), bound.get("names"));
    }

    @Test
    public void arrayStringElementsCoerceToDeclaredType() {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", "[1,2,3]");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("ids", ParamTypeEnum.LONG, true, false, null)), body);
        Assert.assertEquals(Arrays.asList(1L, 2L, 3L), bound.get("ids"));
    }

    @Test
    public void objectChildArrayAcceptsJsonArrayString() {
        ItemParam decl = new ItemParam();
        decl.setName("obj");
        decl.setType(ParamTypeEnum.OBJECT);
        decl.setIsArray(false);
        decl.setRequired(false);
        BaseParam child = new BaseParam();
        child.setName("tags");
        child.setType(ParamTypeEnum.STRING);
        child.setIsArray(true);
        child.setRequired(false);
        decl.setChildren(new ArrayList<>(Collections.singletonList(child)));

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("tags", "[\"a\",\"b\"]");
        body.put("obj", inner);

        Map<String, Object> bound = DataTaskParamBinder.bind(Collections.singletonList(decl), body);
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) bound.get("obj");
        Assert.assertEquals(Arrays.asList("a", "b"), obj.get("tags"));
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
