package org.ravenclient.config;

import org.ravenclient.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Saved launch profiles ({@code profiles.json} in the launcher dir). Each profile pins a
 * Minecraft version to a loader — e.g. "1.21.11 Fabric" — and gets its own mods folder, so
 * users can keep strictly compatible mod sets per version (Fabric API for 1.21.11, etc.).
 */
public final class ProfileStore {

    private static final String PROFILES_FILE = "profiles.json";

    public record Profile(
            String id,
            String name,
            String version,
            String loader,
            String createdAt
    ) {
    }

    private record State(List<Profile> profiles) {
    }

    private ProfileStore() {
    }

    public static Path file(Path launcherDir) {
        return launcherDir.resolve(PROFILES_FILE);
    }

    public static List<Profile> load(Path launcherDir) {
        Path file = file(launcherDir);
        if (!Files.isRegularFile(file)) return new ArrayList<>();
        try {
            State s = Json.mapper().readValue(file.toFile(), State.class);
            return s == null || s.profiles() == null ? new ArrayList<>() : new ArrayList<>(s.profiles());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static Profile find(Path launcherDir, String id) {
        for (Profile p : load(launcherDir)) {
            if (p.id().equals(id)) return p;
        }
        return null;
    }

    /** Creates a new profile, generating a unique id from the name. */
    public static Profile create(Path launcherDir, String name, String version, String loader) throws IOException {
        List<Profile> profiles = load(launcherDir);
        String id = slugify(name);
        if (id.isBlank()) id = "profile";
        String base = id;
        int n = 1;
        String candidate = id;
        while (true) {
            final String probe = candidate;
            if (profiles.stream().noneMatch(p -> p.id().equals(probe))) break;
            candidate = base + "-" + n;
            n++;
        }
        id = candidate;
        Profile p = new Profile(id, name.isBlank() ? base : name, version,
                loader == null ? "Vanilla" : loader, java.time.Instant.now().toString());
        profiles.add(p);
        save(launcherDir, profiles);
        return p;
    }

    public static void delete(Path launcherDir, String id) throws IOException {
        List<Profile> profiles = new ArrayList<>(load(launcherDir));
        profiles.removeIf(p -> p.id().equals(id));
        save(launcherDir, profiles);
    }

    public static void save(Path launcherDir, List<Profile> profiles) throws IOException {
        Files.createDirectories(launcherDir);
        Files.write(file(launcherDir),
                Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(new State(profiles)));
    }

    private static String slugify(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
