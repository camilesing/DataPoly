// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "更新系统用户（省略的项不动；角色/锁定另有服务端保护）")
public class SystemUserUpdateRequest {

    @Schema(description = "用户ID")
    @NotNull(message = "id不能为空")
    private Long id;

    @Schema(description = "实际名（省略不动）")
    private String realName;

    @Schema(description = "电子邮箱（省略不动）")
    private String email;

    @Schema(description = "地址（省略不动）")
    private String address;

    @Schema(description = "角色（省略不动）")
    @Pattern(regexp = "ADMIN|USER", message = "role仅支持ADMIN或USER")
    private String role;

    @Schema(description = "是否锁定（省略不动）")
    private Boolean locked;
}
