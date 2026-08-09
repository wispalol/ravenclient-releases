package org.ravenclient.meta;

import org.ravenclient.util.Http;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.util.List;

public final class MinecraftMeta {

    public static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String LIBRARIES_URL = "https://libraries.minecraft.net/";
    public static final String ASSETS_URL = "https://resources.download.minecraft.net/";

    private static volatile VersionManifest cached;
    private static volatile long cachedAt;

    private MinecraftMeta() {
    }

    public static synchronized VersionManifest manifest() throws IOException {
        long now = System.currentTimeMillis();
        if (cached == null || now - cachedAt > 3_600_000L) {
            cached = Json.mapper().readValue(Http.getString(VERSION_MANIFEST_URL), VersionManifest.class);
            cachedAt = now;
        }
        return cached;
    }

    public static String versionJsonUrl(String id) throws IOException {
        VersionManifest mf = manifest();
        for (VersionManifest.Version v : mf.versions()) {
            if (v.id().equals(id)) return v.url();
        }
        throw new IOException("Unknown Minecraft version: " + id);
    }

    public static List<String> versionIds() throws IOException {
        return manifest().versions().stream().map(VersionManifest.Version::id).toList();
    }
}
