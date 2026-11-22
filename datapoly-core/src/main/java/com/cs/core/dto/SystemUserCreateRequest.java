// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "创建系统用户")
public class SystemUserCreateRequest {

    @Schema(description = "登陆名（字母/数字/下划线，3-64）")
    @NotBlank(message = "username不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,64}$", message = "username仅支持字母、数字、下划线，长度3-64")
    private String username;

    @Schema(description = "初始密码（至少8位）")
    @NotBlank(message = "password不能为空")
    @Size(min = 8, max = 64, message = "password长度须为8-64位")
    private String password;

    @Schema(description = "实际名")
    private String realName;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "角色（ADMIN/USER）")
    @NotBlank(message = "role不能为空")
    @Pattern(regexp = "ADMIN|USER", message = "role仅支持ADMIN或USER")
    private String role;
}
