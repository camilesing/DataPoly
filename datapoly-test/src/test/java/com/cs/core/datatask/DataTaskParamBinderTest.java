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

    // --------------------------------------------------- array edge branches

    @Test
    public void optionalArrayAbsentIsOmitted() {
        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("ids", ParamTypeEnum.LONG, true, false, null)),
                Collections.emptyMap());
        Assert.assertTrue(bound.isEmpty());
    }

    @Test
    public void requiredArrayAbsentReportsMissing() {
        try {
            DataTaskParamBinder.bind(
                    Collections.singletonList(simple("ids", ParamTypeEnum.LONG, true, true, null)),
                    Collections.emptyMap());
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void requiredArraySubmittedEmptyReportsMissing() {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", new ArrayList<Object>());

        try {
            DataTaskParamBinder.bind(
                    Collections.singletonList(simple("ids", ParamTypeEnum.LONG, true, true, null)), body);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void optionalArraySubmittedEmptyIsOmittedNotMissing() {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", new ArrayList<Object>());

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("ids", ParamTypeEnum.LONG, true, false, null)), body);
        Assert.assertTrue(bound.isEmpty());
    }

    @Test
    public void arrayDefaultFallsBackWhenNothingUsableSubmitted() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", Integer.valueOf(42)); // neither List nor String: nothing usable

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, "a,b")), body);
        // the raw wire value has no array shape, so the declared default is coerced instead
        Assert.assertEquals("a,b", bound.get("names"));
    }

    @Test
    public void arrayDefaultMissingLeavesParameterOut() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", Integer.valueOf(42));

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        Assert.assertTrue(bound.isEmpty());
    }

    @Test
    public void bracketedNonJsonStringFallsBackToCommaSplitting() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "[a,b"); // starts with '[' but is no JSON array and does not end with ']'

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        Assert.assertEquals(Arrays.asList("[a", "b"), bound.get("names"));
    }

    @Test
    public void malformedJsonArrayFallsBackToCommaSplitting() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "[x,y]"); // bracketed, so JSON is attempted, but it does not parse

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        // comma splitting runs on the raw bracketed text, so the brackets stay as data
        Assert.assertEquals(Arrays.asList("[x", "y]"), bound.get("names"));
    }

    @Test
    public void commaSeparatedElementsKeepQuotesOnlyWhenWrapping() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "\"a\" , b\"c");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        // fully wrapped quotes are stripped; a half-quoted token is data
        Assert.assertEquals(Arrays.asList("a", "b\"c"), bound.get("names"));
    }

    @Test
    public void blankStringElementsAreDroppedFromCommaList() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "a , , b");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        Assert.assertEquals(Arrays.asList("a", "b"), bound.get("names"));
    }

    @Test
    public void requiredBadArrayElementFailsElementwise() {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", Arrays.asList("1", "abc"));

        try {
            DataTaskParamBinder.bind(
                    Collections.singletonList(simple("ids", ParamTypeEnum.LONG, true, true, null)), body);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    // --------------------------------------------------- scalar edge branches

    @Test
    public void optionalBadScalarCoercesToNullAndIsOmitted() {
        Map<String, Object> body = new HashMap<>();
        body.put("offset", "abc");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("offset", ParamTypeEnum.LONG, false, false, null)), body);
        Assert.assertTrue(bound.isEmpty());
    }

    @Test
    public void requiredBadScalarFails() {
        Map<String, Object> body = new HashMap<>();
        body.put("offset", "abc");

        try {
            DataTaskParamBinder.bind(
                    Collections.singletonList(simple("offset", ParamTypeEnum.LONG, false, true, null)), body);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void nullDeclaredTypeIsAnInternalError() {
        ItemParam decl = new ItemParam();
        decl.setName("weird");
        decl.setType(null);
        decl.setIsArray(false);
        decl.setRequired(false);
        decl.setChildren(Collections.emptyList());

        Map<String, Object> body = new HashMap<>();
        body.put("weird", "value");

        try {
            DataTaskParamBinder.bind(Collections.singletonList(decl), body);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INTERNAL_ERROR, e.getCode());
        }
    }

    @Test
    public void defaultValueUsedWhenRawValueBlank() {
        Map<String, Object> body = new HashMap<>();
        body.put("offset", "   ");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("offset", ParamTypeEnum.LONG, false, false, "5")), body);
        Assert.assertEquals(5L, bound.get("offset"));
    }

    // --------------------------------------------------- OBJECT edge branches

    @Test
    public void optionalObjectWithNothingSubmittedIsOmitted() {
        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("obj", ParamTypeEnum.OBJECT, false, false, null)),
                Collections.emptyMap());
        Assert.assertTrue(bound.isEmpty());
    }

    @Test
    public void requiredObjectWithNothingSubmittedReportsMissing() {
        try {
            DataTaskParamBinder.bind(
                    Collections.singletonList(simple("obj", ParamTypeEnum.OBJECT, false, true, null)),
                    Collections.emptyMap());
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void objectWithoutChildrenIsRejected() {
        ItemParam decl = new ItemParam();
        decl.setName("obj");
        decl.setType(ParamTypeEnum.OBJECT);
        decl.setIsArray(false);
        decl.setRequired(false);

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

    @Test
    public void requiredObjectChildArrayMissingReportsChildPath() {
        ItemParam decl = new ItemParam();
        decl.setName("obj");
        decl.setType(ParamTypeEnum.OBJECT);
        decl.setIsArray(false);
        decl.setRequired(false);
        BaseParam child = new BaseParam();
        child.setName("tags");
        child.setType(ParamTypeEnum.STRING);
        child.setIsArray(true);
        child.setRequired(true);
        decl.setChildren(new ArrayList<>(Collections.singletonList(child)));

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("other", "x"); // sibling keeps the container non-empty while tags is absent
        body.put("obj", inner);

        try {
            DataTaskParamBinder.bind(Collections.singletonList(decl), body);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void optionalObjectChildArrayMissingStaysOut() {
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
        inner.put("other", "x"); // keeps the container non-empty while tags is absent
        body.put("obj", inner);

        Map<String, Object> bound = DataTaskParamBinder.bind(Collections.singletonList(decl), body);
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) bound.get("obj");
        Assert.assertTrue("unknown child keys are ignored, tags stays out", obj.isEmpty());
    }

    @Test
    public void objectChildScalarDefaultsApply() {
        ItemParam decl = new ItemParam();
        decl.setName("obj");
        decl.setType(ParamTypeEnum.OBJECT);
        decl.setIsArray(false);
        decl.setRequired(false);
        BaseParam child = new BaseParam();
        child.setName("sub");
        child.setType(ParamTypeEnum.LONG);
        child.setIsArray(false);
        child.setRequired(false);
        child.setDefaultValue("3");
        decl.setChildren(new ArrayList<>(Collections.singletonList(child)));

        Map<String, Object> bound = DataTaskParamBinder.bind(Collections.singletonList(decl),
                Collections.emptyMap());
        // empty container short-circuits before children are walked; submit a sibling key to enter
        Assert.assertTrue(bound.isEmpty());

        Map<String, Object> body = new HashMap<>();
        body.put("obj.sibling", "keep"); // unknown dotted child keeps container non-empty
        bound = DataTaskParamBinder.bind(Collections.singletonList(decl), body);
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) bound.get("obj");
        Assert.assertEquals(3L, obj.get("sub"));
    }

    @Test
    public void nestedFormWinsOverDottedFormOnConflict() {
        ItemParam decl = simple("obj", ParamTypeEnum.OBJECT, false, false, null);

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("sub", "1");
        body.put("obj", inner);
        body.put("obj.sub", "2");

        Assert.assertEquals(Collections.singletonMap("sub", 1L),
                DataTaskParamBinder.bind(Collections.singletonList(decl), body).get("obj"));
    }

    // --------------------------------------------------- binder edge branches

    @Test
    public void nullDeclarationsAndNullSubmittedBothBindToEmpty() {
        Assert.assertTrue(DataTaskParamBinder.bind(null, null).isEmpty());
        Assert.assertTrue(DataTaskParamBinder.bind(null, new HashMap<>()).isEmpty());
        // an explicitly empty declaration list behaves exactly like a null one
        Assert.assertTrue(DataTaskParamBinder.bind(new ArrayList<ItemParam>(), new HashMap<>()).isEmpty());

        // null submitted body behaves like an empty one: defaults still apply
        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("offset", ParamTypeEnum.LONG, false, false, "5")), null);
        Assert.assertEquals(Collections.singletonMap("offset", 5L), bound);
    }

    @Test
    public void blankStringArrayValueFallsBackToTheDeclaredDefault() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "   "); // a String too blank to carry elements

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, "a,b")), body);
        Assert.assertEquals("a,b", bound.get("names"));
    }

    @Test
    public void leadingQuoteWithoutTrailingQuoteIsData() {
        Map<String, Object> body = new HashMap<>();
        body.put("names", "\"bc , x");

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(simple("names", ParamTypeEnum.STRING, true, false, null)), body);
        // the opening quote is only stripped when the item is fully wrapped
        Assert.assertEquals(Arrays.asList("\"bc", "x"), bound.get("names"));
    }

    @Test
    public void optionalObjectArrayWithNothingSubmittedBindsAnEmptyList() {
        ItemParam decl = simple("objs", ParamTypeEnum.OBJECT, false, false, null);
        decl.setIsArray(true); // declared array-of-OBJECT with no submitted container

        Map<String, Object> bound = DataTaskParamBinder.bind(
                Collections.singletonList(decl), Collections.emptyMap());
        // the unsupported-combination guard only fires for non-empty containers;
        // an optional empty one binds an empty list instead of being omitted
        Assert.assertEquals(Collections.singletonList(Collections.emptyList()),
                new ArrayList<>(bound.values()));
    }

    @Test
    public void emptyChildrenListIsRejectedLikeNullChildren() {
        ItemParam decl = new ItemParam();
        decl.setName("obj");
        decl.setType(ParamTypeEnum.OBJECT);
        decl.setIsArray(false);
        decl.setRequired(false);
        decl.setChildren(new ArrayList<BaseParam>()); // explicitly empty, not null

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

    @Test
    public void optionalChildArraySubmittedAsBareCommaStaysOut() {
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
        inner.put("tags", ","); // splits into two blank items, both dropped: an empty element list
        inner.put("other", "x"); // keeps the container non-empty
        body.put("obj", inner);

        Map<String, Object> bound = DataTaskParamBinder.bind(Collections.singletonList(decl), body);
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) bound.get("obj");
        Assert.assertTrue("an empty submitted child array stays out entirely", obj.isEmpty());
    }

    @Test
    public void optionalScalarChildWithoutValueOrDefaultIsOmitted() {
        ItemParam decl = new ItemParam();
        decl.setName("obj");
        decl.setType(ParamTypeEnum.OBJECT);
        decl.setIsArray(false);
        decl.setRequired(false);
        BaseParam child = new BaseParam();
        child.setName("sub");
        child.setType(ParamTypeEnum.LONG);
        child.setIsArray(false);
        child.setRequired(false); // no default either
        decl.setChildren(new ArrayList<>(Collections.singletonList(child)));

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> inner = new HashMap<>();
        inner.put("sibling", "x"); // container non-empty through an unknown key
        body.put("obj", inner);

        Map<String, Object> bound = DataTaskParamBinder.bind(Collections.singletonList(decl), body);
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) bound.get("obj");
        Assert.assertTrue("optional valueless child must not appear", obj.isEmpty());
    }
}
