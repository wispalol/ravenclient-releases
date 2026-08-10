package org.ravenclient.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;

public class HudConfig {

	public Map<String, ElementConfig> elements = new LinkedHashMap<>();
	public Map<String, ModuleConfig> modules = new LinkedHashMap<>();
	public Map<String, Map<String, Object>> settings = new LinkedHashMap<>();

	public int menuKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
	public String activeProfile = "Default";
	public Map<String, Profile> profiles = new LinkedHashMap<>();

	@SuppressWarnings("unused")
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

	@SuppressWarnings("unused")
	public static class ModuleConfig {
		public boolean enabled = false;
		public String keybind = "";
	}

	@SuppressWarnings("unused")
	public static class Profile {
		public Map<String, ElementConfig> elements = new LinkedHashMap<>();
		public Map<String, ModuleConfig> modules = new LinkedHashMap<>();
		public Map<String, Map<String, Object>> settings = new LinkedHashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "ravenclient-hud.json";
	private static final Type EL_TYPE = new TypeToken<Map<String, ElementConfig>>() {}.getType();
	private static final Type MOD_TYPE = new TypeToken<Map<String, ModuleConfig>>() {}.getType();
	private static final Type SET_TYPE = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();

	public static HudConfig load() {
		Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(FILE_NAME);
		if (Files.exists(file)) {
			try {
				String json = Files.readString(file);
				HudConfig cfg = GSON.fromJson(json, HudConfig.class);
				if (cfg == null) return new HudConfig();
				return cfg;
			} catch (IOException ignored) {}
		}
		return new HudConfig();
	}

	public void save() throws IOException {
		Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(FILE_NAME);
		Files.createDirectories(file.getParent());
		String json = GSON.toJson(this);
		Files.writeString(file, json);
	}

	public ElementConfig element(String id) {
		return elements.computeIfAbsent(id, k -> new ElementConfig());
	}

	public ModuleConfig module(String id) {
		return modules.computeIfAbsent(id, k -> new ModuleConfig());
	}

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
		p.elements = new LinkedHashMap<>(elements);
		p.modules = new LinkedHashMap<>(modules);
		p.settings = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Object>> e : settings.entrySet()) {
			p.settings.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
		}
		return p;
	}

	public void apply(Profile p) {
		if (p == null) return;
		elements = p.elements == null ? new LinkedHashMap<>() : new LinkedHashMap<>(p.elements);
		modules = p.modules == null ? new LinkedHashMap<>() : new LinkedHashMap<>(p.modules);
		settings = p.settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>();
		if (p.settings != null) {
			for (Map.Entry<String, Map<String, Object>> e : p.settings.entrySet()) {
				settings.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
			}
		}
	}

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

	public void applyProfile(String name) {
		Profile p = profiles.get(name);
		if (p == null) return;
		apply(p);
		activeProfile = name;
	}

	public void deleteProfile(String name) {
		if (profiles.size() <= 1) return;
		profiles.remove(name);
		if (activeProfile.equals(name)) activeProfile = profiles.keySet().iterator().next();
	}

	public void resetToDefaults() {
		elements.clear();
		modules.clear();
		settings.clear();
		profiles.clear();
		activeProfile = "Default";
	}

	public Map<String, Object> moduleSettings(String id) {
		return settings.computeIfAbsent(id, k -> new LinkedHashMap<>());
	}
}
