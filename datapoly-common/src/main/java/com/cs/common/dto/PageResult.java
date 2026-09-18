// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;
import java.util.List;

@Schema(description = "分页结果")
@NoArgsConstructor
@Data
public class PageResult<E> implements Serializable {

    @Schema(description = "状态码")
    private Integer code = 0;

    @Schema(description = "状态描述")
    private String message = "success";

    @Schema(description = "分页信息")
    private Pagination pagination;

    @Schema(description = "数据")
    private List<E> data;

    @Schema(description = "分页结果")
    @NoArgsConstructor
    @Data
    public static class Pagination {

        @Schema(description = "页码")
        private int page;

        @Schema(description = "记录总数")
        private int total;

        @Schema(description = "每页大小")
        private int size;
    }

}
