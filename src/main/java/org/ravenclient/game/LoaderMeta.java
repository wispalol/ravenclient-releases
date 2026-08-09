package org.ravenclient.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ravenclient.config.LauncherConfig;
import org.ravenclient.meta.MinecraftMeta;
import org.ravenclient.util.Http;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Unified mod-loader manager, ported from OneLauncher's metadata store + install path.
 *
 * <p>All loaders (Fabric, Quilt, Forge, NeoForge) are described by the same metadata
 * API ({@code {METADATA_API}/{format}/v0/manifest.json}), whose manifests follow the
 * interfrost schema: a list of game versions, each carrying loader builds. Installing a
 * profile downloads the loader's <em>partial</em> version JSON and merges it into the
 * vanilla version manifest exactly like OneLauncher's {@code merge_partial_version}: the
 * loader's main class, arguments and libraries are layered on top of the vanilla ones and
 * the result is written as a standalone version manifest under {@code versions/{prefix}{mc}/}.
 * GameLauncher#prepare treats that file like any other version, so no profile inheritance
 * resolution is ever needed.
 */
public final class LoaderMeta {

    public static final String METADATA_API = "https://meta.polyfrost.org";
    private static final String FORMAT_VERSION = "v0";
    private static final long TTL_MS = 30 * 60 * 1000L;

    private static final ConcurrentMap<Loader, ManifestCache> cache = new ConcurrentHashMap<>();

    private LoaderMeta() {
    }

    private static final class ManifestCache {
        final LoaderManifest manifest;
        final long at;

        ManifestCache(LoaderManifest manifest, long at) {
            this.manifest = manifest;
            this.at = at;
        }
    }

    /** Returns the ravenclient profile id for a loader + Minecraft version, e.g. {@code fabric-1.21.11}. */
    public static String profileId(Loader loader, String mcVersion) {
        return loader.prefix() + mcVersion;
    }

    /** Returns loader profile ids already installed locally (under versions/). */
    public static List<String> installed(LauncherConfig config) {
        List<String> out = new ArrayList<>();
        for (Loader l : Loader.moddedLoaders()) out.addAll(installedFor(config, l));
        return out;
    }

    public static List<String> installedFor(LauncherConfig config, Loader loader) {
        Path versions = config.gameDir.resolve("versions");
        List<String> out = new ArrayList<>();
        if (loader.isModded() && Files.isDirectory(versions)) {
            try (var s = Files.newDirectoryStream(versions, loader.prefix() + "*")) {
                for (Path p : s) {
                    // Only a real profile counts: a version dir also exists purely to hold the
                    // profile's mods folder, and must not be mistaken for an installed loader.
                    if (Files.isDirectory(p)
                            && Files.isRegularFile(p.resolve(p.getFileName() + ".json"))) {
                        out.add(p.getFileName().toString());
                    }
                }
            } catch (IOException ignored) { }
        }
        return out;
    }

    public static boolean hasProfile(LauncherConfig config, Loader loader, String mcVersion) {
        return installedFor(config, loader).contains(profileId(loader, mcVersion));
    }

    /** The loader for an installed profile id, or null when it is a vanilla profile. */
    public static Loader loaderOf(String profileId) {
        for (Loader l : Loader.moddedLoaders()) {
            if (profileId != null && profileId.startsWith(l.prefix())) return l;
        }
        return null;
    }

    /** Fetches (with a short in-memory TTL) the unified manifest for a loader. */
    public static LoaderManifest manifest(Loader loader) throws IOException {
        if (!loader.isModded()) throw new IOException("Vanilla has no loader manifest");
        ManifestCache cached = cache.get(loader);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.at < TTL_MS) return cached.manifest;

