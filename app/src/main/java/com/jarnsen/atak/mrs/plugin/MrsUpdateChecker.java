package com.jarnsen.atak.mrs.plugin;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class MrsUpdateChecker {

    static final String RELEASES_URL =
            "https://github.com/Jarnsen/ATAK-Jarnsen-Mrs-Plugin/releases";

    interface Callback {
        void onResult(Result result);
    }

    static final class Result {
        final boolean updateAvailable;
        final String latestVersion;
        final String releaseUrl;
        final String error;

        Result(
                boolean updateAvailable,
                String latestVersion,
                String releaseUrl,
                String error) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.releaseUrl = releaseUrl;
            this.error = error;
        }
    }

    private MrsUpdateChecker() {
    }

    static void checkAsync(String currentVersion, Callback callback) {
        new Thread(() -> callback.onResult(check(currentVersion)),
                "JarnsenMrsUpdateCheck").start();
    }

    static Result check(String currentVersion) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(
                    "https://api.github.com/repos/"
                            + "Jarnsen/ATAK-Jarnsen-Mrs-Plugin/releases/latest"
            );
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty(
                    "User-Agent",
                    "Jarnsen-Mrs-Plugin"
            );
            connection.setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
            );

            int code = connection.getResponseCode();
            if (code == 404) {
                return new Result(
                        false,
                        null,
                        RELEASES_URL,
                        "GitHub-Repository ist nicht öffentlich erreichbar."
                );
            }
            if (code < 200 || code >= 300) {
                return new Result(
                        false,
                        null,
                        RELEASES_URL,
                        "GitHub HTTP " + code
                );
            }

            String body = read(connection.getInputStream());
            JSONObject release = new JSONObject(body);
            String tag = release.optString("tag_name", "");
            String latest = stripVersionPrefix(tag);
            String releaseUrl = release.optString(
                    "html_url",
                    RELEASES_URL
            );

            if (latest.isEmpty()) {
                return new Result(
                        false,
                        null,
                        releaseUrl,
                        "Keine Versionsnummer im Release gefunden."
                );
            }

            boolean newer = compareVersions(
                    latest,
                    stripVersionPrefix(currentVersion)
            ) > 0;

            return new Result(
                    newer,
                    latest,
                    releaseUrl,
                    null
            );
        } catch (Exception e) {
            return new Result(
                    false,
                    null,
                    RELEASES_URL,
                    e.getClass().getSimpleName()
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static int compareVersions(String left, String right) {
        int[] a = parseVersion(left);
        int[] b = parseVersion(right);
        int size = Math.max(a.length, b.length);
        for (int i = 0; i < size; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    static String stripVersionPrefix(String version) {
        if (version == null) {
            return "";
        }
        String value = version.trim().toLowerCase(Locale.US);
        if (value.startsWith("v")) {
            value = value.substring(1);
        }
        int end = 0;
        while (end < value.length()) {
            char ch = value.charAt(end);
            if ((ch >= '0' && ch <= '9') || ch == '.') {
                end++;
            } else {
                break;
            }
        }
        return value.substring(0, end);
    }

    private static int[] parseVersion(String version) {
        String clean = stripVersionPrefix(version);
        if (clean.isEmpty()) {
            return new int[]{0};
        }
        String[] parts = clean.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String read(InputStream input) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
        }
        return out.toString();
    }
}
