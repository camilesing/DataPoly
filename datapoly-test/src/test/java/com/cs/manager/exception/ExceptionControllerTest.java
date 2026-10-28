// Use of this source code is governed by a BSD-style license
package com.cs.manager.exception;

import com.cs.common.dto.ResultEntity;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.template.XmlSqlTemplate;
import org.apache.ibatis.builder.BuilderException;
import org.junit.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;

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