package org.ravenclient.hud.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.ravenclient.hud.config.HudConfig;
import org.ravenclient.hud.render.HudElement;

import java.util.ArrayList;
import java.util.List;

public class RavenHudScreen extends Screen {

	private final HudConfig config;
	private final org.ravenclient.hud.render.HudRenderer renderer;
	private final Screen parent;
	private int currentTab = 0;
	private final String[] tabs = {"HUD", "Settings"};

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
			int idx = i;
			Button tabBtn = Button.builder(Component.literal(tabs[i]), btn -> {
				currentTab = idx;
				clearWidgets();
				init();
			}).bounds(i * tabWidth, 20, tabWidth, 20).build();
			addRenderableWidget(tabBtn);
		}

		if (currentTab == 0) buildHudTab();
		else buildSettingsTab();
	}

	private void buildHudTab() {
		int y = 60;
		int rowH = 24;
		for (HudElement el : renderer.getElements()) {
			var ec = config.element(el.id);
			Button toggle = Button.builder(Component.literal((ec.visible ? "[x] " : "[ ] ") + el.label), btn -> {
				ec.visible = !ec.visible;
				btn.setMessage(Component.literal((ec.visible ? "[x] " : "[ ] ") + el.label));
			}).bounds(20, y, 160, rowH).build();
			addRenderableWidget(toggle);
			y += rowH + 4;
		}
	}

	private void buildSettingsTab() {
		int y = 60;
		int rowH = 24;
		addRenderableWidget(Button.builder(Component.literal("Reset Positions"), btn -> {
			config.elements.clear();
			config.ensureDefaults();
			btn.setMessage(Component.literal("Reset!"));
		}).bounds(20, y, 160, rowH).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float tickDelta) {
		super.render(g, mouseX, mouseY, tickDelta);
		g.fill(0, 0, width, height, 0xCC000000);
		g.fill(0, 0, width, 1, 0xFF7c6af7);
		g.fill(0, 19, width, 1, 0xFF7c6af7);
		for (int i = 1; i < tabs.length; i++) {
			g.fill(i * (width / tabs.length), 0, 1, 20, 0xFF333344);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 346 || keyCode == 345) {
			Minecraft.getInstance().setScreen(parent);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
