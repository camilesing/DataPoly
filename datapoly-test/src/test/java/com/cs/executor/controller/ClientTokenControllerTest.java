// Use of this source code is governed by a BSD-style license
package com.cs.executor.controller;

import com.cs.common.dto.AccessToken;
import com.cs.common.dto.ResultEntity;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.servlet.ClientTokenGuard;
import com.cs.core.servlet.ClientTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * /token/generate controller: guard integration on the happy path, guard rejection (429),
 * credential failures (403) and unexpected failures (500), each with the matching HTTP
 * status and ResultEntity error code plus guard bookkeeping.
 */
public class ClientTokenControllerTest {

    private ClientTokenController controller;
    private StubTokenService tokenService;
    private StubGuard guard;
    private final Integer[] statusRef = new Integer[]{null};
    private final List<Integer> statusesSet = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        controller = new ClientTokenController();
        tokenService = new StubTokenService();
        guard = new StubGuard();
        statusRef[0] = null;
        statusesSet.clear();
        inject("clientTokenService", tokenService);
        inject("clientTokenGuard", guard);
    }

    @Test
    public void successfulGenerationReturnsTokenAndRecordsSuccess() {
        AccessToken token = AccessToken.builder().appKey("client-1").accessToken("tk").build();
        tokenService.token = token;

        ResultEntity result = controller.generateToken(body("client-1", "sec"), request("10.1.2.3"), response());

        assertEquals(Integer.valueOf(0), result.getCode());
        assertSame(token, result.getData());
        assertTrue("no error status must be set on success", statusesSet.isEmpty());
        assertEquals(Collections.singletonList("ok:client-1@10.1.2.3"), guard.events);
        assertEquals("client-1", tokenService.lastClientId);
        assertEquals("sec", tokenService.lastSecret);
    }

    @Test
    public void guardRejectionReturns429WithoutTouchingTokenService() {
        guard.checkAllowedFailure = new CommonException(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, "token.rate.limited");

        ResultEntity result = controller.generateToken(body("client-1", "sec"), request("10.1.2.3"), response());

        assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS.getCode()), result.getCode());
        assertEquals(Collections.singletonList(Integer.valueOf(429)), statusesSet);
        assertNull(tokenService.lastClientId);
        assertTrue("neither success nor failure bookkeeping happens when the guard rejects",
                guard.events.isEmpty());
    }

    @Test
    public void invalidCredentialsReturn403AndRecordFailure() {
        tokenService.generateFailure = new CommonException(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, "client.secret.invalid");

        ResultEntity result = controller.generateToken(body("client-1", "bad"), request("10.1.2.3"), response());

        assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN.getCode()), result.getCode());
        assertEquals(Collections.singletonList(Integer.valueOf(403)), statusesSet);
        assertEquals(Collections.singletonList("fail:client-1@10.1.2.3"), guard.events);
    }

    @Test
    public void unexpectedFailureReturns500AndRecordsFailure() {
        tokenService.generateFailure = new IllegalStateException("boom");

        ResultEntity result = controller.generateToken(body("client-1", "sec"), request("10.1.2.3"), response());

        assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_INTERNAL_ERROR.getCode()), result.getCode());
        assertEquals(Collections.singletonList(Integer.valueOf(500)), statusesSet);
        assertEquals(Collections.singletonList("fail:client-1@10.1.2.3"), guard.events);
    }

    @Test
    public void missingClientIdIsPassedThroughAndFailsClosed() {
        tokenService.generateFailure = new CommonException(ResponseErrorCode.ERROR_CLIENT_FORBIDDEN, "client.id.invalid");

        ResultEntity result = controller.generateToken(new HashMap<>(), request("10.1.2.3"), response());

        assertNull(tokenService.lastClientId);
        assertEquals(Integer.valueOf(403), result.getCode());
        assertEquals(Collections.singletonList("fail:null@10.1.2.3"), guard.events);
    }

    private Map<String, String> body(String clientId, String secret) {
        Map<String, String> map = new HashMap<>();
        map.put("clientId", clientId);
        map.put("secret", secret);
        return map;
    }

    private HttpServletRequest request(String remoteAddr) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (p, m, a) -> "getRemoteAddr".equals(m.getName()) ? remoteAddr : null);
    }

    private HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> {
                    if ("setStatus".equals(m.getName())) {
                        statusesSet.add((Integer) a[0]);
                        statusRef[0] = (Integer) a[0];
                        return null;
                    }
                    return null;
                });
    }

    private void inject(String name, Object value) throws Exception {
        Field field = ClientTokenController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static class StubTokenService extends ClientTokenService {
        AccessToken token;
        RuntimeException generateFailure;
        String lastClientId;
        String lastSecret;

        @Override
        public AccessToken generateToken(String clientId, String clientSecret) {
            lastClientId = clientId;
            lastSecret = clientSecret;
            if (null != generateFailure) {
                throw generateFailure;
            }
            return token;
        }
    }

    private static class StubGuard extends ClientTokenGuard {
        CommonException checkAllowedFailure;
        final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void checkAllowed(String clientId, String remoteAddr) {
            if (null != checkAllowedFailure) {
                throw checkAllowedFailure;
            }
        }

        @Override
        public void recordSuccess(String clientId, String remoteAddr) {
            events.add("ok:" + clientId + "@" + remoteAddr);
        }

        @Override
        public void recordFailure(String clientId, String remoteAddr) {
            events.add("fail:" + clientId + "@" + remoteAddr);
        }
    }
}
