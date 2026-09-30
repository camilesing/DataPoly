// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

import graphql.language.Argument;
import graphql.language.DirectivesContainer;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.StringValue;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * A GraphQL SDL document acting as an API template (GRAPHQL engine). The document declares the
 * {@code Query} (and optionally {@code Mutation}) type whose root fields each carry the built-in
 * {@code @sql(sql: """...""")} directive holding a MyBatis dynamic SQL template; at request time the
 * engine builds the executable schema via runtime wiring (no code generation) and resolves root
 * fields by rendering that SQL with the field arguments plus request variables.
 *
 * <p>Built-in declarations ({@code @sql} directive and the {@code Long}/{@code Date}/{@code Time}/
 * {@code DateTime} scalars) are merged in automatically and skipped when the document already
 * declares the same name. SQL inside the directive is an XML fragment exactly like the SQL engine's
 * templates: {@code #{}} binds PreparedStatement placeholders, {@code <if>/<where>/<foreach>} tags
 * are dynamic, and {@code &}/{@code <} must be XML-escaped outside tags.</p>
 */
public class GraphqlSdlTemplate {

    public static final String SQL_DIRECTIVE_NAME = "sql";
    public static final String SQL_DIRECTIVE_ARGUMENT = "sql";

    private static final String QUERY_TYPE = "Query";
    private static final String MUTATION_TYPE = "Mutation";

    private static final String SQL_DIRECTIVE_DECLARATION = "directive @sql(sql: String!) on FIELD_DEFINITION";
    private static final List<String> BUILTIN_SCALAR_NAMES = List.of("Long", "Date", "Time", "DateTime");

    private final String sdl;
    private final TypeDefinitionRegistry registry;

    public GraphqlSdlTemplate(String sdl) {
        this.sdl = Objects.requireNonNull(sdl, "sdl");
        String document = mergeBuiltinDeclarations(sdl);
        try {
            this.registry = new SchemaParser().parse(document);
        } catch (Exception e) {
            throw new GraphqlSdlException("GraphQL SDL parse failed: " + e.getMessage(), e);
        }
    }

    public String getSdl() {
        return sdl;
    }

    /**
     * Prepends the built-in {@code @sql} directive and Long/Date/Time/DateTime scalar declarations
     * that the document does not already provide. Done at the text level because registry-level
     * merges reject duplicate-ish definitions silently and their API shifts between versions.
     */
    private static String mergeBuiltinDeclarations(String sdl) {
        StringBuilder document = new StringBuilder();
        if (!sdl.contains("directive @" + SQL_DIRECTIVE_NAME)) {
            document.append(SQL_DIRECTIVE_DECLARATION).append('\n');
        }
        for (String scalar : BUILTIN_SCALAR_NAMES) {
            if (!Pattern.compile("(?m)^\\s*scalar\\s+" + scalar + "\\b").matcher(sdl).find()) {
                document.append("scalar ").append(scalar).append('\n');
            }
        }
        return document.append(sdl).toString();
    }

    /**
     * Validates the whole document: parseability, a non-empty {@code Query} type, {@code @sql} on
     * every {@code Query}/{@code Mutation} root field, and that the schema assembles into an
     * executable form (dangling type references and unwired scalars fail here).
     */
    public void validate() {
        if (!(registry.types().get(QUERY_TYPE) instanceof ObjectTypeDefinition)) {
            throw new GraphqlSdlException("GraphQL SDL must define a Query type");
        }
        Map<FieldCoordinates, String> rootFields = extractRootFieldSql();
        if (rootFields.isEmpty()) {
            throw new GraphqlSdlException("Query type declares no field");
        }
        buildGraphQLSchema((coordinates, sql) -> environment -> null);
    }

    /**
     * @return the {@code @sql} text of every {@code Query}/{@code Mutation} root field, keyed by
     *         field coordinate and ordered as declared
     */
    public Map<FieldCoordinates, String> extractRootFieldSql() {
        Map<FieldCoordinates, String> result = new LinkedHashMap<>();
        for (String rootType : Arrays.asList(QUERY_TYPE, MUTATION_TYPE)) {
            if (!(registry.types().get(rootType) instanceof ObjectTypeDefinition typeDefinition)) {
                continue;
            }
            for (FieldDefinition field : typeDefinition.getFieldDefinitions()) {
                result.put(FieldCoordinates.coordinates(rootType, field.getName()),
                        fieldSql(rootType, field));
            }
        }
        return result;
    }

    /**
     * Assembles the executable schema. Each root field's {@link DataFetcher} comes from
     * {@code rootFieldFetcher} applied to the field coordinate and its {@code @sql} text; other
     * fields keep graphql-java's default map-property resolution.
     */
    public GraphQLSchema buildGraphQLSchema(
            BiFunction<FieldCoordinates, String, DataFetcher<?>> rootFieldFetcher) {
        Map<FieldCoordinates, String> rootFields = extractRootFieldSql();
        RuntimeWiring.Builder wiring = RuntimeWiring.newRuntimeWiring()
                .scalar(GraphqlScalars.LONG)
                .scalar(GraphqlScalars.DATE)
                .scalar(GraphqlScalars.TIME)
                .scalar(GraphqlScalars.DATE_TIME);
        Map<String, TypeRuntimeWiring.Builder> typeWirings = new LinkedHashMap<>();
        rootFields.forEach((coordinates, sql) -> typeWirings
                .computeIfAbsent(coordinates.getTypeName(), typeName -> TypeRuntimeWiring.newTypeWiring(typeName))
                .dataFetcher(coordinates.getFieldName(), rootFieldFetcher.apply(coordinates, sql)));
        typeWirings.values().forEach(wiring::type);
        try {
            return new SchemaGenerator().makeExecutableSchema(registry, wiring.build());
        } catch (Exception e) {
            throw new GraphqlSdlException("GraphQL schema build failed: " + e.getMessage(), e);
        }
    }

    private static String fieldSql(String typeName, FieldDefinition field) {
        String coordinate = typeName + "." + field.getName();
        if (!(field instanceof DirectivesContainer<?>)) {
            throw new GraphqlSdlException("Root field " + coordinate + " must declare @" + SQL_DIRECTIVE_NAME + "(sql: \"...\")");
        }
        return ((DirectivesContainer<?>) field).getDirectives().stream()
                .filter(directive -> SQL_DIRECTIVE_NAME.equals(directive.getName()))
                .findFirst()
                .map(directive -> directive.getArguments().stream()
                        .filter(argument -> SQL_DIRECTIVE_ARGUMENT.equals(argument.getName()))
                        .map(Argument::getValue)
                        .filter(StringValue.class::isInstance)
                        .map(value -> ((StringValue) value).getValue())
                        .findFirst()
                        .orElseThrow(() -> new GraphqlSdlException(
                                "Root field " + coordinate + " @" + SQL_DIRECTIVE_NAME + " is missing the sql argument")))
                .filter(sql -> !sql.isBlank())
                .orElseThrow(() -> new GraphqlSdlException(
                        "Root field " + coordinate + " must declare @" + SQL_DIRECTIVE_NAME + "(sql: \"...\")"));
    }
}
