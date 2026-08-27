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
@ApiModel("枚举键值")
public class NameValueBaseResponse {

    @ApiModelProperty("枚举至")
    private String key;

    @ApiModelProperty("说明")
    private String value;
}
