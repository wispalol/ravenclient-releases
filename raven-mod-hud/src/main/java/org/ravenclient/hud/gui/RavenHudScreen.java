package org.ravenclient.hud.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.ravenclient.hud.RavenHudMod;
import org.ravenclient.hud.config.HudConfig;
import org.ravenclient.hud.render.HudElement;

import java.util.ArrayList;
import java.util.List;

public class RavenHudScreen extends Screen {

	private final HudConfig config;
	private final org.ravenclient.hud.render.HudRenderer renderer;
	private Screen parent;
	private int currentTab = 0;
	private final String[] tabs = {"HUD", "Mods", "Settings"};

	public RavenHudScreen(HudConfig config, org.ravenclient.hud.render.HudRenderer renderer) {
		super(Component.literal("RavenClient HUD"));
		this.config = config;
		this.renderer = renderer;
		this.parent = Minecraft.getInstance().screen;
	}

	@Override
	protected void init() {
		super.init();

		int tabWidth = width / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			int tabIndex = i;
			Button tabBtn = Button.builder(Component.literal(tabs[i]), btn -> {
				currentTab = tabIndex;
				clearWidgets();
				init();
			}).bounds(i * tabWidth, 20, tabWidth, 20).build();
			addRenderableWidget(tabBtn);
		}

		if (currentTab == 0) buildHudTab();
		else if (currentTab == 1) buildModsTab();
		else buildSettingsTab();
	}

	private void buildHudTab() {
		int y = 60;
		int rowHeight = 24;
		for (HudElement el : renderer.getElements()) {
			HudConfig.ElementConfig ec = config.element(el.id);

			Button toggle = Button.builder(Component.literal((ec.visible ? "[x] " : "[ ] ") + el.label), btn -> {
				ec.visible = !ec.visible;
				btn.setMessage(Component.literal((ec.visible ? "[x] " : "[ ] ") + el.label));
			}).bounds(20, y, 160, rowHeight).build();
			addRenderableWidget(toggle);

			y += rowHeight + 4;
		}
	}

	private void buildModsTab() {
		int y = 60;
		int rowHeight = 24;

		List<ModuleInfo> modules = new ArrayList<>(List.of(
			new ModuleInfo("fullbright", "Fullbright", "See clearly in the dark"),
			new ModuleInfo("zoom", "Zoom", "Zoom your view"),
			new ModuleInfo("crosshair", "Custom Crosshair", "Replace default crosshair"),
			new ModuleInfo("fov", "FOV Changer", "Change field of view"),
			new ModuleInfo("blockoverlay", "Block Overlay", "Highlight looked-at block")
		));

		for (ModuleInfo mod : modules) {
			HudConfig.ModuleConfig mc = config.module(mod.id);
			Button toggle = Button.builder(Component.literal((mc.enabled ? "[x] " : "[ ] ") + mod.name + " - " + mod.desc), btn -> {
				mc.enabled = !mc.enabled;
				btn.setMessage(Component.literal((mc.enabled ? "[x] " : "[ ] ") + mod.name + " - " + mod.desc));
			}).bounds(20, y, 300, rowHeight).build();
			addRenderableWidget(toggle);
			y += rowHeight + 4;
		}
	}

	private void buildSettingsTab() {
		int y = 60;
		int rowHeight = 24;

		addRenderableWidget(Button.builder(Component.literal("Reset All HUD Positions"), btn -> {
			config.resetToDefaults();
			btn.setMessage(Component.literal("Reset!"));
		}).bounds(20, y, 200, rowHeight).build());

		y += rowHeight + 8;
		addRenderableWidget(Button.builder(Component.literal("Export Profile"), btn -> {}).bounds(20, y, 120, rowHeight).build());

		addRenderableWidget(Button.builder(Component.literal("Import Profile"), btn -> {}).bounds(150, y, 120, rowHeight).build());
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		super.render(guiGraphics, mouseX, mouseY, delta);

		guiGraphics.fill(0, 0, width, height, 0xCC000000);
		guiGraphics.fill(0, 0, width, 1, 0xFF7c6af7);
		guiGraphics.fill(0, 19, width, 1, 0xFF7c6af7);

		for (int i = 1; i < tabs.length; i++) {
			guiGraphics.fill(i * (width / tabs.length), 0, 1, 20, 0xFF333344);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			try {
				config.save();
			} catch (Exception ignored) {}
			Minecraft.getInstance().setScreen(parent);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 1 && mouseY < 20) {
			currentTab = (int)(mouseX / (width / (double)tabs.length));
			clearWidgets();
			init();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private record ModuleInfo(String id, String name, String desc) {}
}