        String url = METADATA_API + "/" + loader.formatName() + "/" + FORMAT_VERSION + "/manifest.json";
        LoaderManifest mf = Json.mapper().readValue(Http.getString(url), LoaderManifest.class);
        cache.put(loader, new ManifestCache(mf, now));
        return mf;
    }

    /**
     * Returns the loader builds available for {@code mcVersion} (empty when unsupported).
     *
     * <p>The manifest lists concrete game versions (usually without per-version builds) plus a
     * catch-all entry whose id is the {@code ${interfrost.gameVersion}} placeholder that
     * matches <em>any</em> Minecraft version. When the concrete entry carries no builds we
     * fall through to the placeholder so loader resolution keeps working (e.g. 1.21.11 has an
     * empty explicit entry but 251 builds via the catch-all).</p>
     */
    public static List<LoaderVersion> loadersFor(Loader loader, String mcVersion) throws IOException {
        List<LoaderVersion> emptyMatch = null;
        for (LoaderManifest.GameEntry entry : manifest(loader).gameVersions()) {
            if (!mcVersion.equals(LoaderManifest.substituteGameVersion(entry.id(), mcVersion))) continue;
            List<LoaderVersion> loaders = entry.loaders() == null ? List.of() : entry.loaders();
            if (!loaders.isEmpty()) return loaders;
            emptyMatch = loaders;
        }
        return emptyMatch == null ? List.of() : emptyMatch;
    }

    /** True when the loader ships a build for {@code mcVersion}. */
    public static boolean supported(Loader loader, String mcVersion) throws IOException {
        if (loader == Loader.VANILLA) return true;
        for (LoaderManifest.GameEntry entry : manifest(loader).gameVersions()) {
            if (mcVersion.equals(LoaderManifest.substituteGameVersion(entry.id(), mcVersion))) return true;
        }
        return false;
    }

    /** Loaders that have a build for {@code mcVersion}, vanilla first. */
    public static List<Loader> availableLoaders(String mcVersion) throws IOException {
        List<Loader> out = new ArrayList<>();
        out.add(Loader.VANILLA);
        for (Loader l : Loader.moddedLoaders()) {
            try {
                if (supported(l, mcVersion)) out.add(l);
            } catch (IOException ignored) {
                // A single loader's metadata being down must not hide the rest.
            }
        }
        return out;
    }

    /**
     * Picks a loader build for {@code mcVersion}, preferring the latest stable one — the
     * same rule OneLauncher applies in {@code get_loader_version}.
     */
    public static LoaderVersion resolve(Loader loader, String mcVersion, String preferredId) throws IOException {
        List<LoaderVersion> loaders = loadersFor(loader, mcVersion);
        if (loaders.isEmpty()) return null;
        if (preferredId != null) {
            for (LoaderVersion lv : loaders) if (preferredId.equals(lv.id())) return lv;
        }
        for (LoaderVersion lv : loaders) if (lv.stable()) return lv;
        return loaders.get(0);
    }

    /**
     * Installs a loader profile for {@code mcVersion}: fetches the latest stable loader
     * build, downloads its partial version JSON, merges it into the vanilla manifest and
     * writes the standalone profile under versions/.
     *
     * @return the installed profile id, e.g. {@code fabric-1.21.11}
     */
    public static String install(LauncherConfig config, Loader loader, String mcVersion,
                                 GameLauncher.Listener listener) throws IOException {
        if (listener != null) listener.status("Resolving " + loader.displayName() + " for " + mcVersion + "...");

        LoaderVersion lv = resolve(loader, mcVersion, null);
        if (lv == null) {
            throw new IOException("No " + loader.displayName() + " loader available for " + mcVersion);
        }

        try {
            if (listener != null) listener.log("Loader build: " + loader.displayName() + " " + lv.id());
            ObjectNode partial = (ObjectNode) Json.mapper().readTree(Http.getString(lv.url()));
            ObjectNode vanilla = (ObjectNode) Json.mapper().readTree(Http.getString(MinecraftMeta.versionJsonUrl(mcVersion)));
            ObjectNode merged = merge(loader, mcVersion, partial, vanilla);

            String id = profileId(loader, mcVersion);
            Path dir = config.gameDir.resolve("versions").resolve(id);
            Path file = dir.resolve(id + ".json");
            Files.createDirectories(dir);
            Files.write(file, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(merged));

            if (listener != null) {
                listener.log("Installed " + loader.displayName() + " profile " + id + " (loader " + lv.id() + ")");
                listener.status(loader.displayName() + " profile ready: " + id);
            }
            return id;
        } catch (Exception e) {
            if (listener != null) listener.log(loader.displayName() + " installation error: " + e);
            throw new IOException("Failed to install " + loader.displayName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Port of interfrost's {@code merge_partial_version}. The vanilla manifest is the base;
     * the loader's main class, arguments and libraries are layered on top. Vanilla libraries
     * that a loader re-ships (same {@code group:artifact}) are dropped so the loader's build
     * wins and nothing is duplicated on the classpath.
     */
    static ObjectNode merge(Loader loader, String mcVersion, ObjectNode partial, ObjectNode vanilla) {
        ObjectNode out = vanilla.deepCopy();
        out.put("id", profileId(loader, mcVersion));
        out.remove("inheritsFrom");

        if (partial.hasNonNull("mainClass")) {
            out.put("mainClass", partial.get("mainClass").asText());
        }

        JsonNode pArgs = partial.get("arguments");
        if (pArgs != null && pArgs.isObject()) {
            ObjectNode args = out.putObject("arguments");
            mergeArgumentList(args, "game", vanilla.get("arguments"), pArgs);
            mergeArgumentList(args, "jvm", vanilla.get("arguments"), pArgs);
        }

        if (partial.hasNonNull("minecraftArguments")) {
            out.put("minecraftArguments", partial.get("minecraftArguments").asText());
        } else {
            out.remove("minecraftArguments");
        }

        if (partial.hasNonNull("type")) {
            out.put("type", partial.get("type").asText());
        }

        ArrayNode libraries = out.putArray("libraries");
        Set<String> overridden = new HashSet<>();
        JsonNode pLibs = partial.get("libraries");
        if (pLibs != null && pLibs.isArray()) {
            for (JsonNode lib : pLibs) {
                if (lib.path("include_in_classpath").asBoolean(true)) {
                    String artifact = groupAndArtifact(lib.path("name").asText(""));
                    if (artifact != null) overridden.add(artifact);
                }
            }
        }
        JsonNode vLibs = vanilla.get("libraries");
        if (vLibs != null && vLibs.isArray()) {
            for (JsonNode lib : vLibs) {
                String artifact = groupAndArtifact(lib.path("name").asText(""));
                if (artifact != null && overridden.contains(artifact)) continue;
                libraries.add(lib.deepCopy());
            }
        }
        if (pLibs != null && pLibs.isArray()) {
            for (JsonNode lib : pLibs) {
                ObjectNode copy = (ObjectNode) lib.deepCopy();
                String name = copy.path("name").asText("");
                copy.put("name", name
                        .replace(LoaderManifest.DUMMY_GAME_VERSION, mcVersion)
                        .replace(LoaderManifest.LEGACY_DUMMY_GAME_VERSION, mcVersion));
                libraries.add(copy);
            }
        }

        return out;
    }

    private static void mergeArgumentList(ObjectNode out, String key, JsonNode vanillaArgs, JsonNode partialArgs) {
        ArrayNode arr = out.putArray(key);
        appendArguments(arr, vanillaArgs == null ? null : vanillaArgs.get(key));
        appendArguments(arr, partialArgs.get(key));
    }

    private static void appendArguments(ArrayNode out, JsonNode node) {
        if (node != null && node.isArray()) {
            for (JsonNode arg : node) out.add(arg.deepCopy());
        }
    }

    /** Returns {@code group:artifact} for a maven coordinate, or null when it cannot be parsed. */
    static String groupAndArtifact(String name) {
        if (name == null) return null;
        String[] parts = name.split(":");
        if (parts.length < 3) return null;
        return parts[0] + ":" + parts[1];
    }
}
