// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "带说明的枚举键值")
public class NameValueRemarkResponse extends NameValueBaseResponse {

    @Schema(description = "注释")
    private String remark;
}
