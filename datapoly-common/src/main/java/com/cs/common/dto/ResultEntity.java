// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.cs.common.exception.ResponseErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

@Schema(description = "响应结果")
@AllArgsConstructor
@Data
public class ResultEntity<T> implements Serializable {

    private static final String SUCCESS = "success";

    @Schema(description = "状态码")
    private Integer code;

    @Schema(description = "状态描述")
    private String message;

    @Schema(description = "数据")
    private T data;

    public static <T> ResultEntity success() {
        return new ResultEntity(ResponseErrorCode.SUCCESS.getCode(), SUCCESS, null);
    }

    public static <T> ResultEntity success(T data) {
        return new ResultEntity<>(ResponseErrorCode.SUCCESS.getCode(), SUCCESS, data);
    }

    public static ResultEntity failed(ResponseErrorCode code) {
        return new ResultEntity(code.getCode(), code.getMessage(), null);
    }

    public static ResultEntity failed(ResponseErrorCode code, String message) {
        return new ResultEntity(code.getCode(), code.getMessage() + "," + message, null);
    }

    public static ResultEntity failed(String message) {
        return new ResultEntity(ResponseErrorCode.ERROR_INTERNAL_ERROR.getCode(), message, null);
    }
}
