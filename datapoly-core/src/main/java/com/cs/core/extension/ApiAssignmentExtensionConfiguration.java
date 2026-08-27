// Use of this source code is governed by a BSD-style license
package com.cs.core.extension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the {@link ApiAssignmentPostProcessor} extension point: Spring beans (if any)
 * are merged with {@code META-INF/services} providers into the shared composite. Host
 * applications that embed this module get the composite for free; none of the channels
 * is required, an empty composite is valid.
 */
@Configuration
public class ApiAssignmentExtensionConfiguration {

    @Bean
    public ApiAssignmentPostProcessors apiAssignmentPostProcessors(
            @Autowired(required = false) List<ApiAssignmentPostProcessor> registered,
            ApplicationEventPublisher eventPublisher) {
        return ApiAssignmentPostProcessors.aggregate(registered, eventPublisher);
    }
}
