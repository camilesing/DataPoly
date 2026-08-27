// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.io.Serializable;
import java.util.List;

@ApiModel(description = "分页结果")
@NoArgsConstructor
@Data
public class PageResult<E> implements Serializable {

    @ApiModelProperty("状态码")
    private Integer code = 0;

    @ApiModelProperty("状态描述")
    private String message = "success";

    @ApiModelProperty("分页信息")
    private Pagination pagination;

    @ApiModelProperty("数据")
    private List<E> data;

    @ApiModel(description = "分页结果")
    @NoArgsConstructor
    @Data
    public static class Pagination {

        @ApiModelProperty("页码")
        private int page;

        @ApiModelProperty("记录总数")
        private int total;

        @ApiModelProperty("每页大小")
        private int size;
    }

}
