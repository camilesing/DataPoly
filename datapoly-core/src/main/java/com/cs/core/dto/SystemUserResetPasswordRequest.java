// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "重置用户口令（该账号全部会话随之吊销）")
public class SystemUserResetPasswordRequest {

    @Schema(description = "登陆名")
    @NotBlank(message = "username不能为空")
    private String username;

    @Schema(description = "新密码（至少8位）")
    @NotBlank(message = "newPassword不能为空")
    @Size(min = 8, max = 64, message = "newPassword长度须为8-64位")
    private String newPassword;
}
