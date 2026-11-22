// Use of this source code is governed by a BSD-style license
package com.cs.manager.exception;

import com.cs.common.dto.ResultEntity;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.template.XmlSqlTemplate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.apache.ibatis.builder.BuilderException;
import org.junit.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * How an unparseable SQL template reaches its caller. Both editors' 入参解析/调试 buttons and the save
 * path of both features parse through {@code XmlSqlTemplate}, and a failure fell through to the generic
 * internal error — a 500 whose body said nothing about what to fix. It is caller input, so it answers as
 * an invalid argument, and the reason (which names the offending element for a dynamic tag) is in the body.
 */
public class ExceptionControllerTest {

    private ExceptionController controller;

    @Before
    public void setUp() throws Exception {
        controller = new ExceptionController();
        Field field = ExceptionController.class.getDeclaredField("httpStatusMappingEnabled");
        field.setAccessible(true);
        field.set(controller, true);
    }

    @Test
    public void unparseableTemplateIsAnInvalidArgumentNamingTheOffendingElement() {
        ResponseEntity<ResultEntity> response =
                controller.sqlTemplateParseException(parseFailure("select 1 <if test='a != null'>x</iff>"));

        Assert.assertEquals(HttpStatus.valueOf(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getHttpStatus()),
                response.getStatusCode());
        ResultEntity body = response.getBody();
        Assert.assertNotNull(body);
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getCode()), body.getCode());
        Assert.assertTrue(describe(body), body.getMessage().contains("</iff>"));
    }

    @Test
    public void aWronglyCasedTagIsReportedWithItsFix() {
        ResponseEntity<ResultEntity> response =
                controller.sqlTemplateParseException(parseFailure("select 1 <IF test='a != null'>x</IF>"));

        ResultEntity body = response.getBody();
        Assert.assertNotNull(body);
        Assert.assertTrue(describe(body), body.getMessage().contains("<IF>"));
        Assert.assertTrue(describe(body), body.getMessage().contains("<if>"));
    }

    @Test
    public void plainSqlParsesWithoutReachingTheHandler() {
        // the counterpart of the cases above: operators and '&' are ordinary SQL text
        XmlSqlTemplate template = new XmlSqlTemplate(
                "select id from t_order where dt >= #{dt} and amount <= #{amount} and name like 'A&B%'");

        Assert.assertTrue(template.getParameterNames().containsKey("dt"));
        Assert.assertTrue(template.getParameterNames().containsKey("amount"));
    }

    @Test
    public void unexpectedFailuresStillAnswerAsInternalErrors() {
        ResponseEntity<ResultEntity> response = controller.errorHandler(new IllegalStateException("boom"));

        Assert.assertEquals(HttpStatus.valueOf(ResponseErrorCode.ERROR_INTERNAL_ERROR.getHttpStatus()),
                response.getStatusCode());
        ResultEntity body = response.getBody();
        Assert.assertNotNull(body);
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_INTERNAL_ERROR.getCode()), body.getCode());
    }

    @Test
    public void commonExceptionKeepsItsOwnCodeAndHttpStatus() {
        ResponseErrorCode code = ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS;
        CommonException exception = new CommonException(code, "id=42");

        ResponseEntity<ResultEntity> response = controller.errorHandler(exception);

        Assert.assertEquals(HttpStatus.valueOf(code.getHttpStatus()), response.getStatusCode());
        ResultEntity body = response.getBody();
        Assert.assertNotNull(body);
        Assert.assertEquals(Integer.valueOf(code.getCode()), body.getCode());
        Assert.assertTrue(body.getMessage().endsWith(exception.getMessage()));
    }

    @Test
    public void bodyValidationErrorsNameTheFieldAndDefaultMessage() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "不能为空"));
        bindingResult.addError(new FieldError("request", "endpoint", "必须是 http(s) 地址"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                methodParameter(), bindingResult);

        ResponseEntity<ResultEntity> response = controller.argumentValidException(exception);

        Assert.assertEquals(HttpStatus.valueOf(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getHttpStatus()),
                response.getStatusCode());
        ResultEntity body = response.getBody();
        Assert.assertNotNull(body);
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getCode()), body.getCode());
        Assert.assertTrue(describe(body), body.getMessage().contains("name: 不能为空"));
        Assert.assertTrue(describe(body), body.getMessage().contains("endpoint: 必须是 http(s) 地址"));
        Assert.assertTrue(describe(body), body.getMessage().contains("; "));
    }

    @Test
    public void parameterValidationErrorsAreInvalidArgumentsNotInternalErrors() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<SampleRequest>> violations = validator.validate(new SampleRequest());
        Assert.assertFalse("sample must actually violate", violations.isEmpty());

        ResponseEntity<ResultEntity> response =
                controller.constraintViolationException(new ConstraintViolationException(violations));

        Assert.assertEquals(HttpStatus.valueOf(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getHttpStatus()),
                response.getStatusCode());
        ResultEntity body = response.getBody();
        Assert.assertNotNull(body);
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getCode()), body.getCode());
        Assert.assertTrue(describe(body), body.getMessage().contains("name: name不能为空"));
    }

    @Test
    public void unmappedPathIsA404StyleAnswerNotA500() {
        ResponseEntity<ResultEntity> response = controller.noHandlerFoundException(
                new NoHandlerFoundException("GET", "/api/nope", null));

        Assert.assertEquals(HttpStatus.valueOf(ResponseErrorCode.ERROR_PATH_NOT_EXISTS.getHttpStatus()),
                response.getStatusCode());
        ResultEntity body = response.getBody();
        Assert.assertNotNull(body);
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_PATH_NOT_EXISTS.getCode()), body.getCode());
        Assert.assertTrue(describe(body), body.getMessage().contains("GET /api/nope not exists"));
    }

    @Test
    public void httpStatusMappingCanBeDisabledForLegacyFrontends() throws Exception {
        Field flag = ExceptionController.class.getDeclaredField("httpStatusMappingEnabled");
        flag.setAccessible(true);
        flag.set(controller, false);

        ResponseEntity<ResultEntity> response = controller.errorHandler(new IllegalStateException("boom"));
        Assert.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_INTERNAL_ERROR.getCode()),
                response.getBody().getCode());
    }

    private static MethodParameter methodParameter() throws Exception {
        // a realistic handler signature: (String name) argument
        Method method = SampleRequest.class.getDeclaredMethod("sampleHandler", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static class SampleRequest {
        @NotBlank(message = "name不能为空")
        private final String name = null;

        void sampleHandler(String name) {
        }
    }

    private static BuilderException parseFailure(String sql) {
        try {
            new XmlSqlTemplate(sql);
        } catch (BuilderException e) {
            return e;
        }
        throw new AssertionError("expected the template to fail: " + sql);
    }

    private static String describe(ResultEntity body) {
        return "message was: " + body.getMessage();
    }
}