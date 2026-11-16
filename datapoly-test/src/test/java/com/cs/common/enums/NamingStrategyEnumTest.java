// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import static org.junit.Assert.*;

public class NamingStrategyEnumTest {

    @Test
    public void testNamingFunctionsTransformAsDeclared() {
        assertEquals("already-camel", NamingStrategyEnum.NONE.getFunction().apply("already-camel"));
        assertEquals("helloWorld", NamingStrategyEnum.CAMEL_CASE.getFunction().apply("hello_world"));
        assertEquals("snake_case_here", NamingStrategyEnum.SNAKE_CASE.getFunction().apply("snakeCaseHere"));
        assertEquals("alllower", NamingStrategyEnum.LOWER_CASE.getFunction().apply("AllLower"));
        assertEquals("ALLUPPER", NamingStrategyEnum.UPPER_CASE.getFunction().apply("allUpper"));
    }

    @Test
    public void testEveryConstantCarriesATransformation() {
        for (NamingStrategyEnum strategy : NamingStrategyEnum.values()) {
            assertNotNull("function missing for " + strategy, strategy.getFunction());
            assertNotNull("description lookup must answer for " + strategy, strategy.getDescription());
        }
    }
}
