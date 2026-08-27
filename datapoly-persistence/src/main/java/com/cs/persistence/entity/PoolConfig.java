// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import lombok.*;

import java.io.Serializable;

/**
 * Connection pool configuration (stored as JSON)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoolConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final PoolConfig DEFAULT = PoolConfig.builder()
            .maximumPoolSize(10)
            .minimumIdle(10)
            .maxLifetime(3600000L)
            .connectionTimeout(60000L)
            .idleTimeout(60000)
            .build();

    /**
     * Maximum pool size
     */
    private Integer maximumPoolSize;

    /**
     * Minimum idle connections
     */
    private Integer minimumIdle;

    /**
     * Maximum connection lifetime (ms)
     */
    private Long maxLifetime;

    /**
     * Connection timeout (ms)
     */
    private Long connectionTimeout;

    /**
     * Idle timeout (ms)
     */
    private Integer idleTimeout;
}
