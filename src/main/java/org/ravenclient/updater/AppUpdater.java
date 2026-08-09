package org.ravenclient.updater;

import com.fasterxml.jackson.databind.JsonNode;
import org.ravenclient.util.Http;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Self-update support. On startup (or on demand) the launcher fetches an update
 * manifest, compares versions, and when newer downloads + verifies the update
 * zip. A small batch script performs the file swap after this process exits
 * (an .exe can't overwrite itself while running) and relaunches RavenClient.
 *
 * <p>Only {@code app.jar} / {@code libs/} (and optionally {@code RavenClient.exe})
 * need to ship in the zip - the bundled JRE is stable and stays in place, so
 * updates are small.
 */
public final class AppUpdater {

    /**
     * URL of the update manifest. The source repo (wispalol/ravenclient) is
     * private, so the update artifacts live in a separate public repo
     * (wispalol/ravenclient-releases) that installed clients can reach without
     * any credentials. update.json there is kept in sync by scripts/release.ps1
     * on every release. Can be overridden at launch with
     * {@code -Draven.updateUrl=https://.../update.json}
     */
    public static final String DEFAULT_MANIFEST_URL =
            "https://raw.githubusercontent.com/wispalol/ravenclient-releases/main/update.json";

    private AppUpdater() {
    }

    public static String manifestUrl() {
        String prop = System.getProperty("raven.updateUrl");
        return prop != null && !prop.isBlank() ? prop : DEFAULT_MANIFEST_URL;
    }

    public interface UpdateListener {
        void status(String text);
        void progress(double fraction);
    }

    /** The folder holding RavenClient.exe / app.jar / libs, or the dev classpath root. */
    public static Path installDir() {
        try {
            URI loc = AppUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path p = Path.of(loc);
            if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) {
                return p.getParent();
            }
            // jpackage app-image: exe is in RavenClient/, app.jar is in RavenClient/app/
            Path exeDir = p;
            if (Files.isRegularFile(exeDir.resolve("app/app.jar"))) {
                return exeDir.resolve("app");
            }
            return exeDir;
        } catch (Exception e) {
            return Path.of(System.getProperty("user.dir"));
        }
    }

    /** True when running from a packaged build (RavenClient.exe next to app/app.jar). */
    public static boolean isPackaged() {
        Path dir = installDir();
        return Files.isRegularFile(dir.getParent().resolve("RavenClient.exe"))
            && Files.isRegularFile(dir.resolve("app.jar"));
    }

    /** Fetches the manifest. Returns null when the client is already up to date. */
    public static UpdateManifest check() throws IOException {
        String json = Http.getString(manifestUrl());
        JsonNode node = Json.mapper().readTree(json);
        if (node == null || !node.hasNonNull("version") || node.get("version").asText().isBlank()) {
            throw new IOException("Update manifest is missing a version");
        }
        UpdateManifest m = Json.mapper().treeToValue(node, UpdateManifest.class);
        if (ClientVersion.compare(m.version(), ClientVersion.VERSION) <= 0) return null;
        return m;
    }

    /**
     * Downloads, verifies and stages the update into a writable staging folder,
     * then launches the swap script and returns once it has been handed off.
     * The caller should exit the app right after this returns; the script waits
     * for the app to close, replaces the files and relaunches RavenClient.exe.
     */
    public static void apply(UpdateManifest m, UpdateListener listener) throws IOException {
        if (!isPackaged()) {
            throw new IOException("Updates can only be installed from a packaged build.");
        }
        if (m == null || m.url() == null || m.url().isBlank()) {
            throw new IOException("Update manifest has no download URL.");
        }

        Path updatesDir = updatesDir();
        Files.createDirectories(updatesDir);
        Path zip = updatesDir.resolve("update-" + m.version() + ".zip");
        Path extractDir = updatesDir.resolve("update-" + m.version());

        listener.status("Downloading " + m.version() + "...");
        listener.progress(0);
        Http.download(m.url(), zip);

        if (m.sha256() != null && !m.sha256().isBlank()) {
            String actual = sha256(zip);
            if (!actual.equalsIgnoreCase(m.sha256())) {
                Files.deleteIfExists(zip);
                throw new IOException("Update failed checksum verification - try again later.");
            }
        }

        deleteRecursively(extractDir);
        Files.createDirectories(extractDir);
        unzip(zip, extractDir);
        Files.deleteIfExists(zip);
        if (!Files.isRegularFile(extractDir.resolve("app.jar"))) {
            throw new IOException("Update archive is missing app.jar.");
        }
        listener.progress(0.75);

        Path install = installDir();
        Path bat = updatesDir.resolve("apply-update.bat");
        Files.writeString(bat, batchScript());
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c",
                bat.toString(), install.toString(), extractDir.toString());
        pb.start();
        listener.status("Installing update...");
        listener.progress(1);
    }

    private static Path updatesDir() {
        String appdata = System.getenv("APPDATA");
        Path base = Path.of(appdata != null ? appdata : System.getProperty("user.home"), ".ravenclient");
        return base.resolve("updates");
    }

    private static String batchScript() {
        return """
                @echo off
                setlocal
                set "APP=%~1"
                set "UPDATE=%~2"
                :wait
                tasklist /FI "IMAGENAME eq RavenClient.exe" 2>nul | find /I "RavenClient.exe" >nul
                if not errorlevel 1 (
                  timeout /t 1 /nobreak >nul
                  goto wait
                )
                xcopy /E /Y /Q "%UPDATE%\\*" "%APP%\\" >nul
                rmdir /S /Q "%UPDATE%"
                start "" "%APP%\\..\\RavenClient.exe"
                del "%~f0"
                """;
    }

    private static void unzip(Path zip, Path target) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                Path out = target.resolve(e.getName()).normalize();
                if (!out.startsWith(target)) {
                    throw new IOException("Illegal entry in update archive: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (var fos = Files.newOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
                }
                zis.closeEntry();
            }
        }
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (var s = Files.list(p)) {
                s.forEach(ch -> { try { deleteRecursively(ch); } catch (IOException ignored) { } });
            }
        }
        Files.deleteIfExists(p);
    }
}
