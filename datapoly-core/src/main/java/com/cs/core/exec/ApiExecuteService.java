// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec;

import cn.hutool.crypto.digest.DigestUtil;
import com.cs.cache.*;
import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.common.util.I18nUtils;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.exec.engine.ApiExecutorEngineFactory;
import com.cs.core.exec.extractor.HttpRequestBodyExtractor;
import com.cs.core.exec.logger.RequestParamLogger;
import com.cs.core.util.*;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.*;
import com.cs.persistence.util.JsonUtils;
import com.google.common.collect.Lists;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ApiExecuteService {

    @Resource
    private CacheFactory cacheFactory;
    @Resource
    private DataSourceDao dataSourceDao;
    @Resource
    private ExecutorMetadataCache executorMetadataCache;
    @Resource
    private DriverLoadService driverLoadService;
    @Resource
    private List<HttpRequestBodyExtractor> requestBodyExtractors;

    private DistributedCache getDistributedCache() {
        return cacheFactory.getDistributedCache(Constants.CACHE_NAME_API_RESPONSE);
    }

    private String getCacheKeyValue(ApiAssignmentEntity config, Map<String, Object> paramValues) {
        String key = CacheKeyTypeEnum.SPEL == config.getCacheKeyType()
                ? SpelUtils.getExpressionValue(config.getCacheKeyExpr(), paramValues)
                : DigestUtil.sha256Hex(JsonUtils.toJsonString(new TreeMap<>(paramValues)));
        return buildCacheKey(Constants.getResourceName(config.getMethod().name(), config.getPath()),
                config.getCommitId(), key);
    }

    /**
     * Response cache key is versioned with the online commitId (A5): after deploying a new version, old cache keys become unreachable immediately,
     * and stale entries are reclaimed naturally by the existing per-entry TTL (cacheExpireSeconds).
     */
    static String buildCacheKey(String resourceName, Long commitId, String rawKey) {
        return resourceName + ":" + Optional.ofNullable(commitId).orElse(0L) + ":" + rawKey;
    }

    public ResultEntity<Object> execute(ApiAssignmentEntity config, HttpServletRequest request, boolean printSqlLog) {
        String resourceName = Constants.getResourceName(config.getMethod().name(), config.getPath());
        try {
            List<ItemParam> invalidArgs = new ArrayList<>();
            Map<String, Object> paramValues = mergeParameters(request, config.getParams(), invalidArgs);
            RequestParamLogger.set(paramValues);
            if (invalidArgs.size() > 0) {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, convertInvalidArgs(invalidArgs));
            }
            return execute(config, paramValues, printSqlLog);
        } catch (IOException e) {
            log.warn("Failed read input body parameters for {}, error:{}", resourceName, e.getMessage());
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, e);
        }
    }

    public ResultEntity<Object> execute(ApiAssignmentEntity config, Map<String, Object> paramValues,
                                        boolean printSqlLog) {
        if (config.getCacheKeyType().isUseCache()) {
            String key = getCacheKeyValue(config, paramValues);
            DistributedCache cache = getDistributedCache();
            ResultEntity result = cache.get(key, ResultEntity.class);
            if (null == result) {
                result = doExecute(getDataSourceEntity(config), config, paramValues, printSqlLog);
                cache.put(key, result, config.getCacheExpireSeconds(), TimeUnit.SECONDS);
            } else {
                String resourceName = Constants.getResourceName(config.getMethod().name(), config.getPath());
                log.info("Execute for {} find cache response by cacheKey={}", resourceName, key);
            }
            return result;
        } else {
            return doExecute(getDataSourceEntity(config), config, paramValues, printSqlLog);
        }
    }

    private DataSourceEntity getDataSourceEntity(ApiAssignmentEntity config) {
        // Local short-TTL metadata cache (A2): datasource config changes take effect within one TTL at most; negative results are also cached as NOT_EXISTS
        DataSourceEntity dsEntity = executorMetadataCache.getDataSource(config.getDatasourceId(),
                () -> dataSourceDao.getById(config.getDatasourceId()));
        if (null == dsEntity) {
            String message = I18nUtils.getMessage("common.datasource.not.found", config.getDatasourceId());
            log.warn("Error for handle api[id={}],information:{}", config.getId(), message);
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, message);
        }
        return dsEntity;
    }

    private ResultEntity doExecute(DataSourceEntity dsEntity, ApiAssignmentEntity config,
                                   Map<String, Object> paramValues, boolean printSqlLog) {
        File driverPath = driverLoadService.getVersionDriverFile(dsEntity.getType(), dsEntity.getVersion());
        HikariDataSource dataSource = DataSourceUtils.getHikariDataSource(dsEntity, driverPath.getAbsolutePath());
        // open=true public APIs forcibly forbid ${} literal substitution (S3); the global switch can tighten this to all APIs
        boolean dollarSubstitutionAllowed = SqlTemplateGuard.isDollarSubstitutionAllowed(config.getOpen());
        List<Object> results = ApiExecutorEngineFactory
                .getExecutor(config.getEngine(), dataSource, dsEntity.getType(), printSqlLog)
                .execute(config.getContextList(), paramValues, config.getNamingStrategy(), dollarSubstitutionAllowed);
        Object answer = results.size() > 1 ? results : results.stream().findAny().orElse(null);
        if (ProductTypeEnum.HTTP == dsEntity.getType() && answer instanceof List) {
            answer = ((List) answer).get(0);
        }
        return ResultEntity.success(answer);
    }

    private String convertInvalidArgs(List<ItemParam> invalidArgs) {
        return I18nUtils.getMessage("api.invalid.param")
                + ","
                + invalidArgs.stream()
                .map(
                        p ->
                                (p.getIsArray() ? I18nUtils.getMessage("api.param.array") : "")
                                        + I18nUtils.getMessage("api.param.name", p.getName())
                                        + p.getLocation().getIn())
                .collect(Collectors.joining(";"));
    }

    private Map<String, Object> mergeParameters(HttpServletRequest request, List<ItemParam> params,
                                                List<ItemParam> invalidArgs) throws IOException {
        if (CollectionUtils.isEmpty(params)) {
            return Collections.emptyMap();
        }

        Map<String, Object> map = new HashMap<>(params.size());
        Map<String, Object> bodyMap = getRequestBodyMap(request);
        for (ItemParam param : params) {
            String name = param.getName();
            ParamTypeEnum type = param.getType();
            ParamLocationEnum location = param.getLocation();
            Boolean isArray = param.getIsArray();
            Boolean required = param.getRequired();
            String defaultValue = param.getDefaultValue();
            if (location == ParamLocationEnum.REQUEST_HEADER) {
                List<Object> hv = Collections.list(request.getHeaders(name))
                        .stream().map(v -> type.getConverter().apply(v))
                        .collect(Collectors.toList());
                if (isArray) {
                    if (CollectionUtils.isEmpty(hv)) {
                        if (required) {
                            invalidArgs.add(param);
                        }
                    } else {
                        map.put(name, hv);
                    }
                } else {
                    if (CollectionUtils.isEmpty(hv)) {
                        if (required) {
                            invalidArgs.add(param);
                        } else {
                            map.put(name, type.getConverter().apply(defaultValue));
                        }
                    } else {
                        map.put(name, hv.get(0));
                    }
                }
            } else if (location == ParamLocationEnum.REQUEST_BODY) {
                Object paramValue = bodyMap.get(name);
                if (null == paramValue) {
                    if (required) {
                        invalidArgs.add(param);
                    } else {
                        if (!isArray) {
                            map.put(name, type.getConverter().apply(defaultValue));
                        }
                    }
                } else {
                    if (isArray) {
                        List<Object> values = (paramValue instanceof List)
                                ? (List) paramValue
                                : Lists.newArrayList(paramValue);
                        if (type.isObject()) {
                            map.put(name, values);
                        } else {
                            List<Object> hv = values
                                    .stream().map(v -> type.getConverter().apply(v.toString()))
                                    .collect(Collectors.toList());
                            map.put(name, hv);
                        }
                    } else {
                        if (type.isObject()) {
                            Map<String, Object> objectMap = (paramValue instanceof Map)
                                    ? (Map<String, Object>) paramValue
                                    : new HashMap<>();
                            map.put(name, objectMap);
                        } else {
                            Object targetValue = (paramValue instanceof List)
                                    ? ((List) paramValue).get(0)
                                    : paramValue;
                            map.put(name, type.getConverter().apply(targetValue.toString()));
                        }
                    }
                }
            } else {
                if (isArray) {
                    String[] values = request.getParameterValues(name);
                    if (ArrayUtils.isNotEmpty(values)) {
                        List list = Arrays.asList(values).stream()
                                .map(v -> type.getConverter().apply(v))
                                .collect(Collectors.toList());
                        map.put(name, list);
                    } else {
                        if (required) {
                            invalidArgs.add(param);
                        }
                    }
                } else {
                    String value = request.getParameter(name);
                    if (StringUtils.isEmpty(value)) {
                        if (required) {
                            invalidArgs.add(param);
                        } else {
                            map.put(name, type.getConverter().apply(defaultValue));
                        }
                    } else {
                        map.put(name, type.getConverter().apply(value));
                    }
                }
            }
        }
        return map;
    }

    public Map<String, Object> getRequestBodyMap(HttpServletRequest request) throws IOException {
        HttpMethodEnum methodEnum = HttpMethodEnum.valueOf(request.getMethod());
        if (methodEnum.isHasBody() && null != request.getContentType()) {
            MediaType contentType = MediaType.parseMediaType(request.getContentType());
            Charset charset = (contentType != null && contentType.getCharset() != null ?
                    contentType.getCharset() : StandardCharsets.UTF_8);
            for (HttpRequestBodyExtractor bodyExtractor : requestBodyExtractors) {
                if (bodyExtractor.support(contentType)) {
                    return bodyExtractor.read(charset, request.getInputStream());
                }
            }
        }
        return Collections.emptyMap();
    }
}
