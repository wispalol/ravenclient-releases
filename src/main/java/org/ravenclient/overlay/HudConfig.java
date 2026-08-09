package org.ravenclient.overlay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HudConfig {

    public Map<String, ElementConfig> elements = new LinkedHashMap<>();
    public Map<String, ModuleConfig> modules = new LinkedHashMap<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ElementConfig {
        public double x = 0.01;
        public double y = 0.01;
        public double width = 0.12;
        public double height = 0.04;
        public double scale = 1.0;
        public double opacity = 1.0;
        public boolean visible = true;
        public int color = 0xFFFFFFFF;
        public boolean background = false;
        public int bgColor = 0x80000000;
        public boolean shadow = true;
        public boolean rounded = false;
        public int fontSize = 12;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModuleConfig {
        public boolean enabled = false;
        public String keybind = "";
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static HudConfig load(Path dir) {
        Path file = dir.resolve("hud-config.json");
        if (Files.exists(file)) {
            try {
                return MAPPER.readValue(file.toFile(), HudConfig.class);
            } catch (IOException ignored) {}
        }
        return new HudConfig();
    }

    public void save(Path dir) throws IOException {
        Files.createDirectories(dir);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("hud-config.json").toFile(), this);
    }

    public ElementConfig element(String id) {
        return elements.computeIfAbsent(id, k -> new ElementConfig());
    }

    public ModuleConfig module(String id) {
        return modules.computeIfAbsent(id, k -> new ModuleConfig());
    }
}
