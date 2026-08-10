package org.ravenclient.game;

import com.fasterxml.jackson.databind.JsonNode;
import org.ravenclient.auth.Account;
import org.ravenclient.config.LauncherConfig;
import org.ravenclient.download.Downloader;
import org.ravenclient.meta.Artifact;
import org.ravenclient.meta.Asset;
import org.ravenclient.meta.AssetIndexInfo;
import org.ravenclient.meta.GameAssetIndex;
import org.ravenclient.meta.Library;
import org.ravenclient.meta.MinecraftMeta;
import org.ravenclient.meta.VersionManifest;
import org.ravenclient.meta.VersionMeta;
import org.ravenclient.updater.AppUpdater;
import org.ravenclient.util.Http;
import org.ravenclient.util.Json;
import org.ravenclient.config.ProfileStore;
import org.ravenclient.util.Json;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Downloads the Minecraft version, libraries, natives and assets, then launches
 * the game with the correct JVM arguments and the account's session tokens.
 */
public final class GameLauncher {

    public static final String CLIENT_ID = "00000000402b5328";

    public interface Listener {
        void log(String line);

        void status(String text);

        void progress(double fraction);
    }

    public record LaunchData(VersionMeta meta, String id, List<String> classpath,
                             Path nativesDir, Path assetsRoot, Path gameDir, String assetIndexId,
                             Path javaExe, Path versionDir, Path modsDir) {
    }

    private final LauncherConfig config;
    private final Listener listener;

    public GameLauncher(LauncherConfig config, Listener listener) {
        this.config = config;
        this.listener = listener;
    }

    public List<String> availableVersions() throws IOException {
        return MinecraftMeta.versionIds();
    }

    public String resolveId(String selected) throws IOException {
        VersionManifest mf = MinecraftMeta.manifest();
        if (selected == null || selected.isBlank()) return mf.latest().release();
        String s = selected.trim();
        if (s.equalsIgnoreCase("latest release")) return mf.latest().release();
        if (s.equalsIgnoreCase("latest snapshot")) return mf.latest().snapshot();
        for (VersionManifest.Version v : mf.versions()) {
            if (v.id().equals(s)) return v.id();
        }
        throw new IOException("Unknown Minecraft version: " + s);
    }

