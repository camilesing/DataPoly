// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.*;

import java.util.Map;

@Slf4j
public final class SpelUtils {

    private final static ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * Uses SimpleEvaluationContext (read-only data binding): only variables and Map/POJO property access are supported;
     * type references T(), constructors, arbitrary method calls and bean references are forbidden, shrinking the SpEL attack surface;
     * on evaluation failure the original expression text is returned as a fallback (same as the existing behavior).
     */
    public static String getExpressionValue(String expr, Map<String, Object> paramValues) {
        EvaluationContext context = new SimpleEvaluationContext.Builder(
                DataBindingPropertyAccessor.forReadOnlyAccess(), new MapAccessor()).build();
        for (Map.Entry<String, Object> entry : paramValues.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        try {
            Object value = expressionParser.parseExpression(expr).getValue(context);
            if (null != value) {
                return value.toString();
            }
        } catch (Exception e) {
            log.warn("Parse SpEL value from parameters failed:{}", e.getMessage());
        }
        return expr;
    }
}
