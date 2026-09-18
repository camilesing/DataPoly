// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import org.junit.*;

public class SqlTemplateGuardTest {

    @Test
    public void defaultsForbidDollarOnlyForOpenApis() {
        // Defaults (dollar-enabled=true, open-dollar-forbidden=true)
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE, true, true));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(null, true, true));
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE, true, true));
    }

    @Test
    public void globalSwitchDisablesAll() {
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE, false, true));
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE, false, true));
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(null, false, false));
    }

    @Test
    public void openForbiddenSwitchCanBeRelaxed() {
        // With open enforcement disabled, public APIs are also allowed (escape hatch restoring the old unguarded behavior)
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE, true, false));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE, true, false));
    }

    @Test
    public void noSpringContextFallsBackToSafeDefaults() {
        // Plain unit test without a Spring container: Environment read failure falls back to defaults (true/true),
        // i.e. public APIs rejected, non-public APIs allowed
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(null));
    }
}
