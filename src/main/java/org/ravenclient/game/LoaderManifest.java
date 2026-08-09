package org.ravenclient.game;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The unified loader manifest served by the metadata API, mirroring interfrost's
 * {@code modded::Manifest}: {@code {"gameVersions":[{"id":...,"stable":...,"loaders":[...]}]}}.
 *
 * A game version id may contain the placeholder {@code ${interfrost.gameVersion}} (or the
 * legacy {@code ${interpulse.gameVersion}}), meaning that loader build applies to any
 * Minecraft version; callers substitute the concrete version before comparing.
 */
public record LoaderManifest(@JsonProperty("gameVersions") List<GameEntry> gameVersions) {

    public record GameEntry(String id, boolean stable, List<LoaderVersion> loaders) {
    }

    public static final String DUMMY_GAME_VERSION = "${interfrost.gameVersion}";
    public static final String LEGACY_DUMMY_GAME_VERSION = "${interpulse.gameVersion}";

    /** Substitutes the game-version placeholder if present, otherwise returns the id unchanged. */
    public static String substituteGameVersion(String id, String mcVersion) {
        if (id == null) return null;
        return id.replace(DUMMY_GAME_VERSION, mcVersion).replace(LEGACY_DUMMY_GAME_VERSION, mcVersion);
    }
}
