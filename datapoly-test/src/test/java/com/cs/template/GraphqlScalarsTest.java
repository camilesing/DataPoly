// Use of this source code is governed by a BSD-style license
package com.cs.template;

import graphql.language.BooleanValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.junit.Test;

import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Coercing contracts of {@link GraphqlScalars}: values and literals in both directions, plus the
 * errors raised for anything that is not the scalar's shape. java.util.Date inputs are built from
 * the JVM default zone (the same zone the conversion under test uses), so assertions hold on any
 * host timezone.
 */
public class GraphqlScalarsTest {

    private static Coercing<?, ?> coercing(GraphQLScalarType scalar) {
        return scalar.getCoercing();
    }

    private static java.util.Date utilDateOf(LocalDate date) {
        return java.util.Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static java.util.Date utilDateOf(LocalDateTime dateTime) {
        return java.util.Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    public void longSerializesNumbersAndNumericStrings() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.LONG);
        assertEquals(7L, coercing.serialize(7));
        // any Number narrows through longValue()
        assertEquals(3L, coercing.serialize(3.9D));
        assertEquals(9L, coercing.serialize((short) 9));
        assertEquals(123L, coercing.serialize("123"));
    }

    @Test
    public void longSerializeRejectsTheUnconvertible() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.LONG);
        assertSerializeRejected(coercing, null);
        assertSerializeRejected(coercing, "");
        assertSerializeRejected(coercing, "not-a-number");
        assertSerializeRejected(coercing, new Object());
    }

    @Test
    public void longParsesValuesAsNumbersOrStrings() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.LONG);
        assertEquals(42L, coercing.parseValue(42));
        assertEquals(123L, coercing.parseValue("123"));
        try {
            coercing.parseValue("twelve");
            fail("expected CoercingParseValueException");
        } catch (CoercingParseValueException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Not a Long literal"));
        }
        assertParseValueRejected(coercing, "");
        assertParseValueRejected(coercing, null);
    }

    @Test
    public void longParsesIntAndStringLiterals() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.LONG);
        assertEquals(9L, coercing.parseLiteral(new IntValue(BigInteger.valueOf(9))));
        assertEquals(123L, coercing.parseLiteral(StringValue.of("123")));
        try {
            coercing.parseLiteral(StringValue.of("twelve"));
            fail("expected CoercingParseLiteralException");
        } catch (CoercingParseLiteralException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Not a Long literal"));
        }
        assertParseLiteralRejected(coercing, BooleanValue.of(true));
    }

    @Test
    public void dateSerializesLocalAndUtilDates() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.DATE);
        assertEquals("2026-01-15", coercing.serialize(LocalDate.of(2026, 1, 15)));
        assertEquals("2026-01-15", coercing.serialize(utilDateOf(LocalDate.of(2026, 1, 15))));
        assertEquals("2026-01-15", coercing.serialize(java.sql.Date.valueOf(LocalDate.of(2026, 1, 15))));
        assertSerializeRejected(coercing, "2026-01-15");
        assertSerializeRejected(coercing, LocalTime.of(8, 5, 3));
    }

    @Test
    public void dateParsesStringValuesAndLiterals() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.DATE);
        assertEquals(LocalDate.of(2026, 1, 15), coercing.parseValue("2026-01-15"));
        try {
            coercing.parseValue("2026-02-30");
            fail("expected CoercingParseValueException");
        } catch (CoercingParseValueException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Not a yyyy-MM-dd date"));
        }
        assertParseValueRejected(coercing, 20260115);
        assertEquals(LocalDate.of(2026, 1, 15), coercing.parseLiteral(StringValue.of("2026-01-15")));
        assertParseLiteralRejected(coercing, new IntValue(BigInteger.ONE));
    }

    @Test
    public void timeSerializesLocalAndSqlTimes() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.TIME);
        assertEquals("08:05:03", coercing.serialize(LocalTime.of(8, 5, 3)));
        assertEquals("08:05:03", coercing.serialize(Time.valueOf(LocalTime.of(8, 5, 3))));
        assertSerializeRejected(coercing, LocalDate.of(2026, 1, 15));
        assertSerializeRejected(coercing, "08:05:03");
    }

    @Test
    public void timeParsesStringValuesAndLiterals() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.TIME);
        assertEquals(LocalTime.of(8, 5, 3), coercing.parseValue("08:05:03"));
        try {
            coercing.parseValue("25:00:00");
            fail("expected CoercingParseValueException");
        } catch (CoercingParseValueException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Not an HH:mm:ss time"));
        }
        assertParseValueRejected(coercing, null);
        assertEquals(LocalTime.of(8, 5, 3), coercing.parseLiteral(StringValue.of("08:05:03")));
        assertParseLiteralRejected(coercing, BooleanValue.of(false));
    }

    @Test
    public void dateTimeSerializesLocalUtilAndSqlTimestamps() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.DATE_TIME);
        LocalDateTime value = LocalDateTime.of(2026, 1, 15, 10, 30, 45);
        assertEquals("2026-01-15T10:30:45", coercing.serialize(value));
        assertEquals("2026-01-15T10:30:45", coercing.serialize(Timestamp.valueOf(value)));
        assertEquals("2026-01-15T10:30:45", coercing.serialize(utilDateOf(value)));
        assertSerializeRejected(coercing, LocalDate.of(2026, 1, 15));
        assertSerializeRejected(coercing, "2026-01-15T10:30:45");
    }

    @Test
    public void dateTimeParsesStringValuesAndLiterals() {
        Coercing<?, ?> coercing = coercing(GraphqlScalars.DATE_TIME);
        assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30, 45), coercing.parseValue("2026-01-15T10:30:45"));
        try {
            // a date alone is not a datetime
            coercing.parseValue("2026-01-15");
            fail("expected CoercingParseValueException");
        } catch (CoercingParseValueException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Not an ISO yyyy-MM-dd'T'HH:mm:ss datetime"));
        }
        assertParseValueRejected(coercing, 1);
        assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30, 45),
                coercing.parseLiteral(StringValue.of("2026-01-15T10:30:45")));
        assertParseLiteralRejected(coercing, new IntValue(BigInteger.ONE));
    }

    private static void assertSerializeRejected(Coercing<?, ?> coercing, Object value) {
        try {
            coercing.serialize(value);
        } catch (CoercingSerializeException e) {
            assertTrue(String.valueOf(e.getMessage()), e.getMessage() != null);
            return;
        }
        fail("expected CoercingSerializeException for: " + value);
    }

    private static void assertParseValueRejected(Coercing<?, ?> coercing, Object value) {
        try {
            coercing.parseValue(value);
        } catch (CoercingParseValueException e) {
            assertTrue(String.valueOf(e.getMessage()), e.getMessage() != null);
            return;
        }
        fail("expected CoercingParseValueException for: " + value);
    }

    private static void assertParseLiteralRejected(Coercing<?, ?> coercing, Object literal) {
        try {
            coercing.parseLiteral(literal);
        } catch (CoercingParseLiteralException e) {
            assertTrue(String.valueOf(e.getMessage()), e.getMessage() != null);
            return;
        }
        fail("expected CoercingParseLiteralException for: " + literal);
    }
}
