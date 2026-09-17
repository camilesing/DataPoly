// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import java.lang.reflect.Field;

/** Minimal reflection injection helper mirroring the hand-written-fake test style used across this repo. */
public final class DataTaskTestSupport {

    private DataTaskTestSupport() {
    }

    public static void setField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("failed injecting field '" + name + "'", e);
            }
        }
        throw new IllegalStateException("no field '" + name + "' on " + target.getClass().getName());
    }
}
