package org.ravenclient.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LauncherConfig {

    private static final String CONFIG_FILE = "config.json";

    public final Path launcherDir;
    public final Path gameDir;
    public int memoryMb = 4096;
    public String selectedVersion = null;
    public String selectedProfile = null;

    private LauncherConfig(Path launcherDir) {
        this.launcherDir = launcherDir;
        this.gameDir = launcherDir.resolve("minecraft");
    }

    public static LauncherConfig load(Path dir) throws IOException {
        Files.createDirectories(dir);
        LauncherConfig cfg = new LauncherConfig(dir);
        Files.createDirectories(cfg.gameDir);

        Path file = dir.resolve(CONFIG_FILE);
        if (Files.exists(file)) {
            ObjectNode node = (ObjectNode) Json.mapper().readTree(file.toFile());
            if (node.hasNonNull("memoryMb")) cfg.memoryMb = node.get("memoryMb").asInt(4096);
            if (node.hasNonNull("selectedVersion") && !node.get("selectedVersion").asText().isEmpty()) {
                cfg.selectedVersion = node.get("selectedVersion").asText();
            }
            if (node.hasNonNull("selectedProfile") && !node.get("selectedProfile").asText().isEmpty()) {
                cfg.selectedProfile = node.get("selectedProfile").asText();
            }
        }
        return cfg;
    }

    public void save() throws IOException {
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("memoryMb", memoryMb);
        node.put("selectedVersion", selectedVersion == null ? "" : selectedVersion);
        node.put("selectedProfile", selectedProfile == null ? "" : selectedProfile);
        Files.write(launcherDir.resolve(CONFIG_FILE),
                Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(node));
    }
}
