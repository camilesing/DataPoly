// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@Schema(description = "账号登陆请求")
public class UserLoginRequest {

    @NotBlank(message = "username不能为空")
    @Schema(description = "账号")
    private String username;

    @NotBlank(message = "password不能为空")
    @Schema(description = "密码")
    private String password;
}
