// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.persistence.dao.LoginTokenDao;
import com.cs.persistence.entity.LoginTokenEntity;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory stand-in for the DATAPOLY_LOGIN_TOKEN table: enough of the DAO's semantics for tests
 * that need sessions without a database. It counts lookups too, so a test can prove a request was
 * served from the in-memory cache rather than the row.
 */
class InMemoryLoginTokenDao extends LoginTokenDao {

    private final Map<String, LoginTokenEntity> rows = new LinkedHashMap<>();

    private int lookups;

    int lookups() {
        return lookups;
    }

    /** The persisted row of a token, or {@code null} — what a restart would still find. */
    LoginTokenEntity row(String accessToken) {
        return rows.get(accessToken);
    }

    @Override
    public LoginTokenEntity findByToken(String accessToken) {
        lookups++;
        return rows.get(accessToken);
    }

    @Override
    public void insert(LoginTokenEntity loginToken) {
        rows.put(loginToken.getAccessToken(), loginToken);
    }

    @Override
    public void deleteByToken(String accessToken) {
        rows.remove(accessToken);
    }

    @Override
    public void deleteByUsername(String username, String keepToken) {
        rows.values().removeIf(row -> username.equals(row.getUsername())
                && !StringUtils.equals(row.getAccessToken(), keepToken));
    }
}