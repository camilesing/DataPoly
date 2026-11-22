// Use of this source code is governed by a BSD-style license
package com.cs.common.consts;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConstantsTest {

    @Test
    public void testResourceNameFormat() {
        assertEquals("/api/client[GET]", Constants.getResourceName("GET", "client"));
        assertEquals("/api/user/list[POST]", Constants.getResourceName("POST", "user/list"));
        // path is inserted verbatim, no normalization happens here
        assertEquals("/api//double[PUT]", Constants.getResourceName("PUT", "/double"));
    }

    @Test
    public void testAbstractBaseCanBeSubclassed() {
        // trivial instantiation through a subclass; only documents the type stays subclassable
        Constants instance = new Constants() {
        };
        assertNotNull(instance);
        assertEquals("/api/x[GET]", instance.getResourceName("GET", "x"));
    }
}
