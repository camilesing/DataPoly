// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.cs.common.enums.*;
import com.cs.common.exception.*;
import io.swagger.annotations.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("入参信息")
public class ItemParam extends BaseParam {

    @ApiModelProperty("Object类型的子元素")
    private List<BaseParam> children;

    public void checkValid(HttpMethodEnum method) {
        if (StringUtils.isBlank(getName())) {
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "common.parameter.name.blank");
        }
        if (getType() == ParamTypeEnum.OBJECT) {
            if (!method.isHasBody()) {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                        "api.get.head.no.body");
            }
            if (null != children && children.size() > 0) {
                for (BaseParam param : children) {
                    if (StringUtils.isBlank(param.getName())) {
                        throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "common.parameter.name.blank");
                    }
                }
            } else {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                        "Object input param '" + getName() + "' must have child parameter.");
            }
        } else {
            children = Collections.emptyList();
        }
    }
}
