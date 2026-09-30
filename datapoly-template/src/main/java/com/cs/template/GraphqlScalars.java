// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Scalars beyond the graphql-java built-ins (String/Int/Float/Boolean/ID) that SQL column types need:
 * {@code Long} (all integer types wider than Int), {@code Date} (yyyy-MM-dd), {@code Time} (HH:mm:ss)
 * and {@code DateTime} (ISO yyyy-MM-dd'T'HH:mm:ss). Hand-written Coercing instead of pulling the
 * graphql-java-extended-scalars artifact; date/time conversions use the JVM default zone because JDBC
 * values arrive zone-less.
 */
public final class GraphqlScalars {

    public static final GraphQLScalarType LONG = GraphQLScalarType.newScalar()
            .name("Long")
            .description("64-bit signed integer, maps JDBC integer columns wider than Int")
            .coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object dataFetcherResult) {
                    if (dataFetcherResult instanceof Number number) {
                        return number.longValue();
                    }
                    if (dataFetcherResult instanceof String text && !text.isEmpty()) {
                        try {
                            return Long.parseLong(text);
                        } catch (NumberFormatException ignored) {
                            // fall through to the error below
                        }
                    }
                    throw new CoercingSerializeException("Expected a Long, got: " + dataFetcherResult);
                }

                @Override
                public Object parseValue(Object input) {
                    if (input instanceof Number number) {
                        return number.longValue();
                    }
                    if (input instanceof String text && !text.isEmpty()) {
                        try {
                            return Long.parseLong(text);
                        } catch (NumberFormatException e) {
                            throw new CoercingParseValueException("Not a Long literal: " + text);
                        }
                    }
                    throw new CoercingParseValueException("Expected a Long, got: " + input);
                }

                @Override
                public Object parseLiteral(Object input) {
                    if (input instanceof IntValue value) {
                        return value.getValue().longValue();
                    }
                    if (input instanceof StringValue value) {
                        try {
                            return Long.parseLong(value.getValue());
                        } catch (NumberFormatException e) {
                            throw new CoercingParseLiteralException("Not a Long literal: " + value.getValue());
                        }
                    }
                    throw new CoercingParseLiteralException("Expected a Long literal, got: " + input);
                }
            })
            .build();

    public static final GraphQLScalarType DATE = GraphQLScalarType.newScalar()
            .name("Date")
            .description("Calendar date, yyyy-MM-dd, maps JDBC DATE columns")
            .coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object dataFetcherResult) {
                    if (dataFetcherResult instanceof LocalDate localDate) {
                        return localDate.toString();
                    }
                    if (dataFetcherResult instanceof java.util.Date date) {
                        return toLocalDate(date).toString();
                    }
                    throw new CoercingSerializeException("Expected a Date, got: " + dataFetcherResult);
                }

                @Override
                public Object parseValue(Object input) {
                    if (input instanceof String text) {
                        return parseLocalDate(text);
                    }
                    throw new CoercingParseValueException("Expected a yyyy-MM-dd string, got: " + input);
                }

                @Override
                public Object parseLiteral(Object input) {
                    if (input instanceof StringValue value) {
                        return parseLocalDate(value.getValue());
                    }
                    throw new CoercingParseLiteralException("Expected a yyyy-MM-dd string literal, got: " + input);
                }
            })
            .build();

    public static final GraphQLScalarType TIME = GraphQLScalarType.newScalar()
            .name("Time")
            .description("Wall-clock time, HH:mm:ss, maps JDBC TIME columns")
            .coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object dataFetcherResult) {
                    if (dataFetcherResult instanceof LocalTime localTime) {
                        return localTime.toString();
                    }
                    if (dataFetcherResult instanceof Time time) {
                        return time.toLocalTime().toString();
                    }
                    throw new CoercingSerializeException("Expected a Time, got: " + dataFetcherResult);
                }

                @Override
                public Object parseValue(Object input) {
                    if (input instanceof String text) {
                        return parseLocalTime(text);
                    }
                    throw new CoercingParseValueException("Expected an HH:mm:ss string, got: " + input);
                }

                @Override
                public Object parseLiteral(Object input) {
                    if (input instanceof StringValue value) {
                        return parseLocalTime(value.getValue());
                    }
                    throw new CoercingParseLiteralException("Expected an HH:mm:ss string literal, got: " + input);
                }
            })
            .build();

    public static final GraphQLScalarType DATE_TIME = GraphQLScalarType.newScalar()
            .name("DateTime")
            .description("Timestamp, ISO yyyy-MM-dd'T'HH:mm:ss, maps JDBC TIMESTAMP columns")
            .coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object dataFetcherResult) {
                    if (dataFetcherResult instanceof LocalDateTime localDateTime) {
                        return localDateTime.toString();
                    }
                    if (dataFetcherResult instanceof java.util.Date date) {
                        return toLocalDateTime(date).toString();
                    }
                    throw new CoercingSerializeException("Expected a DateTime, got: " + dataFetcherResult);
                }

                @Override
                public Object parseValue(Object input) {
                    if (input instanceof String text) {
                        return parseLocalDateTime(text);
                    }
                    throw new CoercingParseValueException("Expected an ISO yyyy-MM-dd'T'HH:mm:ss string, got: " + input);
                }

                @Override
                public Object parseLiteral(Object input) {
                    if (input instanceof StringValue value) {
                        return parseLocalDateTime(value.getValue());
                    }
                    throw new CoercingParseLiteralException("Expected an ISO yyyy-MM-dd'T'HH:mm:ss string literal, got: " + input);
                }
            })
            .build();

    private GraphqlScalars() {
    }

    private static LocalDate toLocalDate(java.util.Date date) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
    }

    private static LocalDateTime toLocalDateTime(java.util.Date date) {
        if (date instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
    }

    private static LocalDate parseLocalDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException e) {
            throw new CoercingParseValueException("Not a yyyy-MM-dd date: " + text);
        }
    }

    private static LocalTime parseLocalTime(String text) {
        try {
            return LocalTime.parse(text);
        } catch (RuntimeException e) {
            throw new CoercingParseValueException("Not an HH:mm:ss time: " + text);
        }
    }

    private static LocalDateTime parseLocalDateTime(String text) {
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException e) {
            throw new CoercingParseValueException("Not an ISO yyyy-MM-dd'T'HH:mm:ss datetime: " + text);
        }
    }
}
