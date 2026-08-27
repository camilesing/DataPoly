// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.config;

import org.apache.ibatis.mapping.*;
import org.springframework.context.annotation.*;

import java.util.Properties;

@Configuration
public class MybatisDatabaseConfiguration {

    @Bean
    public DatabaseIdProvider getDatabaseIdProvider() {
        DatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties props = new Properties();
        props.setProperty("PostgreSQL", "postgresql");
        props.setProperty("MySQL", "mysql");
        databaseIdProvider.setProperties(props);
        return databaseIdProvider;
    }
}
