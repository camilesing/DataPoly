// Use of this source code is governed by a BSD-style license
package com.cs.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * Registers MyBatis-Plus TableInfo for entities so lambda wrappers (Wrappers.lambdaQuery()
 * with column references) resolve in plain unit tests, where no mapper parsing happens.
 */
public final class PersistenceTestSupport {

    private PersistenceTestSupport() {
    }

    public static void initTableInfo(Class<?>... entities) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }
}
