// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.cs.common.enums.ParamTypeEnum;
import com.cs.common.exception.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "出参信息")
public class OutParam implements Serializable {

    @Schema(description = "ID(前端生成并使用)")
    private String id;

    @Schema(description = "参数名")
    private String name;

    @Schema(description = "参数类型")
    private ParamTypeEnum type;

    @Schema(description = "是否为数组")
    private Boolean isArray;

    @Schema(description = "参数描述")
    private String remark;

    @Schema(description = "Object类型的子元素")
    private List<OutParam> children;

    public void checkValid() {
        if (StringUtils.isBlank(getName())) {
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "common.parameter.name.blank");
        }
        if (null == getType()) {
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "common.parameter.type.null");
        }

        if (getType() == ParamTypeEnum.OBJECT) {
            if (null != children && children.size() > 0) {
                for (OutParam param : children) {
                    if (StringUtils.isBlank(param.getName())) {
                        throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR,
                                "common.parameter.name.blank");
                    }
                }
            } else {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                        "Object output param '" + getName() + "' must have child parameter.");
            }
        } else {
            children = Collections.emptyList();
        }
    }
}
