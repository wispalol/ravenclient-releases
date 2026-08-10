package org.ravenclient.overlay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
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
    public Map<String, Map<String, Object>> settings = new LinkedHashMap<>();

    /** Windows VK code that opens/closes the bird nest menu. 0xA1 = Right Shift. */
    public int menuKey = Keys.VK_RSHIFT;

    public String activeProfile = "Default";
    public Map<String, Profile> profiles = new LinkedHashMap<>();

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

    /** A full snapshot of one layout/mod configuration, switchable at runtime. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        public Map<String, ElementConfig> elements = new LinkedHashMap<>();
        public Map<String, ModuleConfig> modules = new LinkedHashMap<>();
        public Map<String, Map<String, Object>> settings = new LinkedHashMap<>();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, ElementConfig>> EL_REF = new TypeReference<>() {};
    private static final TypeReference<Map<String, ModuleConfig>> MOD_REF = new TypeReference<>() {};
    @SuppressWarnings("rawtypes")
    private static final TypeReference<Map<String, Map<String, Object>>> SET_REF = new TypeReference<>() {};

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

    // ---------------- Profiles ----------------

    /** Creates the default profile from the current state if none exist. */
    public void ensureDefaultProfile() {
        if (profiles.isEmpty()) {
            profiles.put("Default", snapshot());
            activeProfile = "Default";
        } else if (activeProfile == null || !profiles.containsKey(activeProfile)) {
            activeProfile = profiles.keySet().iterator().next();
        }
    }

    public Profile snapshot() {
        Profile p = new Profile();
        p.elements = MAPPER.convertValue(elements, EL_REF);
        p.modules = MAPPER.convertValue(modules, MOD_REF);
        p.settings = MAPPER.convertValue(settings, SET_REF);
        return p;
    }

    public void apply(Profile p) {
        if (p == null) return;
        elements = p.elements == null ? new LinkedHashMap<>() : p.elements;
        modules = p.modules == null ? new LinkedHashMap<>() : p.modules;
        settings = p.settings == null ? new LinkedHashMap<>() : p.settings;
    }

    /** Persists the current state into the active profile. */
    public void saveCurrentProfile() {
        ensureDefaultProfile();
        profiles.put(activeProfile, snapshot());
    }

    public void createProfile(String name) {
        if (name == null || name.isBlank()) return;
        ensureDefaultProfile();
        profiles.put(name, snapshot());
        activeProfile = name;
    }

    public void duplicateProfile(String name, String newName) {
        Profile p = profiles.get(name);
        if (p == null || newName == null || newName.isBlank()) return;
        String unique = newName;
        int i = 2;
        while (profiles.containsKey(unique)) unique = newName + " " + i++;
        profiles.put(unique, MAPPER.convertValue(p, Profile.class));
        activeProfile = unique;
    }

    public void renameProfile(String oldName, String newName) {
        if (!profiles.containsKey(oldName) || newName == null || newName.isBlank()) return;
        String unique = newName;
        int i = 2;
        while (profiles.containsKey(unique) && !unique.equals(oldName)) unique = newName + " " + i++;
        Profile p = profiles.remove(oldName);
        profiles.put(unique, p);
        if (activeProfile.equals(oldName)) activeProfile = unique;
    }

    public void deleteProfile(String name) {
        if (profiles.size() <= 1) return; // keep at least one profile
        profiles.remove(name);
        if (activeProfile.equals(name)) activeProfile = profiles.keySet().iterator().next();
    }

    public void applyProfile(String name) {
        Profile p = profiles.get(name);
        if (p == null) return;
        apply(p);
        activeProfile = name;
    }

    /** Clears everything back to a fresh install state. */
    public void resetToDefaults() {
        elements.clear();
        modules.clear();
        settings.clear();
        profiles.clear();
        activeProfile = "Default";
    }

    public void exportProfile(String name, Path file) throws IOException {
        Profile p = profiles.get(name);
        if (p == null) return;
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), p);
    }

    public void importProfile(Path file) throws IOException {
        Profile p = MAPPER.readValue(file.toFile(), Profile.class);
        String base = file.getFileName().toString().replaceAll("(?i)\\.json$", "");
        String name = base;
        int i = 2;
        while (profiles.containsKey(name)) name = base + " " + i++;
        profiles.put(name, p);
        activeProfile = name;
        apply(p);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> moduleSettings(String id) {
        return settings.computeIfAbsent(id, k -> new LinkedHashMap<>());
    }
}
