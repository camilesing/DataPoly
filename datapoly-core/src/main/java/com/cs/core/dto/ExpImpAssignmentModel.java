// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.dto.*;
import com.cs.common.enums.*;
import lombok.Data;

import java.util.*;

@Data
public class ExpImpAssignmentModel {

    private String groupName;

    private String moduleName;

    private ExpImpDataSourceModel dataSourceModel;

    private String name;

    private String description;

    private HttpMethodEnum method;

    private String path;

    private List<ItemParam> params;

    private List<OutParam> outputs;

    private Boolean open;

    private Boolean alarm;

    private String contentType;

    private ExecuteEngineEnum engine;

    private Map<DataTypeFormatEnum, String> responseFormat;

    private NamingStrategyEnum namingStrategy;

    private Boolean flowStatus;

    private Integer flowGrade;

    private Integer flowCount;

    private CacheKeyTypeEnum cacheKeyType;

    private String cacheKeyExpr;

    private Long cacheExpireSeconds;

    private List<String> contextList;
}
