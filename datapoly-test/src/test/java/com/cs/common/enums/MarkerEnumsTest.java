// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The plain marker enums carry no behaviour; the assertions pin their constant sets so an
 * accidental rename or removal fails fast instead of surfacing in persisted data.
 */
public class MarkerEnumsTest {

    @Test
    public void testExecuteEngineSurface() {
        assertArrayEquals(new ExecuteEngineEnum[]{ExecuteEngineEnum.SQL, ExecuteEngineEnum.SCRIPT,
                ExecuteEngineEnum.GRAPHQL}, ExecuteEngineEnum.values());
        assertSame(ExecuteEngineEnum.GRAPHQL, ExecuteEngineEnum.valueOf("GRAPHQL"));
    }

    @Test
    public void testNodeStatusSurface() {
        assertArrayEquals(new NodeStatusEnum[]{NodeStatusEnum.normal, NodeStatusEnum.warning},
                NodeStatusEnum.values());
        assertSame(NodeStatusEnum.normal, NodeStatusEnum.valueOf("normal"));
    }

    @Test
    public void testOnOffSurface() {
        assertArrayEquals(new OnOffEnum[]{OnOffEnum.ON, OnOffEnum.OFF}, OnOffEnum.values());
        assertSame(OnOffEnum.OFF, OnOffEnum.valueOf("OFF"));
    }

    @Test
    public void testWhiteBlackSurface() {
        assertArrayEquals(new WhiteBlackEnum[]{WhiteBlackEnum.WHITE, WhiteBlackEnum.BLACK},
                WhiteBlackEnum.values());
        assertSame(WhiteBlackEnum.BLACK, WhiteBlackEnum.valueOf("BLACK"));
    }
}