    /**
     * Ensures everything needed to launch {@code selected} exists on disk.
     *
     * @return the resolved launch data
     */
    public LaunchData prepare(String selected) throws IOException {
        Path versionsRoot = config.gameDir.resolve("versions");
        String id;
        Path metaFile;
        Path local = versionsRoot.resolve(selected).resolve(selected + ".json");
        if (Files.exists(local)) {
            id = selected;
            metaFile = local;
        } else {
            id = resolveId(selected);
            metaFile = versionsRoot.resolve(id).resolve(id + ".json");
        }
        Files.createDirectories(metaFile.getParent());
        listener.status("Preparing " + id + "...");
        if (!Files.exists(metaFile)) {
            listener.log("Downloading version metadata for " + id);
            Files.write(metaFile, Http.getBytes(MinecraftMeta.versionJsonUrl(id)));
        }
        VersionMeta meta = Json.mapper().readValue(metaFile.toFile(), VersionMeta.class);

        Artifact client = meta.downloads() != null ? meta.downloads().client() : null;
        Path versionDir = metaFile.getParent();
        Path clientJar = versionDir.resolve(id + ".jar");
        if (client != null) {
            Downloader.download(new Downloader.Entry(client.url(), clientJar, client.sha1()));
        }

        List<String> classpath = new ArrayList<>();
        List<Path> nativeJars = new ArrayList<>();
        List<Downloader.Entry> toDownload = new ArrayList<>();

        if (meta.libraries() != null) {
            for (Library lib : meta.libraries()) {
                if (!rulesMatch(lib.rules())) continue;
                // Loader manifests can mark a library as non-downloadable (a stub whose
                // artifact is provided another way) — skip it entirely.
                if (!lib.downloadable()) continue;
                // A library re-shipped by the loader may be excluded from the classpath
                // while still being downloaded (see LoaderMeta.merge).
                boolean onClasspath = lib.includeInClasspath();
                String os = osId();
                String classifier = lib.natives() != null ? lib.natives().get(os) : null;
                if (classifier != null) classifier = classifier.replace("${arch}", arch());

                Artifact artifact = lib.downloads() != null ? lib.downloads().artifact() : null;
                if (artifact != null) {
                    Path target = config.gameDir.resolve("libraries").resolve(artifact.path());
                    toDownload.add(new Downloader.Entry(artifact.url(), target, artifact.sha1()));
                    if (onClasspath) classpath.add(target.toString());
                    if (classifier != null && lib.downloads().classifiers() != null) {
                        Artifact nativeArt = lib.downloads().classifiers().get(classifier);
                        if (nativeArt != null) {
                            Path nt = config.gameDir.resolve("libraries").resolve(nativeArt.path());
                            toDownload.add(new Downloader.Entry(nativeArt.url(), nt, nativeArt.sha1()));
                            nativeJars.add(nt);
                        }
                    }
                } else if (lib.name() != null) {
                    // No main artifact declared. Natives-only libraries (e.g. jinput-platform)
                    // ship just a classifier jar; other legacy libraries resolve from the maven name.
                    Artifact nativeArt = classifier != null && lib.downloads() != null
                            && lib.downloads().classifiers() != null
                            ? lib.downloads().classifiers().get(classifier) : null;
                    if (nativeArt != null) {
                        Path nt = config.gameDir.resolve("libraries").resolve(nativeArt.path());
                        toDownload.add(new Downloader.Entry(nativeArt.url(), nt, nativeArt.sha1()));
                        nativeJars.add(nt);
                    } else {
                        String[] parts = lib.name().split(":");
                        if (parts.length >= 3) {
                            String rel = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2]
                                    + "/" + parts[1] + "-" + parts[2] + ".jar";
                            String base = (lib.url() != null && !lib.url().isBlank())
                                    ? lib.url() : MinecraftMeta.LIBRARIES_URL;
                            if (!base.endsWith("/")) base += "/";
                            Path target = config.gameDir.resolve("libraries").resolve(rel);
                            toDownload.add(new Downloader.Entry(base + rel, target, null));
                            if (onClasspath) classpath.add(target.toString());
                            if (classifier != null) {
                                String nrel = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2]
                                        + "/" + parts[1] + "-" + parts[2] + "-" + classifier + ".jar";
                                Path nt = config.gameDir.resolve("libraries").resolve(nrel);
                                toDownload.add(new Downloader.Entry(base + nrel, nt, null));
                                nativeJars.add(nt);
                            }
                        }
                    }
                }
            }
        }

        if (client != null) classpath.add(clientJar.toString());

        if (!toDownload.isEmpty()) {
            listener.status("Downloading libraries (" + toDownload.size() + ")...");
            Downloader.downloadAll(toDownload, 8, (done, total, file) -> {
                listener.progress(done / (double) total);
                if (done % 25 == 0 || done == total) listener.log("  [" + done + "/" + total + "]");
            });
        }

        Path nativesDir = versionDir.resolve("natives");
        for (Path jar : nativeJars) {
            if (Files.exists(jar)) extractNatives(jar, nativesDir);
        }

        Path assetsRoot = config.gameDir.resolve("assets");
        String assetIndexId = meta.assetIndex() != null ? meta.assetIndex().id() : null;
        if (assetIndexId != null) downloadAssets(meta.assetIndex(), assetsRoot);

        Path javaExe = JavaRuntime.resolve(config.gameDir, meta.javaVersion(), profileJavaPath(selected), listener);

        // Create version-specific mods directory for mod loaders
        Path modsDir = versionDir.resolve("mods");
        Files.createDirectories(modsDir);

        // Install bundled RavenClient mods (HUD + version-matched title screen)
        installBundledMods(versionDir, id);

        // Loaders only read mods from the game's root mods/ folder, so materialize the
        // profile's mods there (and drop ones we placed for a previously launched profile).
        syncMods(versionDir);

        listener.status("Ready to launch " + id);
        listener.log("Minecraft Version: " + id);
        listener.log("Game Directory: " + config.gameDir);
        listener.log("Version Directory: " + versionDir);
        listener.log("Mods Directory: " + modsDir);
        listener.log("Installed Mods: " + Files.list(modsDir).count());
        
        return new LaunchData(meta, id, classpath, nativesDir, assetsRoot, config.gameDir, assetIndexId, javaExe, versionDir, modsDir);
    }

    private Path profileJavaPath(String selected) {
        ProfileStore.Profile p = ProfileStore.find(config.launcherDir, selected);
        if (p != null && p.javaPath() != null && !p.javaPath().isBlank()) {
            Path candidate = Path.of(p.javaPath());
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    /**
     * Loaders (Fabric/Quilt/Forge/NeoForge) only scan the game's root {@code mods/} folder,
     * while RavenClient stores mods per profile under {@code versions/<profile>/mods}. Before
     * launching, copy the profile's jars into the root folder; jars we placed for a previously
     * launched profile are tracked in a marker file and removed first, so stale mods from a
     * different version/loader never leak into the game. Manually placed jars are left alone.
     */
    private void syncMods(Path versionDir) throws IOException {
        Path profileMods = versionDir.resolve("mods");
        Path gameMods = config.gameDir.resolve("mods");
        Files.createDirectories(profileMods);
        Files.createDirectories(gameMods);

        Path marker = gameMods.resolve(".ravenclient-modssync.json");
        if (Files.isRegularFile(marker)) {
            try {
                ModsSync prev = Json.mapper().readValue(marker.toFile(), ModsSync.class);
                if (prev != null && prev.files() != null) {
                    for (String f : prev.files()) {
                        if (f == null || f.isBlank()) continue;
                        Files.deleteIfExists(gameMods.resolve(f));
                    }
                }
            } catch (Exception ignored) {
                // unreadable marker: fall through and simply re-copy the current profile's mods
            }
            Files.deleteIfExists(marker);
        }

        List<String> placed = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(profileMods, "*.jar")) {
            for (Path jar : ds) {
                Path out = gameMods.resolve(jar.getFileName().toString());
                Files.copy(jar, out, StandardCopyOption.REPLACE_EXISTING);
                placed.add(out.getFileName().toString());
            }
        }
        if (!placed.isEmpty()) {
            Files.write(marker, Json.mapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(new ModsSync(placed)));
        }
        listener.log("Mods synced to game folder: " + placed.size());
    }

    private record ModsSync(List<String> files) {
    }

    /**
     * Copies the bundled RavenClient mods from the launcher's resources into the
     * version-specific mods directory so they load with the game: the HUD mod for
     * every profile, plus the Lunar-style title-screen mod matched to the profile's
     * Minecraft version (raven-client-title-&lt;mc&gt;.jar, skipped when none is shipped).
     */
    private void installBundledMods(Path versionDir, String profileId) throws IOException {
        Path modsDir = versionDir.resolve("mods");
        Files.createDirectories(modsDir);

        Path hudFile = modsDir.resolve("raven-client-hud.jar");
        if (!Files.exists(hudFile)) {
            try (InputStream modStream = getClass().getResourceAsStream("/raven-client-hud.jar")) {
                if (modStream != null) {
                    Files.copy(modStream, hudFile, StandardCopyOption.REPLACE_EXISTING);
                    listener.log("RavenClient HUD mod installed: raven-client-hud.jar");
                }
            } catch (Exception ignored) {
                // No bundled mod - skip silently
            }
        }

        String mcVersion = mcVersionOf(profileId);
        String titleName = "raven-client-title-" + mcVersion + ".jar";
        Path titleFile = modsDir.resolve(titleName);
        if (!Files.exists(titleFile)) {
            InputStream modStream = getClass().getResourceAsStream("/" + titleName);
            if (modStream == null) {
                String fallback = fallbackTitleMod(mcVersion);
                if (fallback != null) {
                    titleName = "raven-client-title-" + fallback + ".jar";
                    modStream = getClass().getResourceAsStream("/" + titleName);
                }
            }
            if (modStream != null) {
                Files.copy(modStream, titleFile, StandardCopyOption.REPLACE_EXISTING);
                listener.log("RavenClient title mod installed: " + titleName);
            }
        }
    }

    /** The plain Minecraft version for a profile id, e.g. {@code fabric-1.21.11} -> {@code 1.21.11}. */
    private static String mcVersionOf(String profileId) {
        Loader loader = LoaderMeta.loaderOf(profileId);
        return loader != null ? profileId.substring(loader.prefix().length()) : profileId;
    }

    private static String fallbackTitleMod(String mcVersion) {
        String v = mcVersion == null ? "" : mcVersion.trim();
        if (v.isEmpty()) return null;

        int lastDot = v.lastIndexOf('.');
        if (lastDot > 0) {
            String majorMinor = v.substring(0, lastDot);
            String resource = "/raven-client-title-" + majorMinor + ".jar";
            if (GameLauncher.class.getResourceAsStream(resource) != null) {
                return majorMinor;
            }
        }

        if (v.startsWith("26.1")) return "26.2";

        return null;
    }

    private void downloadAssets(AssetIndexInfo info, Path assetsRoot) throws IOException {
        Path indexFile = assetsRoot.resolve("indexes").resolve(info.id() + ".json");
        if (!Files.exists(indexFile)) {
            listener.log("Downloading asset index " + info.id());
            Files.createDirectories(indexFile.getParent());
            Files.write(indexFile, Http.getBytes(info.url()));
        }
        GameAssetIndex idx = Json.mapper().readValue(indexFile.toFile(), GameAssetIndex.class);
        if (idx.objects() == null || idx.objects().isEmpty()) return;

        List<Downloader.Entry> entries = new ArrayList<>();
        for (Asset asset : idx.objects().values()) {
            String hash = asset.hash();
            entries.add(new Downloader.Entry(
                    MinecraftMeta.ASSETS_URL + hash.substring(0, 2) + "/" + hash,
                    assetsRoot.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash),
                    hash));
        }
        listener.status("Downloading " + entries.size() + " assets...");
        listener.progress(0);
        Downloader.downloadAll(entries, 12, (done, total, file) -> listener.progress(done / (double) total));

        // Legacy versions use "virtual" assets that must also exist at assets/virtual/<id>/...
        if (Boolean.TRUE.equals(idx.virtual())) {
            Path virtualRoot = assetsRoot.resolve("virtual").resolve(info.id());
            int copied = 0;
            for (var e : idx.objects().entrySet()) {
                String hash = e.getValue().hash();
                Path src = assetsRoot.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                Path out = virtualRoot.resolve(e.getKey());
                if (Files.exists(src)) {
                    Files.createDirectories(out.getParent());
                    Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
            listener.log("Prepared " + copied + " virtual assets");
        }
    }

    /** Builds the JVM command line and starts the game process. */
    public Process launch(LaunchData data, Account account) throws IOException {
        List<String> cmd = buildCommand(data, account);
        StringBuilder sb = new StringBuilder("Launching:");
        for (String s : cmd) sb.append(' ').append(s.contains(" ") ? "\"" + s + "\"" : s);
        // Never log the session token (public launcher).
        String token = account.minecraftToken();
        String logged = token != null && !token.isBlank() ? sb.toString().replace(token, "[REDACTED]") : sb.toString();
        listener.log(logged);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(data.gameDir().toFile());
        Process process = pb.start();
        new StreamGobbler(process.getInputStream(), listener).start();
        new StreamGobbler(process.getErrorStream(), listener).start();
        return process;
    }

    private List<String> buildCommand(LaunchData data, Account account) {
        List<String> cmd = new ArrayList<>();
        cmd.add(data.javaExe() != null ? data.javaExe().toString() : javaExecutable());

        // Fixed-size heap (equal Xms/Xmx) so the JVM never pauses to resize.
        cmd.add("-Xmx" + config.memoryMb + "m");
        cmd.add("-Xms" + config.memoryMb + "m");

        int javaMajor = requiredJavaMajor(data);
        cmd.addAll(performanceJvmArgs(javaMajor));

        // Version-provided JVM arguments (keep them, but let us own classpath + natives dir).
        JsonNode jvm = data.meta().arguments() != null ? data.meta().arguments().path("jvm") : null;
        if (jvm != null && jvm.isArray()) {
            for (JsonNode arg : jvm) {
                if (arg.isTextual()) {
                    addJvmArg(cmd, arg.asText(), data, account);
                } else if (arg.isObject() && rulesMatch(arg.path("rules"))) {
                    JsonNode value = arg.path("value");
                    if (value.isTextual()) addJvmArg(cmd, value.asText(), data, account);
                    else if (value.isArray()) {
                        for (JsonNode v : value) addJvmArg(cmd, v.asText(), data, account);
                    }
                }
            }
        }

        cmd.add("-Djava.library.path=" + data.nativesDir());
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, data.classpath()));
        cmd.add(data.meta().mainClass() != null && !data.meta().mainClass().isBlank()
                ? data.meta().mainClass() : "net.minecraft.client.main.Main");

        // Game arguments.
        JsonNode game = data.meta().arguments() != null ? data.meta().arguments().path("game") : null;
        if (game != null && game.isArray()) {
            for (JsonNode arg : game) {
                if (arg.isTextual()) {
                    String s = substitute(arg.asText(), data, account);
                    if (s != null && !s.isBlank()) cmd.add(s);
                } else if (arg.isObject() && rulesMatch(arg.path("rules"))) {
                    JsonNode value = arg.path("value");
                    if (value.isTextual()) {
                        String s = substitute(value.asText(), data, account);
                        if (s != null && !s.isBlank()) cmd.add(s);
                    } else if (value.isArray()) {
                        for (JsonNode v : value) {
                            String s = substitute(v.asText(), data, account);
                            if (s != null && !s.isBlank()) cmd.add(s);
                        }
                    }
                }
            }
        } else {
            // Legacy versions (pre-1.13) use a flat minecraftArguments string.
            String legacy = data.meta().minecraftArguments();
            if (legacy != null && !legacy.isBlank()) {
                cmd.addAll(splitArgs(substitute(legacy, data, account)));
            }
        }
        return cmd;
    }

    private void addJvmArg(List<String> cmd, String raw, LaunchData data, Account account) {
        if (raw == null || raw.isBlank()) return;
        if (raw.equals("-cp") || raw.equals("${classpath}") || raw.startsWith("-Djava.library.path=")) return;
        // Fabric/modern version JSON can ship a removed --sun-misc-unsafe-memory-access
        // flag that crashes Java 21. --enable-native-access=ALL-UNNAMED is valid on 21+.
        if (javaMajorForGame(data) < 23 && raw.startsWith("--sun-misc-unsafe-memory-access")) {
            return;
        }
        cmd.add(substitute(raw, data, account));
    }

    /**
     * Major version of the JVM that will actually run the game. Prefer the
     * local runtime when that is the chosen exe; otherwise use the version
     * manifest requirement (Mojang runtimes match that major).
     */
    private static int javaMajorForGame(LaunchData data) {
        try {
            Path exe = data.javaExe();
            if (exe != null) {
                Path local = JavaRuntime.localJava();
                if (Files.isRegularFile(local)
                        && exe.toAbsolutePath().normalize().equals(local.toAbsolutePath().normalize())) {
                    return JavaRuntime.localMajor();
                }
                Path installDir = AppUpdater.installDir();
                if (installDir != null) {
                    String os = System.getProperty("os.name", "").toLowerCase();
                    boolean win = os.contains("win");
                    String javaBin = win ? "bin/java.exe" : "bin/java";
                    Path[] candidates = {
                        installDir.resolve("jre").resolve(javaBin),
                        installDir.resolve("runtime").resolve(javaBin)
                    };
                    Path parent = installDir.getParent();
                    if (parent != null) {
                        candidates = new Path[]{
                            candidates[0], candidates[1],
                            parent.resolve("jre").resolve(javaBin),
                            parent.resolve("runtime").resolve(javaBin)
                        };
                    }
                    for (Path candidate : candidates) {
                        if (Files.isRegularFile(candidate)
                                && exe.toAbsolutePath().normalize().equals(candidate.toAbsolutePath().normalize())) {
                            return JavaRuntime.localMajor();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to required major
        }
        return requiredJavaMajor(data);
    }

    private String substitute(String input, LaunchData data, Account account) {
        return input
                .replace("${auth_player_name}", account.username())
                .replace("${version_name}", data.id())
                .replace("${game_directory}", data.gameDir().toString())
                .replace("${assets_root}", data.assetsRoot().toString())
                .replace("${assets_index_name}", data.assetIndexId() == null ? "legacy" : data.assetIndexId())
                .replace("${auth_uuid}", account.uuid())
                .replace("${auth_access_token}", account.minecraftToken())
                .replace("${user_type}", "msa")
                .replace("${version_type}", data.meta().type() == null ? "release" : data.meta().type())
                .replace("${natives_directory}", data.nativesDir().toString())
                .replace("${launcher_name}", "RavenClient")
                .replace("${launcher_version}", "1.0.0")
                .replace("${classpath}", String.join(File.pathSeparator, data.classpath()))
                .replace("${auth_xuid}", account.xuid() == null ? "" : account.xuid())
                .replace("${clientid}", CLIENT_ID);
    }

    private String javaExecutable() {
        String name = osId().equals("windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

    /** Java major version the version manifest wants (8, 17, 21...). */
    private static int requiredJavaMajor(LaunchData data) {
        try {
            JsonNode jv = data.meta().javaVersion();
            if (jv != null && jv.path("majorVersion").isInt()) return jv.path("majorVersion").asInt();
        } catch (Exception ignored) {
            // fall through
        }
        return 21;
    }

    /**
     * Throughput / FPS / boot-time tuned JVM flags. G1-specific knobs are only applied
     * on Java 17+ where the collector is best tuned; everything else is safe on 8+.
     */
    private static List<String> performanceJvmArgs(int javaMajor) {
        List<String> args = new ArrayList<>();
        if (javaMajor >= 17) {
            args.add("-XX:+UseG1GC");
            args.add("-XX:G1HeapRegionSize=4M");
        }
        args.add("-XX:MaxGCPauseMillis=200");
        args.add("-XX:+ParallelRefProcEnabled");
        args.add("-XX:+DisableExplicitGC");
        args.add("-XX:+PerfDisableSharedMem");
        args.add("-XX:ReservedCodeCacheSize=1G");
        args.add("-XX:+UseStringDeduplication");
        args.add("-XX:+OptimizeStringConcat");
        args.add("-XX:+UseCompressedOops");
        return args;
    }

    /** Evaluates a library/argument rules array. Versions that don't match are excluded. */
    static boolean rulesMatch(JsonNode rules) {
        if (rules == null || !rules.isArray() || rules.isEmpty()) return true;
        boolean result = false;
        for (JsonNode rule : rules) {
            if (ruleApplies(rule)) {
                result = !"disallow".equals(rule.path("action").asText("allow"));
            }
        }
        return result;
    }

    private static boolean ruleApplies(JsonNode rule) {
        JsonNode os = rule.path("os");
        if (os.isObject() && !os.isEmpty()) {
            String name = os.path("name").asText("");
            if (!name.isEmpty() && !name.equals(osId())) return false;
            String version = os.path("version").asText("");
            if (!version.isEmpty() && !System.getProperty("os.version", "").matches(version)) return false;
            String arch = os.path("arch").asText("");
            if (!arch.isEmpty() && !arch.equals(System.getProperty("os.arch", "amd64"))) return false;
        }
        JsonNode features = rule.path("features");
        if (features.isObject() && !features.isEmpty()) {
            // We launch without any extra features (no demo mode, no custom resolution),
            // so feature-gated rules never apply.
            return false;
        }
        return true;
    }

    private static String osId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    private static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (a) {
            case "x86", "i386", "i686" -> "32";
            default -> "64";
        };
    }

    private static void extractNatives(Path nativesJar, Path nativesDir) throws IOException {
        Files.createDirectories(nativesDir);
        try (ZipFile zip = new ZipFile(nativesJar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (!isNativeFile(entry.getName())) continue;
                Path out = nativesDir.resolve(entry.getName());
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean isNativeFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".dll") || lower.endsWith(".so")
                || lower.endsWith(".dylib") || lower.endsWith(".jnilib");
    }

    private static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (char c : s.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static final class StreamGobbler extends Thread {
        private final InputStream in;
        private final Listener listener;

        StreamGobbler(InputStream in, Listener listener) {
            this.in = in;
            this.listener = listener;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    listener.log(line);
                }
            } catch (IOException ignored) {
                // process closed its streams
            }
        }
    }
}
