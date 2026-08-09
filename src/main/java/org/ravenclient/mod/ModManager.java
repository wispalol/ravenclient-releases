package org.ravenclient.mod;

import org.ravenclient.game.Loader;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Installed-mod tracking for a single {@code mods} directory, mirroring OneLauncher's
 * package store: every browser install is recorded in a {@code mods.json} sidecar (keyed
 * by Modrinth project id) so removal, updates and dependency handling stay reliable even
 * when jars are re-downloaded.
 *
 * <p>Required dependencies are pulled in automatically and recursively (OneLauncher
 * installs every {@code DependencyKind::Required} entry); a dependency already present or
 * visited is never re-downloaded, and pinned {@code version_id}s are honoured.
 */
public final class ModManager {

    private static final String STATE_FILE = "mods.json";

    private ModManager() {
    }

    public record InstalledMod(
            String projectId,
            String versionId,
            String name,
            String fileName,
            String sha1,
            String gameVersion,
            String loader,
            String installedAt
    ) {
    }

    public record InstallResult(InstalledMod mod, Path file) {
    }

    private record State(List<InstalledMod> mods) {
    }

    /** Reads the installed-mod state of a mods directory (empty when none exists). */
    public static List<InstalledMod> installed(Path modsDir) {
        Path state = modsDir.resolve(STATE_FILE);
        if (!Files.isRegularFile(state)) return new ArrayList<>();
        try {
            State s = Json.mapper().readValue(state.toFile(), State.class);
            return s == null || s.mods() == null ? new ArrayList<>() : new ArrayList<>(s.mods());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static boolean isInstalled(Path modsDir, String projectId) {
        return installed(modsDir).stream().anyMatch(m -> m.projectId().equals(projectId));
    }

    public static InstalledMod find(Path modsDir, String projectId) {
        for (InstalledMod m : installed(modsDir)) {
            if (m.projectId().equals(projectId)) return m;
        }
        return null;
    }

    /**
     * Installs a version and its required dependencies. Returns one result per file that
     * was actually downloaded (dependencies already present are skipped).
     */
    public static List<InstallResult> install(Path modsDir, ModVersion version,
                                              String gameVersion, Loader loader) throws IOException {
        List<InstallResult> out = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        installRecursive(modsDir, version, gameVersion, loader, visited, out);
        return out;
    }

    private static void installRecursive(Path modsDir, ModVersion version, String gameVersion,
                                         Loader loader, Set<String> visited, List<InstallResult> out)
            throws IOException {
        if (version == null || version.project_id() == null || version.project_id().isBlank()) return;
        if (!visited.add(version.project_id())) return;
        if (isInstalled(modsDir, version.project_id())) return;

        Path file = Modrinth.install(modsDir, version);
        InstalledMod mod = new InstalledMod(
                version.project_id(),
                version.id(),
                version.name(),
                file.getFileName().toString(),
                Modrinth.sha1(file),
                gameVersion,
                loader == null ? "minecraft" : loader.modrinthName(),
                Instant.now().toString());
        List<InstalledMod> list = installed(modsDir);
        list.removeIf(m -> m.projectId().equals(mod.projectId()));
        list.add(mod);
        save(modsDir, list);
        out.add(new InstallResult(mod, file));

        if (version.dependencies() != null) {
            for (ModDependency dep : version.dependencies()) {
                if (!dep.required()) continue;
                ModVersion depVersion = null;
                if (dep.version_id() != null && !dep.version_id().isBlank()) {
                    try {
                        depVersion = Modrinth.version(dep.version_id());
                    } catch (IOException ignored) {
                        // pinned version vanished -> fall back to a fresh resolution
                    }
                }
                if (depVersion == null && dep.project_id() != null && !dep.project_id().isBlank()) {
                    List<ModVersion> available = Modrinth.versionsFor(dep.project_id(), gameVersion, loader);
                    if (!available.isEmpty()) depVersion = available.get(0);
                }
                installRecursive(modsDir, depVersion, gameVersion, loader, visited, out);
            }
        }
    }

    /** Removes a mod (metadata + jar) from the mods directory. */
    public static void remove(Path modsDir, String projectId) throws IOException {
        InstalledMod removed = null;
        List<InstalledMod> list = installed(modsDir);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).projectId().equals(projectId)) {
                removed = list.remove(i);
                break;
            }
        }
        save(modsDir, list);
        if (removed != null && removed.fileName() != null && !removed.fileName().isBlank()) {
            Files.deleteIfExists(modsDir.resolve(removed.fileName()));
        }
    }

    /**
     * True when a newer build of the installed mod exists for the same game version +
     * loader. Modrinth's filtered listing returns newest-first, so the head entry is the
     * candidate.
     */
    public static boolean hasUpdate(Path modsDir, InstalledMod mod,
                                    String gameVersion, Loader loader) throws IOException {
        List<ModVersion> versions = Modrinth.versionsFor(mod.projectId(), gameVersion, loader);
        if (versions.isEmpty()) return false;
        return !versions.get(0).id().equals(mod.versionId());
    }

    private static void save(Path modsDir, List<InstalledMod> mods) throws IOException {
        Files.createDirectories(modsDir);
        Files.write(modsDir.resolve(STATE_FILE),
                Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(new State(mods)));
    }
}
