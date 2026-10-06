package com.jarnsen.atak.mrs.plugin;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads the TAK.gov-signed APK into ATAK's local custom plugin folder.
 *
 * The APK is first downloaded into the plugin's private cache directory and
 * fully verified there (SHA-256, package name, signing certificate, version
 * newer than the installed one). Only then it is copied into the shared ATAK
 * folder and the copy is hashed again before it is moved into place.
 */
final class MrsPluginUpdateDownloader {

    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;
    private static final String ATAK_CUSTOM_PLUGIN_DIRECTORY =
            "support/apks/custom";
    private static final String STAGING_PREFIX = ".jarnsen-mrs-update-";
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
            } catch (RuntimeException e) {
                result = failure("Download/Kopie fehlgeschlagen: "
                        + e.getClass().getSimpleName());
            } finally {
                DOWNLOAD_IN_PROGRESS.set(false);
            }
            final Result finalResult = result;
            new Handler(Looper.getMainLooper()).post(
                    () -> callback.onComplete(finalResult)
            );
        }, "JarnsenMrsUpdateDownload").start();
    }

    private static Result download(
            Context context,
            MrsUpdateChecker.Result update) {
        if (update == null
                || update.apkDownloadUrl == null
                || update.apkFileName == null
                || update.apkSha256 == null
                || update.latestVersion == null) {
            return failure("Für dieses Release fehlt das geprüfte TAK.gov-APK.");
        }
        String normalizedName = update.apkFileName.toLowerCase(Locale.US);
        if (!normalizedName.matches(
                "atak-plugin-jarnsen-mrs-[a-z0-9._-]+\\.apk")) {
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
        File cacheDirectory = context.getCacheDir();
        if (cacheDirectory == null
                || (!cacheDirectory.isDirectory()
                && !cacheDirectory.mkdirs())) {
            return failure("Zwischenspeicher ist nicht verfügbar.");
        }

        // Leftovers of an interrupted earlier run (only one download runs at
        // a time, see DOWNLOAD_IN_PROGRESS).
        removeStaleTemporaryFiles(destinationDirectory);

        File downloaded = null;
        File staged = null;
        HttpURLConnection connection = null;
        try {
            downloaded = File.createTempFile(
                    "jarnsen-mrs-update-",
                    ".apk",
                    cacheDirectory
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
                         new FileOutputStream(downloaded))) {
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

            // All checks run on the private copy.
            if (!update.apkSha256.equalsIgnoreCase(sha256(downloaded))) {
                return failure("SHA-256-Prüfung der APK ist fehlgeschlagen.");
            }
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageArchiveInfo(
                    downloaded.getAbsolutePath(),
                    0
            );
            if (packageInfo == null
                    || !BuildConfig.APPLICATION_ID.equals(
                    packageInfo.packageName)) {
                return failure("Die heruntergeladene Datei ist nicht das "
                        + "Jarnsen-Mrs-Plugin.");
            }
            if (!hasSameSigningCertificate(packageManager, downloaded)) {
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
            String installedVersion = MrsUpdateChecker.stripVersionPrefix(
                    BuildConfig.VERSION_NAME
            );
            if (MrsUpdateChecker.compareVersions(
                    apkVersion,
                    installedVersion) <= 0) {
                return failure("Die APK ist nicht neuer als die "
                        + "installierte Version.");
            }

            // Copy into the shared ATAK folder and verify the copy again.
            staged = File.createTempFile(
                    STAGING_PREFIX,
                    ".part",
                    destinationDirectory
            );
            copyFile(downloaded, staged);
            if (!update.apkSha256.equalsIgnoreCase(sha256(staged))) {
                return failure("SHA-256-Prüfung der kopierten APK ist "
                        + "fehlgeschlagen.");
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
            if (!staged.renameTo(destination)) {
                if (backup.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    backup.renameTo(destination);
                }
                return failure("Die geprüfte APK konnte nicht in den "
                        + "ATAK-Pluginordner kopiert werden.");
            }
            staged = null;
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
            if (downloaded != null && downloaded.exists()) {
                //noinspection ResultOfMethodCallIgnored
                downloaded.delete();
            }
            if (staged != null && staged.exists()) {
                //noinspection ResultOfMethodCallIgnored
                staged.delete();
            }
        }
    }

    private static void removeStaleTemporaryFiles(File directory) {
        File[] files = directory.listFiles((dir, name) ->
                (name.startsWith(STAGING_PREFIX) && name.endsWith(".part"))
                        || (name.toLowerCase(Locale.US)
                        .startsWith("atak-plugin-jarnsen-mrs-")
                        && name.endsWith(".apk.bak")));
        if (files == null) {
            return;
        }
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
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

    private static void copyFile(File from, File to) throws Exception {
        try (InputStream input = new BufferedInputStream(
                new FileInputStream(from));
             BufferedOutputStream output = new BufferedOutputStream(
                     new FileOutputStream(to))) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
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

    /** PackageManager flag that returns the signing certificates. */
    @SuppressWarnings("deprecation")
    static int signatureFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
    }

    /** Current signing certificates of a package (null if unavailable). */
    @SuppressWarnings("deprecation")
    static Signature[] signaturesOf(PackageInfo info) {
        if (info == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            return signingInfo == null
                    ? null
                    : signingInfo.getApkContentsSigners();
        }
        return info.signatures;
    }

    private static Set<String> signerSet(PackageInfo info) {
        Set<String> out = new HashSet<>();
        Signature[] signatures = signaturesOf(info);
        if (signatures != null) {
            for (Signature signature : signatures) {
                out.add(Base64.encodeToString(
                        signature.toByteArray(),
                        Base64.NO_WRAP
                ));
            }
        }
        return out;
    }

    private static boolean hasSameSigningCertificate(
            PackageManager packageManager,
            File apk) throws Exception {
        PackageInfo installed = packageManager.getPackageInfo(
                BuildConfig.APPLICATION_ID,
                signatureFlags()
        );
        PackageInfo candidate = packageManager.getPackageArchiveInfo(
                apk.getAbsolutePath(),
                signatureFlags()
        );
        Set<String> installedSigners = signerSet(installed);
        return !installedSigners.isEmpty()
                && installedSigners.equals(signerSet(candidate));
    }

    private static Result failure(String error) {
        return new Result(false, null, error);
    }
}
