package org.ravenclient.title;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny JSON config in the game's config directory (config/ravenclient.json).
 * {@code showCustomTitle} defaults to true; set it to false to use the vanilla
 * title screen. Holding SHIFT while the title screen loads also skips it.
 */
public final class RavenConfig {

    public static boolean showCustomTitle = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RavenConfig() {
    }

    public static void load() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("ravenclient.json");
            if (!Files.exists(file)) {
                return;
            }
            Holder holder = GSON.fromJson(Files.readString(file), Holder.class);
            if (holder != null) {
                showCustomTitle = holder.showCustomTitle;
            }
        } catch (Exception ignored) {
            // unreadable config: fall back to defaults
        }
    }

    public static void save() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("ravenclient.json");
            Files.createDirectories(file.getParent());
            Holder holder = new Holder();
            holder.showCustomTitle = showCustomTitle;
            Files.writeString(file, GSON.toJson(holder));
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static final class Holder {
        boolean showCustomTitle = true;
    }
}
