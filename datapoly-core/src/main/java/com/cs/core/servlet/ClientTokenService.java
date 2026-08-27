// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import com.cs.cache.CacheFactory;
import com.cs.common.consts.Constants;
import com.cs.common.dto.AccessToken;
import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.common.util.TokenUtils;
import com.cs.core.exec.ExecutorMetadataCache;
import com.cs.persistence.dao.AppClientDao;
import com.cs.persistence.entity.AppClientEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.*;

@Slf4j
@Service
public class ClientTokenService {

    @Resource
    private AppClientDao appClientDao;
    @Resource
    private CacheFactory cacheFactory;
    @Resource
    private ExecutorMetadataCache executorMetadataCache;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        long currentTimestamp = getCurrentTimestamp();
        Map<String, AccessToken> tokenClientMap = cacheFactory
                .getCacheMap(Constants.CACHE_KEY_TOKEN_CLIENT, AccessToken.class);
        try {
            for (AppClientEntity appClient : appClientDao.listAll()) {
                appClient.setAppSecret("******");
                if (StringUtils.isNotBlank(appClient.getAccessToken())) {
                    if (isTokenExpired(appClient, currentTimestamp)) {
                        log.info("Remove expired client token from persistence, appKey:{}", appClient.getAppKey());
                        appClientDao.clearTokenByAppKey(appClient.getAppKey());
                        continue;
                    }

                    if (isOneTimeToken(appClient)) {
                        log.debug("Skip restoring one-time token for appKey:{}", appClient.getAppKey());
                        continue;
                    }

                    AccessToken clientToken = buildAccessTokenFromPersistence(appClient, currentTimestamp);
                    log.info("Load client app token from persistence, appKey:{}", appClient.getAppKey());
                    tokenClientMap.put(appClient.getAccessToken(), clientToken);
                }
            }
            log.info("Finish load client app token from persistence.");
        } catch (Exception e) {
            log.error("load client app token failed:{}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AccessToken generateToken(String clientId, String clientSecret) {
        AppClientEntity appClient = appClientDao.getByAppKey(clientId);
        if (null == appClient) {
            throw new CommonException(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, "client.id.invalid");
        }
        if (!isEqualsConstantTime(appClient.getAppSecret(), clientSecret)) {
            throw new CommonException(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, "client.secret.invalid");
        }
        if (DurationTimeEnum.TIME_VALUE == appClient.getExpireDuration()) {
            Boolean isExpired = getCurrentTimestamp() > appClient.getExpireAt();
            if (isExpired) {
                throw new CommonException(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, "client.id.expired");
            }
        } else if (DurationTimeEnum.ONLY_ONCE == appClient.getExpireDuration()) {
            if (!appClient.getCreateTime().equals(appClient.getUpdateTime())) {
                throw new CommonException(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, "client.id.expired");
            }
        }

        String token = TokenUtils.generateValue();
        Long createTimestamp = getCurrentTimestamp();
        if (AliveTimeEnum.LONGEVITY.equals(appClient.getTokenAlive())
                && StringUtils.isNotBlank(appClient.getAccessToken())) {
            token = appClient.getAccessToken();
            createTimestamp = toEpochSeconds(appClient.getUpdateTime());
        }

        AccessToken clientToken = AccessToken.builder()
                .realName(appClient.getName())
                .appKey(clientId)
                .accessToken(token)
                .createTimestamp(createTimestamp)
                .build();
        clientToken.setExpireSeconds(resolveExpireSeconds(appClient, createTimestamp, getCurrentTimestamp()));
        if (clientToken.getExpireSeconds() == 0L && DurationTimeEnum.ONLY_ONCE != appClient.getExpireDuration()) {
            // Guard against a custom config of 0 seconds; keep a minimum fallback period
            clientToken.setExpireSeconds(Constants.CLIENT_TOKEN_DURATION_SECONDS);
        }

        // Persist the token to the database so it remains valid after a server restart
        if (!isEqualsConstantTime(token, appClient.getAccessToken())) {
            appClientDao.updateTokenByAppKey(clientId, token);
        }

        Map<String, AccessToken> tokenClientMap = cacheFactory
                .getCacheMap(Constants.CACHE_KEY_TOKEN_CLIENT, AccessToken.class);
        if (clientToken.getExpireSeconds() == 0L) {
            tokenClientMap.remove(token);
        } else {
            tokenClientMap.put(token, clientToken);
        }

        return clientToken;
    }

    public String verifyTokenAndGetAppKey(String tokenStr) {
        if (StringUtils.isBlank(tokenStr)) {
            return null;
        }
        long currentTimestamp = getCurrentTimestamp();
        Map<String, AccessToken> tokenClientMap = cacheFactory
                .getCacheMap(Constants.CACHE_KEY_TOKEN_CLIENT, AccessToken.class);
        AccessToken clientToken = tokenClientMap.get(tokenStr);
        if (null == clientToken) {
            if (tokenStr.length() == TokenUtils.getTokenStringLength()) {
                AppClientEntity appClient = appClientDao.getByAccessToken(tokenStr);
                if (null == appClient) {
                    return null;
                }
                if (isTokenExpired(appClient, currentTimestamp)) {
                    appClientDao.clearTokenByAppKey(appClient.getAppKey());
                    return null;
                }

                clientToken = buildAccessTokenFromPersistence(appClient, currentTimestamp);
                if (!isOneTimeToken(appClient)) {
                    tokenClientMap.put(tokenStr, clientToken);
                }
                return clientToken.getAppKey();
            }
            return null;
        }
        long durationTimestamp = currentTimestamp - clientToken.getCreateTimestamp();
        long expireTimestamp = clientToken.getExpireSeconds();
        if (expireTimestamp <= 0) {
            if (0 == expireTimestamp) {
                // One-shot application client
                tokenClientMap.remove(tokenStr);
                appClientDao.clearTokenByAppKey(clientToken.getAppKey());
                log.warn("token [{}] only can use once, clientId: {}", tokenStr, clientToken.getAppKey());
            } else {
                // Long-lived application client using a long-term token
                return clientToken.getAppKey();
            }
        } else if (durationTimestamp > expireTimestamp) {
            log.warn("token [{}] expired, clientId: {}", tokenStr, clientToken.getAppKey());
            return null;
        }
        return clientToken.getAppKey();
    }

    /**
     * Auth-group checks go through the local short-TTL cache (A2): grants/revocations take effect within one TTL at most (10s by default,
     * can be disabled via datapoly.executor.metadata-cache.auth-group-enabled to restore immediate effect).
     */
    public boolean verifyAuthGroup(String clientId, Long groupId) {
        return executorMetadataCache.getAuthGroup(clientId, groupId,
                () -> appClientDao.existsAuthGroups(clientId, groupId));
    }

    /**
     * Constant-time string comparison (S4): avoids timing side channels in secret/token comparisons.
     */
    private boolean isEqualsConstantTime(String value1, String value2) {
        if (null == value1 || null == value2) {
            return value1 == value2;
        }
        return MessageDigest.isEqual(
                value1.getBytes(StandardCharsets.UTF_8),
                value2.getBytes(StandardCharsets.UTF_8));
    }

    private long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    private boolean isTokenExpired(AppClientEntity appClient, long currentTimestamp) {
        Long expireAt = appClient.getExpireAt();
        if (Objects.nonNull(expireAt) && expireAt > 0) {
            return expireAt <= currentTimestamp;
        }
        if (DurationTimeEnum.TIME_VALUE == appClient.getExpireDuration()) {
            return true;
        }
        return false;
    }

    private boolean isOneTimeToken(AppClientEntity appClient) {
        return DurationTimeEnum.ONLY_ONCE == appClient.getExpireDuration();
    }

    private AccessToken buildAccessTokenFromPersistence(AppClientEntity appClient, long currentTimestamp) {
        long createTimestamp = toEpochSeconds(appClient.getUpdateTime());
        long expireSeconds = resolveExpireSeconds(appClient, createTimestamp, currentTimestamp);
        return AccessToken.builder()
                .realName(appClient.getName())
                .appKey(appClient.getAppKey())
                .accessToken(appClient.getAccessToken())
                .createTimestamp(createTimestamp)
                .expireSeconds(expireSeconds)
                .build();
    }

    private long resolveExpireSeconds(AppClientEntity appClient, long createTimestamp, long currentTimestamp) {
        Long expireAt = appClient.getExpireAt();
        if (Objects.nonNull(expireAt)) {
            if (expireAt > 0) {
                long secondsLeft = expireAt - currentTimestamp;
                if (secondsLeft <= 0) {
                    return 0L;
                }
                if (AliveTimeEnum.LONGEVITY.equals(appClient.getTokenAlive())) {
                    long basedOnCreate = expireAt - createTimestamp;
                    return Math.max(basedOnCreate, 0L);
                }
                return Math.min(secondsLeft, appClient.getTokenAlive().getValue());
            }
            if (expireAt == 0) {
                return 0L;
            }
        }

        if (AliveTimeEnum.LONGEVITY.equals(appClient.getTokenAlive())) {
            return -1L;
        }
        return appClient.getTokenAlive().getValue();
    }

    private long toEpochSeconds(Timestamp timestamp) {
        if (timestamp == null) {
            return getCurrentTimestamp();
        }
        return timestamp.getTime() / 1000;
    }
}
