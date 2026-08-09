package org.ravenclient.mod;

import java.util.List;

/**
 * A Modrinth project version, describing a build of a mod, the Minecraft versions /
 * mod loaders it supports, its files and its dependencies. The {@code dependencies} list
 * drives automatic installs of required libraries, exactly like OneLauncher's
 * {@code VersionDetail}.
 */
public record ModVersion(
        String id,
        String project_id,
        String name,
        String version_number,
        String version_type,
        List<String> game_versions,
        List<String> loaders,
        List<ModFile> files,
        List<ModDependency> dependencies,
        String changelog,
        String date_published,
        long downloads
) {

    /** The preferred file to download, falling back to the first entry. */
    public ModFile primaryFile() {
        if (files == null || files.isEmpty()) return null;
        for (ModFile f : files) if (f.primary()) return f;
        return files.get(0);
    }
}
