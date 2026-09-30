// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * SDL document parsing and validation for the GRAPHQL engine: built-in @sql directive and scalar
 * injection (with user declarations winning), block-string indentation stripping, root-field
 * contract enforcement, and executable schema wiring.
 */
public class GraphqlSdlTemplateTest {

    private static final String VALID_SDL = "type Query {\n" +
            "  users(id: Long, nameLike: String): [User]\n" +
            "    @sql(sql: \"\"\"SELECT id, name FROM t_user WHERE 1=1\n" +
            "      <if test=\"id != null\"> AND id = #{id}</if>\n" +
            "      <if test=\"nameLike != null and nameLike != ''\"> AND name LIKE CONCAT('%', #{nameLike}, '%')</if>\"\"\")\n" +
            "}\n" +
            "type User { id: Long  name: String }\n";

    @Test
    public void validateAcceptsDocumentWithSqlDirective() {
        new GraphqlSdlTemplate(VALID_SDL).validate();
    }

    @Test
    public void extractedSqlHasBlockIndentationStripped() {
        Map<FieldCoordinates, String> fields = new GraphqlSdlTemplate(VALID_SDL).extractRootFieldSql();
        assertEquals(1, fields.size());
        String sql = fields.get(FieldCoordinates.coordinates("Query", "users"));
        assertEquals("SELECT id, name FROM t_user WHERE 1=1\n" +
                "<if test=\"id != null\"> AND id = #{id}</if>\n" +
                "<if test=\"nameLike != null and nameLike != ''\"> AND name LIKE CONCAT('%', #{nameLike}, '%')</if>", sql);
    }

    @Test
    public void rejectsMissingQueryType() {
        try {
            new GraphqlSdlTemplate("type Foo { id: Long }").validate();
            fail("Missing Query type should be rejected");
        } catch (GraphqlSdlException expected) {
            assertTrue(expected.getMessage().contains("Query"));
        }
    }

    @Test
    public void rejectsRootFieldWithoutSqlDirective() {
        String sdl = "type Query {\n" +
                "  users: [User]\n" +
                "}\n" +
                "type User { id: Long }\n";
        try {
            new GraphqlSdlTemplate(sdl).validate();
            fail("Root field without @sql should be rejected");
        } catch (GraphqlSdlException expected) {
            assertTrue(expected.getMessage().contains("Query.users"));
        }
    }

    @Test
    public void rejectsRootFieldWithBlankSql() {
        String sdl = "type Query {\n" +
                "  users: [User] @sql(sql: \"   \")\n" +
                "}\n" +
                "type User { id: Long }\n";
        try {
            new GraphqlSdlTemplate(sdl).validate();
            fail("Blank @sql content should be rejected");
        } catch (GraphqlSdlException expected) {
            assertTrue(expected.getMessage().contains("Query.users"));
        }
    }

    @Test
    public void rejectsMalformedSdl() {
        try {
            new GraphqlSdlTemplate("type Query {").validate();
            fail("Broken SDL syntax should be rejected");
        } catch (GraphqlSdlException expected) {
            // parse failure message
        }
    }

    @Test
    public void rejectsDanglingTypeReference() {
        String sdl = "type Query {\n" +
                "  users: [Missing] @sql(sql: \"SELECT 1\")\n" +
                "}\n";
        try {
            new GraphqlSdlTemplate(sdl).validate();
            fail("Reference to an undeclared type should be rejected at schema assembly");
        } catch (GraphqlSdlException expected) {
            // schema build failure message
        }
    }

    @Test
    public void userDeclaredScalarWinsOverBuiltinInjection() {
        String sdl = "scalar Long\n" +
                "type Query {\n" +
                "  users: [User] @sql(sql: \"SELECT 1 AS id\")\n" +
                "}\n" +
                "type User { id: Long }\n";
        // The user declaration must not collide with the injected one, and the scalar still wires
        new GraphqlSdlTemplate(sdl).validate();
    }

    @Test
    public void builtSchemaWiresRootFieldFetchers() {
        GraphQLSchema schema = new GraphqlSdlTemplate(VALID_SDL).buildGraphQLSchema(
                (coordinates, sql) -> environment -> List.of(Map.of("id", 7L, "name", "seven")));
        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute("{ users { id name } }");
        assertTrue(result.getErrors().toString(), result.getErrors().isEmpty());
        Map<?, ?> data = result.getData();
        List<?> users = (List<?>) data.get("users");
        assertEquals(1, users.size());
        assertEquals(Map.of("id", 7L, "name", "seven"), users.get(0));
    }

    @Test
    public void mutationFieldsFollowTheSameContract() {
        String sdl = "type Query {\n" +
                "  all: [User] @sql(sql: \"SELECT id, name FROM t_user\")\n" +
                "}\n" +
                "type Mutation {\n" +
                "  add(name: String): String @sql(sql: \"INSERT INTO t_user(name) VALUES (#{name})\")\n" +
                "}\n" +
                "type User { id: Long  name: String }\n";
        Map<FieldCoordinates, String> fields = new GraphqlSdlTemplate(sdl).extractRootFieldSql();
        assertEquals(2, fields.size());
        assertTrue(fields.containsKey(FieldCoordinates.coordinates("Mutation", "add")));
        new GraphqlSdlTemplate(sdl).validate();
    }
}
