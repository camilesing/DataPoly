// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@ApiModel("账号登陆请求")
public class UserLoginRequest {

    @NotBlank(message = "username不能为空")
    @ApiModelProperty("账号")
    private String username;

    @NotBlank(message = "password不能为空")
    @ApiModelProperty("密码")
    private String password;
}
