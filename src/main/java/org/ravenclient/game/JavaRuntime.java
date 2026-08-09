package org.ravenclient.game;

import com.fasterxml.jackson.databind.JsonNode;
import org.ravenclient.download.Downloader;
import org.ravenclient.updater.AppUpdater;
import org.ravenclient.util.Http;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Locates or downloads the Java runtime needed by a Minecraft version,
 * mirroring the official launcher's java-runtime component.
 */
public final class JavaRuntime {

    private static final String PRODUCTS_URL =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private JavaRuntime() {
    }

    public static int localMajor() {
        return Runtime.version().feature();
    }

    public static Path localJava() {
        return Path.of(System.getProperty("java.home"), "bin",
                osId().equals("windows") ? "java.exe" : "java");
    }

    /**
     * Returns the Java executable to use for the given version requirements.
     * Uses the local JVM when it satisfies the requirement, otherwise downloads
     * the matching Mojang-bundled runtime into {@code gameDir/runtime}.
     * Falls back to the launcher's own bundled JRE when running from a packaged app.
     */
    public static Path resolve(Path gameDir, JsonNode javaVersion, GameLauncher.Listener listener) throws IOException {
        int required = javaVersion != null ? javaVersion.path("majorVersion").asInt(0) : 0;
        String component = javaVersion != null ? javaVersion.path("component").asText("") : "";

        // Versions without a declared runtime run on the legacy Java 8 runtime,
        // mirroring the official launcher (modern Java is incompatible with them).
        if (component.isEmpty()) {
            component = "jre-legacy";
            required = 8;
        }

        if (required <= localMajor() && !"jre-legacy".equals(component)) {
            if (listener != null) {
                listener.log("Using local Java " + localMajor() + " (version requires Java " + required + ")");
            }
            return localJava();
        }

        // Fall back to the launcher's bundled JRE (packaged with jpackage) if it satisfies requirements
        Path bundled = bundledRuntime();
        if (bundled != null && Files.isRegularFile(bundled)) {
            if (listener != null) {
                listener.log("Using bundled launcher JRE (Java " + localMajor() + ")");
            }
            return bundled;
        }

        String platform = platformKey();
        Path root = gameDir.resolve("runtime").resolve(component).resolve(platform);
        if (listener != null) listener.status("Preparing Java runtime " + component + " (Java " + required + ")...");

        JsonNode products = Json.mapper().readTree(Http.getBytes(PRODUCTS_URL));
        JsonNode entry = products.path(platform).path(component).path(0);
        JsonNode manifestMeta = entry.path("manifest");
        String manifestUrl = manifestMeta.path("url").asText("");
        String manifestSha1 = manifestMeta.path("sha1").asText("");
        if (manifestUrl.isEmpty()) {
            throw new IOException("No Java runtime manifest available for " + component + " on " + platform);
        }

        Path manifestFile = root.resolve("version.json");
        Files.createDirectories(root);
        if (!sha1Equals(manifestFile, manifestSha1)) {
            Files.write(manifestFile, Http.getBytes(manifestUrl));
        }

        JsonNode manifest = Json.mapper().readTree(manifestFile.toFile());
        downloadFiles(manifest.path("files"), root, listener);

        Path java = root.resolve(osId().equals("windows") ? "bin/java.exe" : "bin/java");
        if (!Files.isRegularFile(java)) throw new IOException("Installed Java runtime is incomplete: " + java);
        if (listener != null) listener.log("Using Mojang Java runtime " + component + " at " + java);
        return java;
    }

    /**
     * Returns the path to the launcher's own bundled JRE (created by jpackage),
     * or null if not running from a packaged distribution.
     */
    private static Path bundledRuntime() {
        Path installDir = AppUpdater.installDir();
        Path runtime = installDir.resolve("runtime").resolve("bin").resolve(
                osId().equals("windows") ? "java.exe" : "java");
        return Files.isRegularFile(runtime) ? runtime : null;
    }

    private static void downloadFiles(JsonNode files, Path root, GameLauncher.Listener listener) throws IOException {
        List<Downloader.Entry> entries = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = files.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String path = e.getKey();
            JsonNode node = e.getValue();
            String type = node.path("type").asText("file");
            if ("directory".equals(type)) {
                Files.createDirectories(root.resolve(path));
                continue;
            }
            if (!"file".equals(type)) continue; // skip links (unused on Windows installs)
            JsonNode raw = node.path("downloads").path("raw");
            String url = raw.path("url").asText("");
            if (url.isEmpty()) continue;
            entries.add(new Downloader.Entry(url, root.resolve(path), raw.path("sha1").asText("")));
        }
        if (entries.isEmpty()) throw new IOException("Java runtime manifest contains no files");
        if (listener != null) listener.status("Downloading Java runtime (" + entries.size() + " files)...");
        Downloader.downloadAll(entries, 8, (done, total, file) -> {
            if (listener != null) listener.progress(done / (double) total);
        });
    }

    private static boolean sha1Equals(Path file, String expected) throws IOException {
        if (expected == null || expected.isBlank() || !Files.isRegularFile(file)) return false;
        return Downloader.sha1(file).equalsIgnoreCase(expected);
    }

    private static String platformKey() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            if (arch.equals("aarch64") || arch.equals("arm64")) return "windows-arm64";
            if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) return "windows-x86";
            return "windows-x64";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return (arch.equals("aarch64") || arch.equals("arm64")) ? "mac-os-arm64" : "mac-os";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) return "linux-arm64";
        if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) return "linux-i386";
        return "linux";
    }

    private static String osId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }
}
