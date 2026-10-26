// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.util.CacheUtils;
import com.cs.common.util.TokenUtils;
import com.cs.persistence.dao.LoginTokenDao;
import com.cs.persistence.entity.LoginTokenEntity;
import com.cs.persistence.entity.SystemUserEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * Manager login sessions: issued by the password login and by any account provider that
 * authenticates outside DataPoly (the Feishu extension), resolved by the manager's
 * authentication interceptor. A session lives on the {@code DATAPOLY_LOGIN_TOKEN} row, so a
 * process restart no longer logs everyone out, and it expires absolutely: issued_at plus
 * {@code datapoly.security.login-token-ttl-seconds} (7 days by default) — activity never
 * extends it.
 *
 * <p>The in-memory {@link CacheUtils} entry is a read-through cache of that row, never the
 * authority: it carries the row's deadline, so an entry that outlives it (the cache's idle
 * window is 2h, which is longer than a short configured TTL) is rejected and evicted on sight.
 * Whether a session is still valid is decided here and nowhere else; callers get the identity
 * only, and the authoritative account row (lock flag, role) is still re-read per request.</p>
 *
 * <p>Deleting the row ends the session: logout, a password rotation, or the expiry sweep.
 * Deleting it in one place is enough — there is no second judgment to keep in sync.</p>
 */
@Slf4j
@Service
public class LoginSessionService {

    /** 7 days, the shipped lifetime of a manager login. Mirrored in the property default below. */
    public static final long DEFAULT_TTL_SECONDS = 604800L;
    private static final String TTL_PROPERTY = "datapoly.security.login-token-ttl-seconds";

    private final LoginTokenDao loginTokenDao;
    private final long ttlSeconds;

    public LoginSessionService(LoginTokenDao loginTokenDao,
                               @Value("${datapoly.security.login-token-ttl-seconds:604800}") long ttlSeconds) {
        this.loginTokenDao = loginTokenDao;
        if (ttlSeconds <= 0) {
            // Guard against a misconfigured value; without a positive lifetime every login
            // would be born expired, which looks like a broken login rather than a typo
            log.warn("Property [{}] resolved to [{}]; falling back to {} seconds",
                    TTL_PROPERTY, ttlSeconds, DEFAULT_TTL_SECONDS);
            ttlSeconds = DEFAULT_TTL_SECONDS;
        }
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Opens a session for an authenticated account and returns its token: the row first (that is
     * what survives a restart), then the in-memory entry.
     */
    public String issue(SystemUserEntity user) {
        String accessToken = TokenUtils.generateValue();
        long currentTimestamp = currentTimestamp();
        long expireAt = currentTimestamp + ttlSeconds;
        loginTokenDao.insert(LoginTokenEntity.builder()
                .accessToken(accessToken)
                .username(user.getUsername())
                .createAt(currentTimestamp)
                .expireAt(expireAt)
                .build());
        CacheUtils.put(accessToken, new LoginSession(user.getUsername(), expireAt));
        return accessToken;
    }

    /**
     * The session behind a token, or {@code null} when there is none (unknown, revoked, expired).
     * Cache first, persistence on miss — which is exactly the path a restart takes.
     */
    public LoginSession resolve(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return null;
        }
        long currentTimestamp = currentTimestamp();
        Object cached = CacheUtils.get(accessToken);
        if (cached instanceof LoginSession) {
            LoginSession session = (LoginSession) cached;
            if (session.getExpireAt() <= currentTimestamp) {
                // The in-memory entry can outlive the row it mirrors; the row's deadline wins
                CacheUtils.remove(accessToken);
                return null;
            }
            return session;
        }
        if (cached instanceof SystemUserEntity) {
            // Compatibility: an account provider built before this change cached the user row
            // under the token itself. Honored as a session whose lifetime is the in-memory
            // cache's, so a mixed-version deployment keeps working; the next login issues a
            // row again.
            return new LoginSession(((SystemUserEntity) cached).getUsername(), Long.MAX_VALUE);
        }
        if (accessToken.length() != TokenUtils.getTokenStringLength()) {
            // Not a token this service could ever have issued: skip the database round trip
            return null;
        }
        LoginTokenEntity entity = loginTokenDao.findByToken(accessToken);
        if (null == entity) {
            return null;
        }
        if (null == entity.getExpireAt() || entity.getExpireAt() <= currentTimestamp) {
            loginTokenDao.deleteByToken(accessToken);
            return null;
        }
        LoginSession session = new LoginSession(entity.getUsername(), entity.getExpireAt());
        CacheUtils.put(accessToken, session);
        return session;
    }

    /** Ends one session (logout, or a session whose ticket never went out). */
    public void revoke(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return;
        }
        CacheUtils.remove(accessToken);
        loginTokenDao.deleteByToken(accessToken);
    }

    /** Ends every session of an account (a password reset, or a rotation by the account owner). */
    public void revokeAll(String username) {
        revokeAll(username, null);
    }

    /**
     * Ends every session of an account except {@code keepToken} — used when a user rotates their
     * own password, which must drop the other sessions but not the one making the request.
     */
    public void revokeAll(String username, String keepToken) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        loginTokenDao.deleteByUsername(username, keepToken);
        // The cache is keyed by token, so the entries of this account are found by scanning it
        for (Map.Entry<String, Object> entry : CacheUtils.getAll().entrySet()) {
            if (ownedBy(entry.getValue(), username) && !Objects.equals(entry.getKey(), keepToken)) {
                CacheUtils.remove(entry.getKey());
            }
        }
    }

    /** The lifetime every newly issued session gets, in seconds (also the login response's value). */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    private static boolean ownedBy(Object cached, String username) {
        if (cached instanceof LoginSession) {
            return username.equals(((LoginSession) cached).getUsername());
        }
        if (cached instanceof SystemUserEntity) {
            return username.equals(((SystemUserEntity) cached).getUsername());
        }
        return false;
    }

    private static long currentTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * What a token resolves to: who the caller is and when the session dies. Deliberately not
     * the account row — that stays a per-request read, so locks and role changes keep applying
     * to sessions that are still open.
     */
    public static final class LoginSession {

        private final String username;
        private final long expireAt;

        LoginSession(String username, long expireAt) {
            this.username = username;
            this.expireAt = expireAt;
        }

        public String getUsername() {
            return username;
        }

        /** Absolute expiry, epoch seconds. */
        public long getExpireAt() {
            return expireAt;
        }
    }
}