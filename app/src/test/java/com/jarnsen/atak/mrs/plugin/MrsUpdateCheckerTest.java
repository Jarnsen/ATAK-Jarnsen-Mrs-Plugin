package com.jarnsen.atak.mrs.plugin;

import org.junit.Test;
import org.json.JSONObject;

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

    @Test
    public void releaseUsesOnlyTheTakgovSignedPluginApkAsset() throws Exception {
        String checksum = "ab".repeat(32);
        JSONObject release = new JSONObject(
                "{\"tag_name\":\"v0.4.7\","
                        + "\"html_url\":\"https://github.com/Jarnsen/"
                        + "ATAK-Jarnsen-Mrs-Plugin/releases/tag/v0.4.7\","
                        + "\"assets\":["
                        + "{\"name\":\"debug.apk\","
                        + "\"browser_download_url\":\"https://github.com/"
                        + "Jarnsen/ATAK-Jarnsen-Mrs-Plugin/releases/download/"
                        + "v0.4.7/debug.apk\","
                        + "\"digest\":\"sha256:" + checksum + "\"},"
                        + "{\"name\":\"ATAK-Plugin-Jarnsen-Mrs-0.4.7-"
                        + "5.6.0-TAKgov.apk\","
                        + "\"browser_download_url\":\"https://github.com/"
                        + "Jarnsen/ATAK-Jarnsen-Mrs-Plugin/releases/download/"
                        + "v0.4.7/ATAK-Plugin-Jarnsen-Mrs-0.4.7-"
                        + "5.6.0-TAKgov.apk\","
                        + "\"digest\":\"sha256:" + checksum + "\"}]}"
        );

        MrsUpdateChecker.Result result = MrsUpdateChecker.parseRelease(
                release,
                "0.4.6"
        );

        assertTrue(result.updateAvailable);
        assertEquals("0.4.7", result.latestVersion);
        assertEquals(
                "ATAK-Plugin-Jarnsen-Mrs-0.4.7-5.6.0-TAKgov.apk",
                result.apkFileName
        );
        assertEquals(checksum, result.apkSha256);
    }
}
