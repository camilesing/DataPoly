// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.DataTypeFormatEnum;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTypeFormatMapValue {

    private DataTypeFormatEnum key;
    private String value;
    private String remark;
}
