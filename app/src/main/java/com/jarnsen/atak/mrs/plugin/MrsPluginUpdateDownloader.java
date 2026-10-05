package com.jarnsen.atak.mrs.plugin;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Handler;
import android.os.Looper;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads the TAK.gov-signed APK into ATAK's local custom plugin folder. */
final class MrsPluginUpdateDownloader {

    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;
    private static final String ATAK_CUSTOM_PLUGIN_DIRECTORY =
            "support/apks/custom";
    private static final AtomicBoolean DOWNLOAD_IN_PROGRESS =
            new AtomicBoolean(false);

    interface Callback {
        void onComplete(Result result);
    }

    static final class Result {
        final boolean success;
        final String path;
        final String error;

        private Result(boolean success, String path, String error) {
            this.success = success;
            this.path = path;
            this.error = error;
        }
    }

    private MrsPluginUpdateDownloader() {
    }

    static void downloadToAtakFolder(
            Context context,
            MrsUpdateChecker.Result update,
            Callback callback) {
        if (!DOWNLOAD_IN_PROGRESS.compareAndSet(false, true)) {
            new Handler(Looper.getMainLooper()).post(() ->
                    callback.onComplete(failure(
                            "Ein Update wird bereits heruntergeladen."
                    ))
            );
            return;
        }
        new Thread(() -> {
            Result result;
            try {
                result = download(context, update);
            } finally {
                DOWNLOAD_IN_PROGRESS.set(false);
            }
            new Handler(Looper.getMainLooper()).post(
                    () -> callback.onComplete(result)
            );
        }, "JarnsenMrsUpdateDownload").start();
    }

