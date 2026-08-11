package org.ravenclient.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;

public class HudConfig {

	public Map<String, ElementConfig> elements = new LinkedHashMap<>();
	public Map<String, String> notes = new LinkedHashMap<>();

	public Map<String, Boolean> moduleVisibility = new LinkedHashMap<>();
	public Map<String, Integer> keybinds = new LinkedHashMap<>();
	public int menuKey = 0xA1;
	public Map<String, Map<String, Boolean>> profiles = new LinkedHashMap<>();
	public String activeProfile = "default";

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
		public int fontSize = 12;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "ravenclient-hud.json";

	public static HudConfig load(java.nio.file.Path gameDir) {
		java.nio.file.Path file = gameDir.resolve(FILE_NAME);
		if (java.nio.file.Files.exists(file)) {
			try (java.io.BufferedReader r = java.nio.file.Files.newBufferedReader(file)) {
				HudConfig cfg = GSON.fromJson(r, HudConfig.class);
				if (cfg != null) return cfg;
			} catch (IOException ignored) {}
		}
		return new HudConfig();
	}

	public void save(java.nio.file.Path gameDir) throws IOException {
		java.nio.file.Path file = gameDir.resolve(FILE_NAME);
		java.nio.file.Files.createDirectories(file.getParent());
		java.nio.file.Files.writeString(file, GSON.toJson(this));
	}

	public ElementConfig element(String id) {
		return elements.computeIfAbsent(id, k -> new ElementConfig());
	}

	public void ensureDefaults() {
		if (elements.isEmpty()) {
			element("watermark");
			element("fps");
			element("coords");
		}
	}
}
