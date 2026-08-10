package org.ravenclient.overlay;

import com.sun.jna.platform.win32.User32;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.ravenclient.overlay.elements.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the transparent always-on-top overlay window.
 * Spawned by the launcher after Minecraft starts.
 */
public class OverlayManager {

    private static Path configDirectory;

    private final Stage overlayStage;
    private final StackPane root;
    private final Canvas hudCanvas;
    private final List<HudElement> elements;
    private final HudConfig config;
    private final HudRenderer renderer;
    private final ScheduledExecutorService keybindPoller;

    private volatile boolean guiOpen = false;
    private volatile boolean editMode = false;
    private final Map<Integer, Boolean> lastKeyState = new HashMap<>();
    private Process gameProcess;

    public static Path configDir() { return configDirectory; }

    public OverlayManager(Path launcherDir) {
        this(launcherDir, null);
    }

    public OverlayManager(Path launcherDir, Process gameProcess) {
        configDirectory = launcherDir;
        this.gameProcess = gameProcess;
        config = HudConfig.load(launcherDir);

        elements = new ArrayList<>(List.of(
                new WatermarkElement(),
                new FpsElement(),
                new CpsElement(),
                new PingElement(),
                new CoordsElement(),
                new DirectionElement(),
                new SpeedElement(),
                new KeystrokesElement(),
                new TargetHudElement(),
                new ComboElement(),
                new SessionStatsElement(),
                new ServerInfoElement(),
                new ClockElement(),
                new CustomTextElement(),
                new PotionEffectsElement(),
                new ArmorStatusElement(),
                new ItemDurabilityElement(),
                new HeldItemElement(),
                new HitCounterElement(),
                new ScoreboardElement(),
                new ModuleStatusElement()
        ));

        double y = 0.01;
        for (HudElement el : elements) {
            el.bind(config);
            if (!config.elements.containsKey(el.id)) {
                el.cfg.x = 0.01;
                el.cfg.y = y;
                y += 0.035;
            }
        }
        applyModuleVisibility();
        config.ensureDefaultProfile();

        overlayStage = new Stage(StageStyle.TRANSPARENT);
        overlayStage.setAlwaysOnTop(true);
        overlayStage.setTitle("RavenOverlay");
        overlayStage.setResizable(false);

        Screen screen = Screen.getPrimary();
        double sw = screen.getBounds().getWidth();
        double sh = screen.getBounds().getHeight();

        hudCanvas = new Canvas(sw, sh);
        root = new StackPane(hudCanvas);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, sw, sh, Color.TRANSPARENT);
        overlayStage.setScene(scene);
        overlayStage.setX(0);
        overlayStage.setY(0);
        overlayStage.setWidth(sw);
        overlayStage.setHeight(sh);
        overlayStage.show();
        System.out.println("[RAVEN] Overlay stage shown, size=" + sw + "x" + sh);

        setClickThrough(true);

        renderer = new HudRenderer(hudCanvas, elements);
        renderer.start();

        if (gameProcess != null) {
            startLogParser();
        }

        keybindPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "raven-keybind");
            t.setDaemon(false);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });
        keybindPoller.scheduleAtFixedRate(this::pollKeybind, 100, 16, TimeUnit.MILLISECONDS);
        System.out.println("[RAVEN] OverlayManager started, keybind poller running at ~60Hz");
    }

    private void pollKeybind() {
        try {
            int menuVk = config.menuKey;
            if (menuVk != 0) {
                boolean down = isKeyDown(menuVk);
                boolean prev = lastKeyState.computeIfAbsent(menuVk, k -> false);
                if (down && !prev) {
                    System.out.println("[RAVEN] Menu key (" + Keys.name(menuVk) + ") pressed, toggling GUI");
                    Platform.runLater(this::toggleGui);
                }
                lastKeyState.put(menuVk, down);
            }
            // Module keybinds only fire while the menu is closed, so browsing
            // or editing never accidentally flips a mod.
            if (!guiOpen && !editMode) pollModuleKeybinds();
        } catch (Exception e) {
            System.out.println("[RAVEN] poll error: " + e.getMessage());
        }
    }

    private void pollModuleKeybinds() {
        for (ModuleRegistry.ClientModule m : ModuleRegistry.MODULES) {
            if (m.requiresMod()) continue;
            HudConfig.ModuleConfig mc = config.module(m.id());
            if (mc.keybind == null || mc.keybind.isBlank()) continue;
            int vk = Keys.code(mc.keybind);
            if (vk <= 0) continue;
            boolean down = isKeyDown(vk);
            boolean prev = lastKeyState.computeIfAbsent(vk, k -> false);
            lastKeyState.put(vk, down);
            if (down && !prev) {
                mc.enabled = !mc.enabled;
                System.out.println("[RAVEN] Keybind " + Keys.name(vk) + " toggled " + m.id() + " -> " + mc.enabled);
                Platform.runLater(this::onModulesChanged);
            }
        }
    }

    private void startLogParser() {
        if (gameProcess == null) return;
        Thread t = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(gameProcess.getInputStream()))) {
                String line;
                Pattern coordsPattern = Pattern.compile("X:\\s*([-0-9.]+).*Y:\\s*([-0-9.]+).*Z:\\s*([-0-9.]+)");
                Pattern pingPattern = Pattern.compile("Ping:\\s*([-0-9]+)");
                Pattern fpsPattern = Pattern.compile("FPS:\\s*([-0-9]+)");
                Pattern directionPattern = Pattern.compile("Facing:\\s*([A-Za-z]+)");
                Pattern speedPattern = Pattern.compile("Speed:\\s*([-0-9.]+)");
                Pattern serverPattern = Pattern.compile("Connected to\\s+(.+)");
                Pattern comboPattern = Pattern.compile("Combo:\\s*([-0-9]+)");
                Pattern killPattern = Pattern.compile("Kill:\\s*(.+)");
                Pattern deathPattern = Pattern.compile("Death:\\s*(.+)");
                Pattern potionPattern = Pattern.compile("Potion:\\s*(.+)");
                Pattern armorPattern = Pattern.compile("Armor:\\s*([-0-9.]+)%");
                Pattern durabilityPattern = Pattern.compile("Durability:\\s*([-0-9]+)/([-0-9]+)");
                Pattern heldItemPattern = Pattern.compile("Held:\\s*(.+)");
                Pattern scoreboardPattern = Pattern.compile("Scoreboard:\\s*(.+)");
                while ((line = reader.readLine()) != null) {
                    Matcher cm = coordsPattern.matcher(line);
                    if (cm.find()) {
                        try {
                            double x = Double.parseDouble(cm.group(1));
                            double y = Double.parseDouble(cm.group(2));
                            double z = Double.parseDouble(cm.group(3));
                            GameDataBridge.getInstance().setCoords(x, y, z);
                        } catch (Exception ignored) {}
                    }
                    Matcher pm = pingPattern.matcher(line);
                    if (pm.find()) {
                        try {
                            int ping = Integer.parseInt(pm.group(1));
                            GameDataBridge.getInstance().setPing(ping);
                        } catch (Exception ignored) {}
                    }
                    Matcher fm = fpsPattern.matcher(line);
                    if (fm.find()) {
                        try {
                            int fps = Integer.parseInt(fm.group(1));
                            GameDataBridge.getInstance().setFps(fps);
                        } catch (Exception ignored) {}
                    }
                    Matcher dm = directionPattern.matcher(line);
                    if (dm.find()) {
                        GameDataBridge.getInstance().setDirection(dm.group(1));
                    }
                    Matcher sm = speedPattern.matcher(line);
                    if (sm.find()) {
                        try {
                            double speed = Double.parseDouble(sm.group(1));
                            GameDataBridge.getInstance().setSpeed(speed);
                        } catch (Exception ignored) {}
                    }
                    Matcher svm = serverPattern.matcher(line);
                    if (svm.find()) {
                        GameDataBridge.getInstance().setServer(svm.group(1));
                    }
                    Matcher combom = comboPattern.matcher(line);
                    if (combom.find()) {
                        try {
                            int combo = Integer.parseInt(combom.group(1));
                            GameDataBridge.getInstance().setCombo(combo);
                        } catch (Exception ignored) {}
                    }
                    Matcher km = killPattern.matcher(line);
                    if (km.find()) {
                        GameDataBridge.getInstance().incrementKills();
                    }
                    Matcher dm2 = deathPattern.matcher(line);
                    if (dm2.find()) {
                        GameDataBridge.getInstance().incrementDeaths();
                    }
                    Matcher pom = potionPattern.matcher(line);
                    if (pom.find()) {
                        java.util.List<String> potions = new java.util.ArrayList<>();
                        potions.add(pom.group(1));
                        GameDataBridge.getInstance().setPotions(potions);
                    }
                    Matcher am = armorPattern.matcher(line);
                    if (am.find()) {
                        try {
                            double armor = Double.parseDouble(am.group(1));
                            GameDataBridge.getInstance().setArmorPercent(armor);
                        } catch (Exception ignored) {}
                    }
                    Matcher durm = durabilityPattern.matcher(line);
                    if (durm.find()) {
                        try {
                            int dur = Integer.parseInt(durm.group(1));
                            int max = Integer.parseInt(durm.group(2));
                            GameDataBridge.getInstance().setDurability(dur, max);
                        } catch (Exception ignored) {}
                    }
                    Matcher him = heldItemPattern.matcher(line);
                    if (him.find()) {
                        GameDataBridge.getInstance().setHeldItem(him.group(1));
                    }
                    Matcher sbm = scoreboardPattern.matcher(line);
                    if (sbm.find()) {
                        GameDataBridge.getInstance().setScoreboard(sbm.group(1));
                    }
                }
            } catch (Exception ignored) {}
        }, "raven-log-parser");
        t.setDaemon(true);
        t.start();
    }

    private boolean isKeyDown(int vk) {
        try {
            return (User32.INSTANCE.GetAsyncKeyState(vk) & 0x8000) != 0;
        } catch (Exception e) {
            System.out.println("[RAVEN] GetAsyncKeyState error: " + e.getMessage());
            return false;
        }
    }

    private void toggleGui() {
        System.out.println("[RAVEN] toggleGui called, editMode=" + editMode + " guiOpen=" + guiOpen);
        if (editMode) {
            closeEditor();
        } else if (guiOpen) {
            closeGui();
        } else {
            openGui();
        }
    }

    private void openGui() {
        guiOpen = true;
        setClickThrough(false);
        NestGui gui = new NestGui(config, elements, this::openEditorFromMenu, this::onModulesChanged, this::closeGui);
        gui.setOpacity(0);
        root.getChildren().add(gui);
        FadeTransition ft = new FadeTransition(Duration.millis(180), gui);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    /** The bird nest's "Layout" nav: close the menu, then enter layout edit mode. */
    private void openEditorFromMenu() {
        closeGui();
        openEditor();
    }

    private void closeGui() {
        guiOpen = false;
        javafx.scene.Node gui = findTopOfType(NestGui.class);
        if (gui != null) fadeOutAndRemove(gui);
        setClickThrough(true);
        persistConfig();
    }

    private void openEditor() {
        editMode = true;
        renderer.setEditMode(true);
        setClickThrough(false);
        HudEditor editor = new HudEditor(elements, config, hudCanvas, this::saveConfig, this::closeEditor);
        editor.prefWidthProperty().bind(root.widthProperty());
        editor.prefHeightProperty().bind(root.heightProperty());
        editor.setOpacity(0);
        root.getChildren().add(editor);
        FadeTransition ft = new FadeTransition(Duration.millis(200), editor);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void closeEditor() {
        editMode = false;
        renderer.setEditMode(false);
        elements.forEach(el -> { el.selected = false; el.hovered = false; });
        javafx.scene.Node editor = findTopOfType(HudEditor.class);
        if (editor != null) fadeOutAndRemove(editor);
        setClickThrough(true);
        syncModulesFromElements();
        persistConfig();
    }

    private javafx.scene.Node findTopOfType(Class<?> type) {
        for (int i = root.getChildren().size() - 1; i >= 0; i--) {
            javafx.scene.Node n = root.getChildren().get(i);
            if (type.isInstance(n)) return n;
        }
        return null;
    }

    private void fadeOutAndRemove(javafx.scene.Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), node);
        ft.setFromValue(1);
        ft.setToValue(0);
        ft.setOnFinished(e -> root.getChildren().remove(node));
        ft.play();
    }

    private void setClickThrough(boolean passThrough) {
        try {
            com.sun.jna.platform.win32.WinDef.HWND hwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, "RavenOverlay");
            if (hwnd == null) return;
            int GWL_EXSTYLE = -20;
            int WS_EX_LAYERED = 0x80000;
            int WS_EX_TRANSPARENT = 0x20;
            int WS_EX_TOOLWINDOW = 0x80;
            int exStyle = com.sun.jna.platform.win32.User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE);
            if (passThrough) {
                exStyle |= WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW;
            } else {
                exStyle &= ~WS_EX_TRANSPARENT;
            }
            com.sun.jna.platform.win32.User32.INSTANCE.SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
            System.out.println("[RAVEN] setClickThrough=" + passThrough + " exStyle=" + Integer.toHexString(exStyle));
        } catch (Exception e) {
            System.out.println("[RAVEN] setClickThrough error: " + e.getMessage());
        }
    }

    private void saveConfig() {
        try { config.save(configDirectory); } catch (Exception ignored) {}
    }

    /** Saves the current state into the active profile and writes it to disk. */
    private void persistConfig() {
        config.saveCurrentProfile();
        saveConfig();
    }

    /** Called whenever modules are toggled or a profile is applied. */
    private void onModulesChanged() {
        elements.forEach(el -> el.bind(config));
        applyModuleVisibility();
        persistConfig();
    }

    /** Mirrors module.enabled onto linked HUD element visibility. */
    private void applyModuleVisibility() {
        for (ModuleRegistry.ClientModule m : ModuleRegistry.MODULES) {
            if (m.requiresMod()) continue;
            for (HudElement el : elements) {
                if (!el.id.equals(m.id())) continue;
                boolean firstRun = !config.modules.containsKey(m.id());
                HudConfig.ModuleConfig mc = config.module(m.id());
                if (firstRun) mc.enabled = el.cfg.visible; // backward compatible
                el.cfg.visible = mc.enabled;
            }
        }
    }

    /** Flows element visibility (e.g. from PropertiesPanel) back into modules. */
    private void syncModulesFromElements() {
        for (HudElement el : elements) {
            for (ModuleRegistry.ClientModule m : ModuleRegistry.MODULES) {
                if (m.requiresMod()) continue;
                if (m.id().equals(el.id)) config.module(m.id()).enabled = el.cfg.visible;
            }
        }
    }

    public void shutdown() {
        if (keybindPoller != null) keybindPoller.shutdownNow();
        if (renderer != null) renderer.stop();
        Platform.runLater(() -> {
            if (overlayStage != null) overlayStage.close();
        });
    }
}