    private static Result download(
            Context context,
            MrsUpdateChecker.Result update) {
        if (update == null
                || update.apkDownloadUrl == null
                || update.apkFileName == null
                || update.apkSha256 == null) {
            return failure("Für dieses Release fehlt das geprüfte TAK.gov-APK.");
        }
        String normalizedName = update.apkFileName.toLowerCase(Locale.US);
        if (!normalizedName.matches(
                "atak-plugin-jarnsen-mrs-[a-z0-9._-]+-takgov\\.apk")) {
            return failure("Der APK-Dateiname ist ungültig.");
        }
        String expectedPrefix = "https://github.com/"
                + "Jarnsen/ATAK-Jarnsen-Mrs-Plugin/releases/download/";
        if (!update.apkDownloadUrl.startsWith(expectedPrefix)
                || !update.apkSha256.matches("(?i)^[0-9a-f]{64}$")) {
            return failure("Der Download-Link oder SHA-256-Wert ist ungültig.");
        }

        File destinationDirectory = FileSystemUtils.getItem(
                ATAK_CUSTOM_PLUGIN_DIRECTORY
        );
        if (destinationDirectory == null
                || (!destinationDirectory.isDirectory()
                && !destinationDirectory.mkdirs())) {
            return failure("ATAK-Pluginordner kann nicht angelegt werden.");
        }

        File temporary = null;
        HttpURLConnection connection = null;
        try {
            temporary = File.createTempFile(
                    ".jarnsen-mrs-update-",
                    ".part",
                    destinationDirectory
            );
            connection = (HttpURLConnection) new URL(
                    update.apkDownloadUrl
            ).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty(
                    "User-Agent",
                    "Jarnsen-Mrs-Plugin"
            );
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                return failure("GitHub-Download fehlgeschlagen (HTTP "
                        + response + ").");
            }
            String finalHost = connection.getURL().getHost()
                    .toLowerCase(Locale.US);
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())
                    || !("github.com".equals(finalHost)
                    || finalHost.endsWith(".githubusercontent.com"))) {
                return failure("Der Download wurde auf einen nicht "
                        + "vertrauenswürdigen Server umgeleitet.");
            }
            long announcedSize = connection.getContentLength();
            if (announcedSize > MAX_APK_BYTES) {
                return failure("Die APK-Datei ist unerwartet groß.");
            }

            try (InputStream input = new BufferedInputStream(
                    connection.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(
                         new FileOutputStream(temporary))) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0L;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_APK_BYTES) {
                        return failure("Die APK-Datei ist unerwartet groß.");
                    }
                    output.write(buffer, 0, read);
                }
                if (total == 0L || (announcedSize >= 0L
                        && total != announcedSize)) {
                    return failure("Der APK-Download ist unvollständig.");
                }
            }

            if (!update.apkSha256.equalsIgnoreCase(sha256(temporary))) {
                return failure("SHA-256-Prüfung der APK ist fehlgeschlagen.");
            }
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageArchiveInfo(temporary.getAbsolutePath(), 0);
            if (packageInfo == null
                    || !BuildConfig.APPLICATION_ID.equals(
                    packageInfo.packageName)) {
                return failure("Die heruntergeladene Datei ist nicht das "
                        + "Jarnsen-Mrs-Plugin.");
            }
            if (!hasSameSigningCertificate(
                    context.getPackageManager(),
                    temporary
            )) {
                return failure("Die APK-Signatur stimmt nicht mit der "
                        + "installierten Jarnsen-Mrs-Version überein.");
            }
            String apkVersion = MrsUpdateChecker.stripVersionPrefix(
                    packageInfo.versionName
            );
            if (!update.latestVersion.equals(apkVersion)) {
                return failure("APK-Version und Release-Version stimmen "
                        + "nicht überein.");
            }

            File destination = new File(
                    destinationDirectory,
                    update.apkFileName
            );
            File backup = new File(destination.getAbsolutePath() + ".bak");
            if (backup.exists() && !backup.delete()) {
                return failure("Alte temporäre Sicherungsdatei kann nicht "
                        + "entfernt werden.");
            }
            if (destination.exists() && !destination.renameTo(backup)) {
                return failure("Vorhandene Update-Datei kann nicht ersetzt "
                        + "werden.");
            }
            if (!temporary.renameTo(destination)) {
                if (backup.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    backup.renameTo(destination);
                }
                return failure("Die geprüfte APK konnte nicht in den "
                        + "ATAK-Pluginordner kopiert werden.");
            }
            temporary = null;
            if (backup.exists()) {
                //noinspection ResultOfMethodCallIgnored
                backup.delete();
            }
            removeOlderCopies(destinationDirectory, destination);
            return new Result(true, destination.getAbsolutePath(), null);
        } catch (Exception e) {
            return failure("Download/Kopie fehlgeschlagen: "
                    + e.getClass().getSimpleName());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (temporary != null && temporary.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    private static void removeOlderCopies(File directory, File keep) {
        File[] files = directory.listFiles((dir, name) -> {
            String normalized = name.toLowerCase(Locale.US);
            return normalized.startsWith("atak-plugin-jarnsen-mrs-")
                    && normalized.endsWith(".apk");
        });
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.equals(keep)) {
                // Only remove old staged APKs belonging to this one plugin.
                // Never touch ATAK's product.inf or other plugins' files.
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(
                new FileInputStream(file))) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    @SuppressWarnings("deprecation")
    private static boolean hasSameSigningCertificate(
            PackageManager packageManager,
            File apk) throws Exception {
        PackageInfo installed = packageManager.getPackageInfo(
                BuildConfig.APPLICATION_ID,
                PackageManager.GET_SIGNATURES
        );
        PackageInfo candidate = packageManager.getPackageArchiveInfo(
                apk.getAbsolutePath(),
                PackageManager.GET_SIGNATURES
        );
        Signature[] installedSignatures = installed.signatures;
        Signature[] candidateSignatures = candidate == null
                ? null
                : candidate.signatures;
        if (installedSignatures == null
                || candidateSignatures == null
                || installedSignatures.length == 0
                || candidateSignatures.length == 0) {
            return false;
        }
        return Arrays.equals(
                installedSignatures[0].toByteArray(),
                candidateSignatures[0].toByteArray()
        );
    }

    private static Result failure(String error) {
        return new Result(false, null, error);
    }
}
