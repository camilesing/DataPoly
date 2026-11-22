// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "整表替换一个用户的权限配置行（actions 为空 = 该模块回到默认只读）")
public class UserPermissionSaveRequest {

    @Schema(description = "登陆名")
    @NotBlank(message = "username不能为空")
    private String username;

    @Schema(description = "权限行")
    @Valid
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @Schema(description = "一个模块的授权")
    public static class Item {

        @Schema(description = "模块名（PermissionModule）")
        @NotBlank(message = "module不能为空")
        private String module;

        @Schema(description = "操作集合（R/C/U/D/X）")
        private List<String> actions;
    }
}
