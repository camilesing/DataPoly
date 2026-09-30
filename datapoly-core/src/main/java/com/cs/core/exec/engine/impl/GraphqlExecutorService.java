// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.consts.Constants;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.exec.engine.AbstractExecutorEngine;
import com.cs.core.util.PageSizeUtils;
import com.cs.core.util.SqlJdbcUtils;
import com.cs.persistence.entity.ApiContextEntity;
import com.cs.persistence.util.JsonUtils;
import com.cs.template.DollarSubstitutionException;
import com.cs.template.GraphqlSdlTemplate;
import com.cs.template.SqlMeta;
import com.cs.template.XmlSqlTemplate;
import com.zaxxer.hikari.HikariDataSource;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphqlErrorBuilder;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.transform.FieldVisibilitySchemaTransformation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/**
 * GRAPHQL engine: the single template context holds a GraphQL SDL document whose root fields carry
 * {@code @sql(sql: """...""")} directives (see {@link GraphqlSdlTemplate}). The executable schema is
 * assembled at runtime — no class generation — and cached per SDL digest; compiled data fetchers are
 * stateless and receive the datasource/strategy/{@code ${}} policy per request through the
 * {@link ExecutionInput} context, so identical SDLs from different APIs safely share one cache entry.
 *
 * <p>Request parameters are the fixed pair {@code query} (the GraphQL document) and {@code variables};
 * a root field renders its SQL with field arguments merged over the request variables, so MyBatis
 * dynamic tags, {@code #{}} binding and the apiPageNum/apiPageSize pagination convention all behave
 * as in the SQL engine. Field-level failures surface as GraphQL {@code errors} entries per spec;
 * engine-level failures (missing query, timeout, broken SDL) raise {@link CommonException}. Each
 * field executes on its own connection — there is no cross-field transaction (v1 scope).</p>
 */
@Slf4j
public class GraphqlExecutorService extends AbstractExecutorEngine {

    private static final String TIMEOUT_KEY = "datapoly.executor.graphql.timeout-seconds";
    private static final String MAX_DEPTH_KEY = "datapoly.executor.graphql.max-depth";
    private static final String INTROSPECTION_KEY = "datapoly.executor.graphql.introspection-enabled";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final int SCHEMA_CACHE_MAX_ENTRIES = 128;

