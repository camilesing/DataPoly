// Use of this source code is governed by a BSD-style license
package com.cs.core.extension;

/**
 * Post-extension point for API assignment management operations, currently hooking the
 * debug execution ({@link #postDebug}) and configuration update ({@link #postUpdate})
 * flows of {@code ApiAssignmentService}.
 *
 * <p>Providers register either as Spring beans (host application classpath, ordered by
 * {@code @Order}) or via {@code META-INF/services/com.cs.core.extension.ApiAssignmentPostProcessor};
 * a class registered both ways fires only once, with the Spring bean taking precedence.
 * Hooks run synchronously on the request thread and must stay lightweight; a throwing
 * hook is logged and swallowed, never breaking the management API flow.</p>
 */
public interface ApiAssignmentPostProcessor {

    /**
     * Invoked after a debug execution reaches a terminal state — both success and
     * failure outcomes (a failed run is reported through {@link ApiDebugPostContext}).
     *
     * @param context immutable-by-convention debug outcome, including the raw query result
     */
    default void postDebug(ApiDebugPostContext context) {
    }

    /**
     * Invoked after an assignment update has been persisted (the DAO transaction is
     * already committed; a throwing hook cannot and will not roll it back).
     *
     * @param context immutable-by-convention update snapshot (request plus saved entity)
     */
    default void postUpdate(ApiUpdatePostContext context) {
    }
}
