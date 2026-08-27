// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.ProductTypeEnum;
import lombok.Data;

@Data
public class ExpImpDataSourceModel {

    private String name;

    private ProductTypeEnum type;

    private String version;

    private String driver;

    private String url;

    private String username;

    private String password;
}
