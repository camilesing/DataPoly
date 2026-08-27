// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.thread.ExecutorBuilder;
import com.cs.common.consts.Constants;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.common.exception.*;
import com.cs.common.service.FlowControlManger;
import com.cs.common.util.*;
import com.cs.core.exec.*;
import com.cs.core.exec.logger.RequestParamLogger;
import com.cs.core.executor.UnifyAlarmOpsService;
import com.cs.core.util.*;
import com.cs.core.util.ServletUtils;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.entity.*;
import com.cs.persistence.mapper.AccessRecordMapper;
import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;

@Slf4j
@Component
public class AuthenticationFilter implements Filter {

    private static final ExecutorService alarmExecutor = ExecutorBuilder.create()
            .setCorePoolSize(Runtime.getRuntime().availableProcessors())
            .setMaxPoolSize(5 * Runtime.getRuntime().availableProcessors())
            .useArrayBlockingQueue(8912)
            .setHandler(new CallerRunsPolicy())
            .build();

    @Resource
    private ApiOnlineDao apiOnlineDao;
    @Resource
    private ExecutorMetadataCache executorMetadataCache;
    @Resource
    private FlowControlManger flowControlManger;
    @Resource
    private ClientTokenService clientTokenService;
    @Resource
    private AccessRecordMapper accessRecordMapper;
    @Resource
    private UnifyAlarmOpsService unifyAlarmOpsService;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(Charsets.UTF_8.name());
        String path = request.getRequestURI().substring(Constants.API_PATH_PREFIX.length() + 2);
        HttpMethodEnum method = HttpMethodEnum.exists(request.getMethod())
                ? HttpMethodEnum.valueOf(request.getMethod().toUpperCase())
                : HttpMethodEnum.GET;
        // Local short-TTL metadata cache (A2): deploys/offlines take effect within one TTL at most; negative results (404 path) are also cached
        ApiAssignmentEntity apiConfigEntity = executorMetadataCache.getApiAssignment(method, path,
                () -> apiOnlineDao.getByUk(method, path));
        if (null == apiConfigEntity) {
            String message = String.format("/%s/%s[%s]", Constants.API_PATH_PREFIX, path, method.name());
            ResponseWriteUtils.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    ResponseErrorCode.ERROR_PATH_NOT_EXISTS, message);
            log.warn("Request path not exists: {}", message);
            return;
        }

        try {
            ApiAssignmentCache.set(apiConfigEntity);

            if (apiConfigEntity.getFlowStatus()) {
                String resourceName = Constants.getResourceName(method.name(), path);
                if (flowControlManger.checkFlowControl(resourceName, response)) {
                    doAuthenticationFilter(chain, request, response, apiConfigEntity);
                }
            } else {
                doAuthenticationFilter(chain, request, response, apiConfigEntity);
            }
        } finally {
            ApiAssignmentCache.remove();
        }
    }

    private void doAuthenticationFilter(FilterChain chain, HttpServletRequest request, HttpServletResponse response,
                                        ApiAssignmentEntity apiConfigEntity) throws IOException {
        AccessRecordEntity accessRecordEntity = AccessRecordEntity.builder()
                .path(request.getRequestURI())
                .status(HttpStatus.OK.value())
                .duration(System.currentTimeMillis())
                .ipAddr(ServletUtils.getIpAddr())
                .userAgent(ServletUtils.getUserAgent())
                .apiId(apiConfigEntity.getId())
                .executorAddr(InetUtils.getLocalIpStr())
                .gatewayAddr(request.getHeader(Constants.REQUEST_HEADER_GATEWAY_IP))
                .build();

        String path = apiConfigEntity.getPath();
        HttpMethodEnum method = apiConfigEntity.getMethod();

        try {
            if (!apiConfigEntity.getOpen()) {
                String tokenStr = TokenUtils.getRequestToken(request);
                if (StringUtils.isBlank(tokenStr)) {
                    throw new UnAuthorizedException("Need bearer token.");
                }
                String appKey = clientTokenService.verifyTokenAndGetAppKey(tokenStr);
                accessRecordEntity.setClientKey(appKey);
                if (null == appKey) {
                    log.error("Failed get app key from token [{}], maybe is invalid or expired. ", tokenStr);
                    throw new UnAuthorizedException("Invalid or Expired Token.");
                } else {
                    boolean verify = clientTokenService.verifyAuthGroup(appKey, apiConfigEntity.getGroupId());
                    if (!verify) {
                        log.error("Failed verify group from token [{}] , app key [{}].", tokenStr, appKey);
                        String message = String.format("/%s/%s[%s]", Constants.API_PATH_PREFIX, path, method.name());
                        throw new UnPermissionException(I18nUtils.getMessage("auth.no.permission", message));
                    }
                }
            }
            chain.doFilter(request, response);
        } catch (UnAuthorizedException e) {
            accessRecordEntity.setException(e.getMessage());
            accessRecordEntity.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            ResponseWriteUtils.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ResponseErrorCode.ERROR_TOKEN_EXPIRED, e.getMessage());
        } catch (UnPermissionException e) {
            accessRecordEntity.setException(e.getMessage());
            accessRecordEntity.setStatus(HttpServletResponse.SC_FORBIDDEN);
            ResponseWriteUtils.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, e.getMessage());
        } catch (CommonException e) {
            // Business exceptions map to real HTTP status codes by error code (400/404/429 etc.), no longer always 500
            accessRecordEntity.setException(e.getMessage());
            int status = e.getCode().getHttpStatus();
            accessRecordEntity.setStatus(status);
            ResponseWriteUtils.writeError(response, status, e.getCode(), e.getMessage());
        } catch (Throwable t) {
            // Raw exception/stack traces go to logs and access_record only; the response body carries a generic message (H1)
            String exception = (null != t.getMessage()) ? t.getMessage() : ExceptionUtil.stacktraceToString(t, 100);
            log.error("Internal error while processing request: {}", Constants.getResourceName(method.name(), path), t);
            accessRecordEntity.setException(exception);
            accessRecordEntity.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ResponseWriteUtils.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ResponseErrorCode.ERROR_INTERNAL_ERROR,
                    I18nUtils.getMessage("exception.ERROR_INTERNAL_ERROR"));
        } finally {
            final long accessTime = accessRecordEntity.getDuration();
            final int httpStatus = response.getStatus();
            accessRecordEntity.setDuration(System.currentTimeMillis() - accessRecordEntity.getDuration());
            // Mask sensitive values before persisting to audit (execution path unaffected)
            accessRecordEntity.setParameters(ParamMaskUtils.mask(RequestParamLogger.getAndClear()));
            alarmExecutor.submit(() -> doRecord(apiConfigEntity, accessRecordEntity, httpStatus, accessTime));
        }
    }

    private void doRecord(ApiAssignmentEntity config, AccessRecordEntity record, int status, long timestamp) {
        accessRecordMapper.insert(record);
        if (status == HttpServletResponse.SC_OK) {
            return;
        }
        if (!config.getAlarm()) {
            return;
        }

        Map<String, String> dataModel = AlarmModelUtils.getBusinessModel(config, record, timestamp);
        unifyAlarmOpsService.triggerAlarm(dataModel);
    }

    @Override
    public void destroy() {

    }
}
