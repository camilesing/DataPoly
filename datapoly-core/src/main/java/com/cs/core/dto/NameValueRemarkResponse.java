// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("带说明的枚举键值")
public class NameValueRemarkResponse extends NameValueBaseResponse {

    @ApiModelProperty("注释")
    private String remark;
}
