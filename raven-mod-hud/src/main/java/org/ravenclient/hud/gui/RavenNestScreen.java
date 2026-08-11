package org.ravenclient.hud.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.ravenclient.hud.config.HudConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RavenNestScreen extends Screen {

	private static final int SIDEBAR_W = 158;
	private static final int TAB_H = 22;
	private static final int PANEL_W = 880;
	private static final int PANEL_H = 620;

	private final HudConfig config;
	private final Screen parent;
	private int page = 0;
	private int scrollY = 0;
	private int totalContentH = 0;
	private boolean capturing = false;
	private String captureTarget = null;

	private String searchQuery = "";
	private String activeCategory = "All";

	private static final String[] PAGES = {"Browse", "Keybinds", "Profiles", "Settings"};
	private static final String[] CATEGORIES = {"All", "Visual", "PvP", "Utility", "Cosmetic"};

	private static final java.util.List<Module> MODULES = List.of(
			new Module("watermark", "Watermark", "Client logo + server IP in top-left", "Visual"),
			new Module("fps", "FPS Counter", "Shows current frames per second", "Utility"),
			new Module("coords", "Coordinates", "X Y Z position display", "Utility"),
			new Module("cps", "CPS Counter", "Clicks per second", "PvP"),
			new Module("ping", "Ping", "Server latency display", "Utility"),
			new Module("keystrokes", "Keystrokes", "Shows WASD + mouse clicks", "PvP"),
			new Module("direction", "Direction", "Compass heading N/S/E/W", "Utility"),
			new Module("speed", "Speed", "Blocks per second", "PvP"),
			new Module("targetHud", "Target HUD", "Entity info when looking at mobs/players", "PvP"),
			new Module("combo", "Combo Counter", "Tracks consecutive hits", "PvP"),
			new Module("session", "Session Stats", "Play time, kills, deaths this session", "Utility"),
			new Module("serverInfo", "Server Info", "Server brand, protocol, tps", "Utility"),
			new Module("clock", "Clock", "In-game time display", "Utility"),
			new Module("customText", "Custom Text", "Your own text line on HUD", "Cosmetic"),
			new Module("potions", "Potion Effects", "Active potion effect list", "Utility"),
			new Module("armor", "Armor Status", "Armor durability bar", "Utility"),
			new Module("itemDurability", "Item Durability", "Held item damage bar", "Utility"),
			new Module("heldItem", "Held Item", "Name of currently held item", "Utility"),
			new Module("hitCount", "Hit Counter", "Total hits on target", "PvP"),
			new Module("scoreboard", "Scoreboard", "Mirrors sidebar scoreboard", "Utility"),
			new Module("moduleStatus", "Module Status", "Shows enabled module list", "Utility")
	);

	private static class Module {
		final String id, name, desc, category;
		Module(String id, String name, String desc, String category) {
			this.id = id; this.name = name; this.desc = desc; this.category = category;
		}
	}

	public RavenNestScreen(HudConfig config) {
		super(Component.literal("Raven Nest"));
		this.config = config;
		this.parent = Minecraft.getInstance().screen;
	}

	@Override
	protected void init() {
		super.init();
		scrollY = 0;
		totalContentH = 0;
		capturing = false;
		captureTarget = null;

		int sidebarX = (width - PANEL_W) / 2;
		int sidebarY = (height - PANEL_H) / 2;

		for (int i = 0; i < PAGES.length; i++) {
			int p = i;
			Button sb = Button.builder(Component.literal(PAGES[p]), btn -> {
				page = p; scrollY = 0;
				Minecraft.getInstance().setScreen(new RavenNestScreen(config));
			}).bounds(sidebarX + 8, sidebarY + TAB_H + 4 + i * 34, SIDEBAR_W - 16, 28).build();
			addRenderableWidget(sb);
		}

		Button close = Button.builder(Component.literal("Close"), btn -> doClose())
				.bounds(sidebarX + 8, sidebarY + PANEL_H - 36, SIDEBAR_W - 16, 26).build();
		addRenderableWidget(close);

		int contentX = sidebarX + SIDEBAR_W;
		int contentY = sidebarY + TAB_H;
		int contentW = PANEL_W - SIDEBAR_W;

		int tabW = contentW / PAGES.length;
		for (int i = 0; i < PAGES.length; i++) {
			int p = i;
			Button tab = Button.builder(Component.literal(PAGES[p]), btn -> {
				page = p; scrollY = 0;
				Minecraft.getInstance().setScreen(new RavenNestScreen(config));
			}).bounds(contentX + i * tabW, contentY - TAB_H, tabW, TAB_H).build();
			addRenderableWidget(tab);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float tickDelta) {
		int sx = (width - PANEL_W) / 2;
		int sy = (height - PANEL_H) / 2;
		int cx = sx + SIDEBAR_W;
		int cy = sy + TAB_H;

		g.fill(0, 0, width, height, 0x88000000);
		g.fill(sx, sy, sx + PANEL_W, sy + PANEL_H, 0xEE12121e);
		g.fill(sx, sy, sx + PANEL_W, sy + 1, 0xFF7c6af7);
		g.fill(sx, sy + PANEL_H - 1, sx + PANEL_W, sy + PANEL_H, 0xFF7c6af7);
		g.fill(sx + SIDEBAR_W, sy + TAB_H, sx + SIDEBAR_W + 1, sy + PANEL_H, 0xFF333344);

		g.fill(sx + 10, sy + 28, sx + SIDEBAR_W - 10, sy + 29, 0xFF2a2a3a);
		renderSidebar(g, sx + 10, sy + 36);

		int cw = PANEL_W - SIDEBAR_W;
		for (int i = 1; i < PAGES.length; i++) {
			g.fill(cx + i * (cw / PAGES.length), sy + TAB_H - 1, cx + i * (cw / PAGES.length) + 1, sy + PANEL_H, 0xFF1a1a2e);
		}

		renderPage(g, cx + 8, cy + 8, cw - 16, PANEL_H - TAB_H - 16);

		if (capturing) {
			g.fill(0, 0, width, height, 0xCC000000);
			g.drawCenteredString(Minecraft.getInstance().font, "Press a key or mouse button... Esc = cancel, Delete = clear", width / 2, height / 2 - 10, 0xFFd5a83c);
		}

		super.render(g, mouseX, mouseY, tickDelta);
	}

	private void renderSidebar(GuiGraphics g, int x, int y) {
		Font f = Minecraft.getInstance().font;
		F.sb(g, f, "RAVEN NEST", x, y, 0xFF7c6af7);
		F.s(g, f, "\uD83E\uDDBA  bird nest", x, y + 14, 0xFF555555);
	}

	private void renderPage(GuiGraphics g, int x, int y, int w, int h) {
		switch (page) {
			case 0 -> renderBrowse(g, x, y, w, h);
			case 1 -> renderKeybinds(g, x, y, w, h);
			case 2 -> renderProfiles(g, x, y, w, h);
			case 3 -> renderSettings(g, x, y, w);
		}
	}

	private void renderBrowse(GuiGraphics g, int x, int y, int w, int h) {
		Font f = Minecraft.getInstance().font;
		F.sb(g, f, "Browse Mods", x, y, 0xFFe0e0ff);
		F.s(g, f, "Built-in overlay modules. Toggle them on/off.", x, y + 16, 0xFF888888);

		int searchY = y + 34;
		g.fill(x, searchY, x + w, searchY + 20, 0xFF1e1e30);
		g.fill(x, searchY, x + w, searchY + 1, 0xFF333355);
		g.fill(x, searchY + 19, x + w, searchY + 20, 0xFF333355);
		g.fill(x + w - 1, searchY, x + w, searchY + 20, 0xFF333355);
		F.s(g, f, searchQuery.isEmpty() ? "Search..." : searchQuery, x + 6, searchY + 6, searchQuery.isEmpty() ? 0xFF666666 : 0xFFe0e0ff);

		int chipY = searchY + 26;
		int chipW = w / CATEGORIES.length;
		for (int i = 0; i < CATEGORIES.length; i++) {
			boolean active = CATEGORIES[i].equals(activeCategory);
			g.fill(x + i * chipW, chipY, x + (i + 1) * chipW, chipY + 18, active ? 0xFF7c6af7 : 0xFF1e1e30);
			F.c(g, f, CATEGORIES[i], x + i * chipW + chipW / 2, chipY + 5, active ? 0xFFFFFFFF : 0xFF888888);
		}

		int listY = chipY + 22;
		int listH = h - (listY - y);
		List<Module> filtered = getFilteredModules();
		int rowH = 56;
		totalContentH = filtered.size() * rowH;
		scrollY = Math.max(0, Math.min(scrollY, Math.max(0, totalContentH - listH)));

		for (int i = 0; i < filtered.size(); i++) {
			renderModuleCard(g, filtered.get(i), x, listY + i * rowH - scrollY, w, rowH);
		}
	}

	private void renderModuleCard(GuiGraphics g, Module m, int x, int y, int w, int h) {
		if (y + h < 0 || y > 10000) return;
		boolean on = config.moduleVisibility.getOrDefault(m.id, false);
		g.fill(x, y, x + w, y + h, on ? 0xFF1e1e3a : 0xFF16161e);
		g.fill(x, y, x + w, y + 1, on ? 0xFF7c6af7 : 0xFF2a2a3a);
		g.fill(x + w - 1, y, x + w, y + h, 0xFF2a2a3a);
		g.fill(x, y + h - 1, x + w, y + h, 0xFF2a2a3a);

		F.sb(g, Minecraft.getInstance().font, m.name, x + 10, y + 8, on ? 0xFFe0e0ff : 0xFF888888);
		F.s(g, Minecraft.getInstance().font, m.desc, x + 10, y + 22, 0xFF555555);

		g.fill(x + w - 80, y + 8, x + w - 8, y + 28, on ? 0xFF7c6af7 : 0xFF333355);
		F.c(g, Minecraft.getInstance().font, on ? "ON" : "OFF", x + w - 44, y + 15, 0xFFFFFFFF);

		g.fill(x + w - 80, y + 34, x + w - 8, y + 48, 0xFF2a2a3a);
		int kb = config.keybinds.getOrDefault(m.id, 0);
		F.c(g, Minecraft.getInstance().font, kb == 0 ? "No keybind" : vkName(kb), x + w - 44, y + 38, 0xFF7c6af7);
	}

	private void renderKeybinds(GuiGraphics g, int x, int y, int w, int h) {
		Font f = Minecraft.getInstance().font;
		F.sb(g, f, "Keybinds", x, y, 0xFFe0e0ff);
		F.s(g, f, "Click a button, then press a key. Esc cancels, Delete clears.", x, y + 16, 0xFF888888);

		int rowY = y + 34;
		g.fill(x, rowY, x + w, rowY + 1, 0xFF2a2a3a);
		F.s(g, f, "MENU KEY", x + 8, rowY + 6, 0xFF888888);
		g.fill(x + w - 120, rowY + 2, x + w - 8, rowY + 28, 0xFF333355);
		F.c(g, f, vkName(config.menuKey), x + w - 64, rowY + 9, 0xFF7c6af7);
		F.s(g, f, "Press to open Raven Nest", x + 8, rowY + 22, 0xFF555555);

		int listY = rowY + 36;
		F.s(g, f, "MODULE KEYBINDS", x, listY, 0xFF888888);
		listY += 18;
		g.fill(x, listY, x + w, listY + 1, 0xFF2a2a3a);
		listY += 8;

		int rowH = 26;
		List<Module> list = MODULES.stream().filter(m -> !isCore(m.id)).collect(Collectors.toList());
		totalContentH = list.size() * rowH;
		int availH = h - (listY - y);
		scrollY = Math.max(0, Math.min(scrollY, Math.max(0, totalContentH - availH)));

		for (int i = 0; i < list.size(); i++) {
			Module m = list.get(i);
			int ry = listY + i * rowH - scrollY;
			if (ry + rowH < listY || ry > listY + availH) continue;

			g.fill(x, ry, x + w, ry + rowH - 2, 0xFF16161e);
			g.fill(x + w - 110, ry + 3, x + w - 8, ry + rowH - 5, 0xFF333355);
			F.c(g, f, vkName(config.keybinds.getOrDefault(m.id, 0)), x + w - 59, ry + 7, 0xFF7c6af7);
			F.s(g, f, m.name, x + 8, ry + 7, 0xFFcccccc);

			if (capturing && captureTarget != null && captureTarget.equals("mod_" + m.id)) {
				g.fill(x, ry, x + w, ry + rowH - 2, 0x66335522);
				F.s(g, f, "Press a key...", x + 8, ry + 7, 0xFFd5a83c);
			}
		}
	}

	private void renderProfiles(GuiGraphics g, int x, int y, int w, int h) {
		Font f = Minecraft.getInstance().font;
		F.sb(g, f, "Profiles", x, y, 0xFFe0e0ff);
		F.s(g, f, "Active: " + config.activeProfile, x, y + 16, 0xFF888888);

		int listY = y + 36;
		List<String> names = new ArrayList<>(config.profiles.keySet());
		totalContentH = names.size() * 40 + 40;
		int availH = h - (listY - y);
		scrollY = Math.max(0, Math.min(scrollY, Math.max(0, totalContentH - availH)));

		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			int ry = listY + i * 40 - scrollY;
			if (ry + 40 < listY || ry > listY + availH) continue;

			boolean active = name.equals(config.activeProfile);
			g.fill(x, ry, x + w, ry + 36, active ? 0xFF1e1e3a : 0xFF16161e);
			g.fill(x, ry, x + w, ry + 1, active ? 0xFF7c6af7 : 0xFF2a2a3a);

			F.sb(g, f, (active ? "\u25CF " : "  ") + name, x + 10, ry + 12, active ? 0xFF4caf50 : 0xFFcccccc);

			if (!active) {
				g.fill(x + w - 310, ry + 6, x + w - 218, ry + 28, 0xFF333355);
				F.c(g, f, "Use", x + w - 264, ry + 11, 0xFFcccccc);
				g.fill(x + w - 210, ry + 6, x + w - 118, ry + 28, 0xFF5a2e2e);
				F.c(g, f, "Delete", x + w - 164, ry + 11, 0xFFe05a5a);
			}
		}
	}

	private void renderSettings(GuiGraphics g, int x, int y, int w) {
		Font f = Minecraft.getInstance().font;
		F.sb(g, f, "Settings", x, y, 0xFFe0e0ff);

		int rowY = y + 34;
		g.fill(x, rowY, x + w, rowY + 44, 0xFF1e1e3a);
		g.fill(x, rowY, x + w, rowY + 1, 0xFF7c6af7);
		F.sb(g, f, "RavenClient HUD Mod", x + 12, rowY + 8, 0xFFe0e0ff);
		F.s(g, f, "In-game module system for RavenClient", x + 12, rowY + 22, 0xFF888888);

		int btnY = rowY + 56;
		g.fill(x, btnY, x + w, btnY + 1, 0xFF2a2a3a);
		btnY += 10;
		g.fill(x, btnY, x + w, btnY + 28, 0xFF333355);
		F.s(g, f, "Reset Mods & Layout to Defaults", x + 12, btnY + 9, 0xFFcccccc);
		btnY += 38;
		g.fill(x, btnY, x + w, btnY + 26, 0xFF5a2e2e);
		F.s(g, f, "Close Menu", x + 12, btnY + 8, 0xFFe05a5a);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		int sx = (width - PANEL_W) / 2;
		int sy = (height - PANEL_H) / 2;
		int cx = sx + SIDEBAR_W;
		int cy = sy + TAB_H;
		int contentX = cx + 8;
		int contentY = cy + 8;

		if (capturing) {
			if (button >= 1 && button <= 3) {
				finishCapture(new int[] {0x01, 0x02, 0x04}[button - 1]);
				return true;
			}
			return true;
		}

		boolean superResult = super.mouseClicked(mx, my, button);

		if (mx >= sx && mx <= sx + PANEL_W && my >= sy && my <= sy + PANEL_H) {
			if (page == 0 && my >= contentY + 34 + 26 + 22 && my <= sy + PANEL_H - 8) {
				int listY = contentY + 34 + 26 + 22;
				List<Module> filtered = getFilteredModules();
				int rowH = 56;
				int relY = (int) (my - listY + scrollY);
				int idx = relY / rowH;
				if (idx >= 0 && idx < filtered.size()) {
					Module m = filtered.get(idx);
					config.moduleVisibility.put(m.id, !config.moduleVisibility.getOrDefault(m.id, false));
					saveConfig();
				}
			}
			if (page == 0 && mx >= sx + PANEL_W - 70 && my >= contentY + 34 && my <= contentY + 54) {
				searchQuery = "";
			}
			if (page == 1) {
				int rowY2 = contentY + 34;
				if (mx >= sx + PANEL_W - 120 && mx <= sx + PANEL_W - 8 && my >= rowY2 + 2 && my <= rowY2 + 28) {
					startCapture("menuKey");
				}
			}
			if (page == 2) {
				int listY = contentY + 36;
				List<String> names = new ArrayList<>(config.profiles.keySet());
				int rowH = 40;
				for (int i = 0; i < names.size(); i++) {
					int ry = listY + i * rowH - scrollY;
					String name = names.get(i);
					if (my >= ry && my <= ry + 36 && mx >= sx + PANEL_W - 310 && mx <= sx + PANEL_W - 218) {
						if (!name.equals(config.activeProfile)) {
							config.activeProfile = name;
							saveConfig();
							Minecraft.getInstance().setScreen(new RavenNestScreen(config));
						}
					}
					if (my >= ry && my <= ry + 36 && mx >= sx + PANEL_W - 210 && mx <= sx + PANEL_W - 118) {
						config.profiles.remove(name);
						if (config.activeProfile.equals(name) && !config.profiles.isEmpty()) {
							config.activeProfile = config.profiles.keySet().iterator().next();
						}
						saveConfig();
						Minecraft.getInstance().setScreen(new RavenNestScreen(config));
					}
				}
			}
			if (page == 3) {
				int btnY = contentY + 34 + 56 + 10;
				if (my >= btnY && my <= btnY + 28) {
					config.moduleVisibility.clear();
					config.keybinds.clear();
					for (Module m : MODULES) config.moduleVisibility.put(m.id, true);
					config.profiles.clear();
					config.profiles.put("default", new LinkedHashMap<>());
					config.activeProfile = "default";
					saveConfig();
					Minecraft.getInstance().setScreen(new RavenNestScreen(config));
				}
			}
		}

		return superResult || (mx >= sx && mx <= sx + PANEL_W && my >= sy && my <= sy + PANEL_H);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		int sx = (width - PANEL_W) / 2;
		int sy = (height - PANEL_H) / 2;
		int cy = sy + TAB_H;
		int contentH = PANEL_H - TAB_H - 16;
		if (mx >= sx + SIDEBAR_W && mx <= sx + PANEL_W && my >= cy + 8 && my <= cy + 8 + contentH) {
			scrollY = (int) Math.max(0, Math.min(scrollY + dy * -16, Math.max(0, totalContentH - (contentH - 60))));
			return true;
		}
		return super.mouseScrolled(mx, my, dx, dy);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (capturing) {
			if (keyCode == 256) { cancelCapture(); return true; }
			if (keyCode == 259) { finishCapture(0); return true; }
			finishCapture(keyCode);
			return true;
		}

		if (keyCode >= 48 && keyCode <= 57 && searchQuery.length() < 30) {
			searchQuery += (char) ('0' + (keyCode - 48));
			scrollY = 0;
			return true;
		}
		if (keyCode >= 65 && keyCode <= 90 && searchQuery.length() < 30) {
			char c = (char) (modifiers == 2 ? keyCode : keyCode + 32);
			searchQuery += c;
			scrollY = 0;
			return true;
		}
		if (keyCode == 259 && !searchQuery.isEmpty()) {
			searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
			scrollY = 0;
			return true;
		}

		if (keyCode == 256) { doClose(); return true; }
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char c, int mods) {
		if (capturing) return true;
		if (c >= 32 && c <= 126 && searchQuery.length() < 30) {
			searchQuery += c;
			scrollY = 0;
			return true;
		}
		return super.charTyped(c, mods);
	}

	@Override
	public void removed() {
		saveConfig();
	}

	private void doClose() {
		saveConfig();
		Minecraft.getInstance().setScreen(parent);
	}

	private void startCapture(String target) {
		capturing = true;
		captureTarget = target;
	}

	private void finishCapture(int vk) {
		if (captureTarget != null) {
			if (captureTarget.equals("menuKey")) {
				config.menuKey = vk;
			} else if (captureTarget.startsWith("mod_")) {
				String modId = captureTarget.substring(4);
				if (vk > 0) config.keybinds.put(modId, vk);
				else config.keybinds.remove(modId);
			}
			saveConfig();
		}
		cancelCapture();
	}

	private void cancelCapture() {
		capturing = false;
		captureTarget = null;
	}

	private List<Module> getFilteredModules() {
		return MODULES.stream()
				.filter(m -> activeCategory.equals("All") || m.category.equals(activeCategory))
				.filter(m -> searchQuery.isEmpty() || m.name.toLowerCase().contains(searchQuery.toLowerCase()) || m.desc.toLowerCase().contains(searchQuery.toLowerCase()))
				.collect(Collectors.toList());
	}

	private void saveConfig() {
		try {
			config.save(Minecraft.getInstance().gameDirectory.toPath());
		} catch (IOException ignored) {}
	}

	private static boolean isCore(String id) {
		return Set.of("watermark", "fps", "coords").contains(id);
	}

	private static String vkName(int vk) {
		if (vk == 0) return "None";
		return switch (vk) {
			case 0xA1 -> "RShift";
			case 0xA0 -> "LShift";
			case 0x01 -> "M1";
			case 0x02 -> "M2";
			default -> "Key:" + vk;
		};
	}

	private static class F {
		static void s(GuiGraphics g, Font f, String text, int x, int y, int c) {
			if (text == null || text.isEmpty()) return;
			g.drawString(f, text, x, y, c);
		}
		static void sb(GuiGraphics g, Font f, String text, int x, int y, int c) {
			if (text == null || text.isEmpty()) return;
			g.drawString(f, text, x, y, c, true);
		}
		static void c(GuiGraphics g, Font f, String text, int cx, int y, int c) {
			if (text == null || text.isEmpty()) return;
			g.drawCenteredString(f, text, cx, y, c);
		}
	}
}
