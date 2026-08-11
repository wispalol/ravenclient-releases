package org.ravenclient.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LauncherConfig {

    private static final String CONFIG_FILE = "config.json";

    public final Path launcherDir;
    public final Path gameDir;
    public int memoryMb = 4096;
    public String selectedVersion = null;
    public String selectedProfile = null;

    public int windowWidth = 1280;
    public int windowHeight = 720;
    public boolean fullscreen = false;
    public boolean discordRpc = true;
    public boolean hudOverlay = false;
    public boolean autoUpdate = true;
    public boolean launchOnStartup = false;
    public String jvmArgs = "";
    public String gameArgs = "";

    public List<String> quickJoinServers = new ArrayList<>();

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
            if (node.hasNonNull("windowWidth")) cfg.windowWidth = node.get("windowWidth").asInt(1280);
            if (node.hasNonNull("windowHeight")) cfg.windowHeight = node.get("windowHeight").asInt(720);
            if (node.hasNonNull("fullscreen")) cfg.fullscreen = node.get("fullscreen").asBoolean(false);
            if (node.hasNonNull("discordRpc")) cfg.discordRpc = node.get("discordRpc").asBoolean(true);
            if (node.hasNonNull("hudOverlay")) cfg.hudOverlay = node.get("hudOverlay").asBoolean(false);
            if (node.hasNonNull("autoUpdate")) cfg.autoUpdate = node.get("autoUpdate").asBoolean(true);
            if (node.hasNonNull("launchOnStartup")) cfg.launchOnStartup = node.get("launchOnStartup").asBoolean(false);
            if (node.hasNonNull("jvmArgs")) cfg.jvmArgs = node.get("jvmArgs").asText("");
            if (node.hasNonNull("gameArgs")) cfg.gameArgs = node.get("gameArgs").asText("");
            JsonNode quickServers = node.get("quickJoinServers");
            if (quickServers != null && quickServers.isArray()) {
                for (JsonNode s : quickServers) {
                    String v = s.asText();
                    if (v != null && !v.isBlank()) cfg.quickJoinServers.add(v);
                }
            }
        }
        return cfg;
    }

    public void save() throws IOException {
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("memoryMb", memoryMb);
        node.put("selectedVersion", selectedVersion == null ? "" : selectedVersion);
        node.put("selectedProfile", selectedProfile == null ? "" : selectedProfile);
        node.put("windowWidth", windowWidth);
        node.put("windowHeight", windowHeight);
        node.put("fullscreen", fullscreen);
        node.put("discordRpc", discordRpc);
        node.put("hudOverlay", hudOverlay);
        node.put("autoUpdate", autoUpdate);
        node.put("launchOnStartup", launchOnStartup);
        node.put("jvmArgs", jvmArgs == null ? "" : jvmArgs);
        node.put("gameArgs", gameArgs == null ? "" : gameArgs);
        ArrayNode servers = node.putArray("quickJoinServers");
        for (String s : quickJoinServers) servers.add(s);
        Files.write(launcherDir.resolve(CONFIG_FILE),
                Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(node));
    }
}
