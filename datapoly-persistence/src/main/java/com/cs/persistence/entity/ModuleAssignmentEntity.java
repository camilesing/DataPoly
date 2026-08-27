// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleAssignmentEntity {

    private Long moduleId;
    private String moduleName;
    private Long assigmentId;
    private String assigmentName;
    private Long groupId;
}
