// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("模块接口树")
public class ApiModuleAssignments extends EntityIdNameResponse {

    @ApiModelProperty("接口列表")
    private List<SelectedEntityIdName> children;

}
