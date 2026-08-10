package org.ravenclient.overlay;

import java.util.List;

/**
 * The built-in, fully legitimate RavenClient mod catalog shown in the
 * "bird nest" browse menu. Every mod is informational, visual or cosmetic.
 *
 * <p>{@code requiresMod=false} mods are real, working HUD elements rendered by the
 * overlay. {@code requiresMod=true} mods change Minecraft's actual rendering or
 * input and therefore need an in-game mod (Mixin injection), which is a separate
 * codebase — they are listed so the user sees the roadmap, but stay locked.
 */
public final class ModuleRegistry {

    public record ClientModule(String id, String name, String desc, String category,
                               boolean requiresMod, String defaultKey) {}

    public static final List<String> CATEGORIES = List.of("All", "HUD", "PvP", "Visual");

    public static final List<ClientModule> MODULES = List.of(
            // ---- HUD ----
            new ClientModule("fps", "FPS Counter", "Shows your current frames per second", "HUD", false, "F8"),
            new ClientModule("cps", "CPS Counter", "Shows your clicks per second", "HUD", false, ""),
            new ClientModule("ping", "Ping Display", "Shows your latency to the server", "HUD", false, ""),
            new ClientModule("coords", "Coordinates", "Shows your X, Y and Z position", "HUD", false, ""),
            new ClientModule("direction", "Direction", "Shows the direction you are facing", "HUD", false, ""),
            new ClientModule("speed", "Speedometer", "Shows your current speed", "HUD", false, ""),
            new ClientModule("clock", "Clock", "Shows the current time", "HUD", false, ""),
            new ClientModule("serverinfo", "Server Info", "Shows the server you are connected to", "HUD", false, ""),
            new ClientModule("helditem", "Held Item", "Shows the item you are holding", "HUD", false, ""),
            new ClientModule("watermark", "Watermark", "Shows the RavenClient watermark", "HUD", false, ""),
            new ClientModule("customtext", "Custom Text", "Shows a custom message on screen", "HUD", false, ""),
            new ClientModule("modulestatus", "Module Status", "Lists which mods are currently active", "HUD", false, ""),

            // ---- PvP (informational / visual only, no automated combat) ----
            new ClientModule("targethud", "Target HUD", "Shows your target's name and health", "PvP", false, ""),
            new ClientModule("combo", "Combo Counter", "Tracks how many hits you land in a row", "PvP", false, ""),
            new ClientModule("hitcounter", "Hit Counter", "Counts the hits you land", "PvP", false, ""),
            new ClientModule("armorstatus", "Armor Status", "Shows your armor durability percentage", "PvP", false, ""),
            new ClientModule("durability", "Item Durability", "Shows your held item's durability", "PvP", false, ""),
            new ClientModule("potions", "Potion Effects", "Shows your active potion effects", "PvP", false, ""),
            new ClientModule("keystrokes", "Keystrokes", "Shows your WASD and mouse input", "PvP", false, ""),
            new ClientModule("sessionstats", "Session Stats", "Shows kills, deaths and K/D for this session", "PvP", false, ""),
            new ClientModule("scoreboard", "Scoreboard", "Shows the server scoreboard", "PvP", false, ""),
            new ClientModule("bossbar", "Bossbar", "Shows the bossbar at the top of the screen", "PvP", true, ""),
            new ClientModule("reach", "Reach Display", "Shows the distance to your target", "PvP", true, ""),
            new ClientModule("attackcooldown", "Attack Cooldown", "Shows when your next hit is ready", "PvP", true, ""),
            new ClientModule("hitcolor", "Hit Color", "Flashes a color when you land a hit", "PvP", true, ""),
            new ClientModule("hitboxes", "Hitboxes", "Draws expanded hitboxes around entities", "PvP", true, ""),
            new ClientModule("damageindicator", "Damage Indicator", "Shows damage numbers over targets", "PvP", true, ""),
            new ClientModule("damagetint", "Damage Tint", "Tints your screen when damaged", "PvP", true, ""),

            // ---- Visual (all require an in-game mod) ----
            new ClientModule("freelook", "Freelook", "Look around freely without turning your body", "Visual", true, "F6"),
            new ClientModule("zoom", "Zoom", "Zoom your view with a keybind", "Visual", true, ""),
            new ClientModule("fullbright", "Fullbright", "See clearly in the dark", "Visual", true, ""),
            new ClientModule("motionblur", "Motion Blur", "Adds a smooth motion blur effect", "Visual", true, ""),
            new ClientModule("timechanger", "Time Changer", "Change the in-game time of day", "Visual", true, ""),
            new ClientModule("weatherchanger", "Weather Changer", "Change the in-game weather", "Visual", true, ""),
            new ClientModule("blockoverlay", "Block Overlay", "Highlights the block you are looking at", "Visual", true, ""),
            new ClientModule("crosshair", "Custom Crosshair", "Replace the default crosshair", "Visual", true, ""),
            new ClientModule("fov", "FOV Changer", "Change your field of view", "Visual", true, ""),
            new ClientModule("particles", "Particles", "Customize hit and block-break particles", "Visual", true, ""));

    public static boolean requiresInGameMod(String id) {
        for (ClientModule m : MODULES) {
            if (m.id().equals(id)) return m.requiresMod();
        }
        return true;
    }

    private ModuleRegistry() {}
}