    /**
     * Bounded LRU of assembled GraphQL runtimes keyed by SDL digest. Schema assembly is far from free,
     * and published SDLs are immutable snapshots, so entries only churn when definitions evolve.
     */
    private static final Map<String, GraphQL> SCHEMA_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, GraphQL>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, GraphQL> eldest) {
                    return size() > SCHEMA_CACHE_MAX_ENTRIES;
                }
            });

    /** Per-request execution context: everything a cached data fetcher must not bake in. */
    private record RequestContext(HikariDataSource dataSource, ProductTypeEnum productType,
                                  NamingStrategyEnum strategy, boolean dollarSubstitutionAllowed,
                                  boolean printSqlLog, Map<String, Object> variables) {
    }

    public GraphqlExecutorService(HikariDataSource dataSource, ProductTypeEnum productType) {
        super(dataSource, productType);
    }

    @Override
    public List<Object> execute(List<ApiContextEntity> scripts, Map<String, Object> params, NamingStrategyEnum strategy,
                                boolean dollarSubstitutionAllowed) {
        if (null == scripts || scripts.size() != 1) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "api.graphql.context.invalid");
        }
        String query = params == null ? null : (String) params.get(Constants.PARAM_GRAPHQL_QUERY);
        if (StringUtils.isBlank(query)) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "api.graphql.query.missing");
        }
        Map<String, Object> variables = readVariables(params.get(Constants.PARAM_GRAPHQL_VARIABLES));

        GraphQL graphql = SCHEMA_CACHE.computeIfAbsent(DigestUtil.sha256Hex(scripts.get(0).getSqlText()),
                digest -> buildGraphQL(scripts.get(0).getSqlText()));

        RequestContext context = new RequestContext(this.dataSource, this.productType, strategy,
                dollarSubstitutionAllowed, this.printSqlLog, variables);
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .variables(variables)
                .context(context)
                .build();
        long timeoutSeconds = getIntegerProperty(TIMEOUT_KEY, DEFAULT_TIMEOUT_SECONDS);
        try {
            ExecutionResult result = graphql.executeAsync(input).get(timeoutSeconds, TimeUnit.SECONDS);
            return Collections.singletonList(result.toSpecification());
        } catch (TimeoutException e) {
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "api.graphql.execute.timeout",
                    timeoutSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, e);
        } catch (ExecutionException e) {
            Throwable cause = null == e.getCause() ? e : e.getCause();
            if (cause instanceof CommonException commonException) {
                throw commonException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, cause);
        }
    }

    /**
     * Variables arrive as a map through the request-body parameter path, or as a JSON string through
     * the debug panel (which submits plain string parameter values); both forms are accepted here.
     */
    private static Map<String, Object> readVariables(Object rawVariables) {
        if (rawVariables instanceof Map variablesMap) {
            return variablesMap;
        }
        if (rawVariables instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return JsonUtils.toBeanObject(text, Map.class);
            } catch (Exception e) {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "api.graphql.variables.invalid");
            }
        }
        return Collections.emptyMap();
    }

    private GraphQL buildGraphQL(String sdl) {
        GraphqlSdlTemplate template = new GraphqlSdlTemplate(sdl);
        GraphQLSchema schema = template.buildGraphQLSchema(
                (coordinates, sql) -> sqlDataFetcher(new XmlSqlTemplate(sql)));
        if (!Boolean.parseBoolean(getProperty(INTROSPECTION_KEY, "true"))) {
            // Introspection off: strip the __schema/__type meta-fields from the assembled schema
            schema = new FieldVisibilitySchemaTransformation(
                    environment -> !environment.getSchemaElement().getName().startsWith("__")).apply(schema);
        }
        return GraphQL.newGraphQL(schema)
                .instrumentation(new MaxQueryDepthInstrumentation(getIntegerProperty(MAX_DEPTH_KEY, DEFAULT_MAX_DEPTH)))
                .build();
    }

    /**
     * Renders the field's SQL with arguments merged over the request variables and runs it on the
     * request's datasource. The raw (pre-coercion) variables map is used because graphql-java drops
     * values for variables the query document never declared — the apiPageNum/apiPageSize pagination
     * convention must survive that. The template is parsed once at schema assembly and reused
     * concurrently (each {@link XmlSqlTemplate#process} builds its own dynamic script node, like
     * MyBatis DynamicSqlSource). Failures become GraphQL field errors rather than transport errors.
     */
    private static DataFetcher<?> sqlDataFetcher(XmlSqlTemplate template) {
        return environment -> {
            RequestContext context = environment.getContext();
            Map<String, Object> templateParams = new HashMap<>(context.variables());
            templateParams.putAll(environment.getArguments());
            SqlMeta sqlMeta;
            try {
                sqlMeta = template.process(templateParams, context.dollarSubstitutionAllowed());
            } catch (DollarSubstitutionException e) {
                log.warn("Blocked SQL template with dollar substitution: {}", e.getMessage());
                throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "api.sql.dollar.forbidden");
            }
            int page = PageSizeUtils.getPageFromParams(templateParams);
            int size = PageSizeUtils.getSizeFromParams(templateParams);
            boolean isPaging = PageSizeUtils.shouldAppendPagination(templateParams);
            try (Connection connection = context.dataSource().getConnection()) {
                return SqlJdbcUtils.execute(context.productType(), connection, sqlMeta, context.strategy(),
                        page, size, isPaging, context.printSqlLog());
            } catch (SQLException e) {
                return DataFetcherResult.newResult().error(GraphqlErrorBuilder.newError()
                        .message("SQL execution failed: " + e.getMessage())
                        .build()).build();
            }
        };
    }

    private static int getIntegerProperty(String key, int defaultValue) {
        String value = getProperty(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String getProperty(String key, String defaultValue) {
        try {
            String value = StringUtils.trimToEmpty(SpringUtil.getBean(Environment.class).getProperty(key, defaultValue));
            return value.isEmpty() ? defaultValue : value;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
