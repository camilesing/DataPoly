// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "一个模块的权限（有效矩阵或原始配置行）")
public class UserPermissionResponse {

    @Schema(description = "模块名（PermissionModule）")
    private String module;

    @Schema(description = "操作集合（R/C/U/D/X 的子集）")
    private List<String> actions;

    @Schema(description = "是否存在配置行（false=未配置，走默认只读）")
    private Boolean customized;
}
