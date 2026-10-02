package com.jarnsen.atak.mrs.plugin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MrsUpdateCheckerTest {

    @Test
    public void versionComparisonIsSemanticEnoughForPluginReleases() {
        assertTrue(MrsUpdateChecker.compareVersions("0.4.1", "0.4.0") > 0);
        assertTrue(MrsUpdateChecker.compareVersions("1.0.0", "0.9.9") > 0);
        assertEquals(0, MrsUpdateChecker.compareVersions("v0.4.0", "0.4.0"));
    }

    @Test
    public void buildSuffixIsIgnored() {
        assertEquals(
                "0.4.0",
                MrsUpdateChecker.stripVersionPrefix(
                        "0.4.0 (civ) - [5.6.0]"
                )
        );
    }
}
