// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externally configurable gateway/manager service URLs, useful when an nginx or other reverse proxy sits in front.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "datapoly.url")
public class DataPolyUrlConfiguration {

    private String gateway = "";
    private String manager = "";
}
