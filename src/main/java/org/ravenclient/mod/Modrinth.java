package org.ravenclient.mod;

import org.ravenclient.game.Loader;
import org.ravenclient.util.Http;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modrinth v2 client ported from OneLauncher's {@code ModrinthProvider}:
 *
 * <ul>
 *   <li>search with {@code facets} (project type + game versions + loader categories)
 *       so filtering happens server-side;</li>
 *   <li>version listing filtered by game version and loader;</li>
 *   <li>{@code /version_files} lookups so local jars can be identified by sha1
 *       for update detection;</li>
 *   <li>sha1-verified downloads of the primary file.</li>
 * </ul>
 */
public final class Modrinth {

    private static final String BASE = "https://api.modrinth.com/v2";

    private Modrinth() {
    }

    /**
     * Searches the catalog. Game version and loader are passed as facets, so results are
     * filtered server-side like OneLauncher's provider (which turns each dimension into an
     * OR-group of facets, AND-ed together).
     */
    public static ModSearch search(String query, String gameVersion, Loader loader,
                                   int offset, int limit) throws IOException {
        StringBuilder url = new StringBuilder(BASE).append("/search?query=")
                .append(enc(query == null || query.isBlank() ? "" : query))
                .append("&limit=").append(limit)
                .append("&offset=").append(offset);

        List<List<String>> groups = new ArrayList<>();
        groups.add(List.of("project_type:mod"));
        if (gameVersion != null && !gameVersion.isBlank()) {
            groups.add(List.of("versions:" + gameVersion));
        }
        if (loader != null && loader != Loader.VANILLA) {
            groups.add(List.of("categories:" + loader.modrinthName()));
        }
        if (groups.size() > 1) {
            url.append("&facets=").append(enc(Json.mapper().writeValueAsString(groups)));
        }
        return Json.mapper().readValue(Http.getString(url.toString()), ModSearch.class);
    }

    /** Lists the published versions of a project (slug or id) for a game version + loader. */
    public static List<ModVersion> versionsFor(String slug, String gameVersion, Loader loader) throws IOException {
        StringBuilder url = new StringBuilder(BASE).append("/project/")
                .append(enc(slug))
                .append("/version");
        boolean first = true;
        if (gameVersion != null && !gameVersion.isBlank()) {
            url.append("?game_versions=").append(enc("[\"" + gameVersion + "\"]"));
            first = false;
        }
        if (loader != null && loader != Loader.VANILLA) {
            url.append(first ? "?" : "&").append("loaders=").append(enc("[\"" + loader.modrinthName() + "\"]"));
        }
        String json = Http.getString(url.toString());
        return Json.mapper().readValue(json,
                Json.mapper().getTypeFactory().constructCollectionType(List.class, ModVersion.class));
    }

    /** Fetches a specific version by its Modrinth version id. */
    public static ModVersion version(String versionId) throws IOException {
        return Json.mapper().readValue(Http.getString(BASE + "/version/" + enc(versionId)), ModVersion.class);
    }

    /**
     * Identifies local files by their sha1 (POST /version_files). Used for update checks:
     * each installed jar is hashed and mapped back to its current Modrinth version.
     */
    public static Map<String, ModVersion> lookupByHashes(List<String> sha1s) throws IOException {
        if (sha1s == null || sha1s.isEmpty()) return Collections.emptyMap();
        Map<String, ModVersion> out = new HashMap<>();
        for (int i = 0; i < sha1s.size(); i += 20) {
            List<String> batch = sha1s.subList(i, Math.min(i + 20, sha1s.size()));
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("hashes", Json.mapper().writeValueAsString(batch));
            payload.put("algorithm", "sha1");
            Map<String, ModVersion> result = Json.mapper().readValue(
                    Http.postJson(BASE + "/version_files", Json.mapper().writeValueAsString(payload)),
                    Json.mapper().getTypeFactory().constructMapType(HashMap.class, String.class, ModVersion.class));
            out.putAll(result);
        }
        return out;
    }

    /**
     * Downloads {@code version}'s primary file into {@code modsDir}, verified against the
     * file's sha1. No-op (returns the path) when a matching file is already present.
     */
    public static Path install(Path modsDir, ModVersion version) throws IOException {
        ModFile file = version.primaryFile();
        if (file == null || file.url() == null || file.url().isBlank()) {
            throw new IOException("No downloadable file for " + version.name());
        }
        Files.createDirectories(modsDir);
        Path target = modsDir.resolve(file.filename());
        if (file.sha1() != null && Files.exists(target)) {
            try {
                if (sha1(target).equalsIgnoreCase(file.sha1())) return target;
            } catch (IOException ignored) {
                // unreadable/corrupt -> redownload
            }
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try {
            Http.download(file.url(), tmp);
            if (file.sha1() != null && !file.sha1().isBlank()
                    && !sha1(tmp).equalsIgnoreCase(file.sha1())) {
                throw new IOException("Checksum mismatch for " + file.filename());
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            if (!Files.exists(target)) Files.deleteIfExists(tmp);
        }
    }

    /** Returns the mod jars currently installed in {@code modsDir}. */
    public static List<Path> installed(Path modsDir) {
        if (!Files.isDirectory(modsDir)) return Collections.emptyList();
        try (var s = Files.newDirectoryStream(modsDir, "*.jar")) {
            List<Path> out = new ArrayList<>();
            for (Path p : s) out.add(p);
            return out;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String sha1(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return java.util.HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available", e);
        }
    }
}
