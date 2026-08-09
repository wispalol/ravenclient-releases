package org.ravenclient.game;

import java.util.List;

/**
 * The mod loaders a profile can be built on. Mirrors OneLauncher's {@code GameLoader}:
 * each loader knows its metadata-service format name, the on-disk profile prefix, and
 * its Modrinth category name so search facets and version filters line up.
 */
public enum Loader {

    VANILLA("minecraft", null, "Vanilla"),
    FABRIC("fabric", "fabric-", "Fabric"),
    QUILT("quilt", "quilt-", "Quilt"),
    FORGE("forge", "forge-", "Forge"),
    NEOFORGE("neo", "neoforge-", "NeoForge");

    private final String formatName;
    private final String prefix;
    private final String displayName;

    Loader(String formatName, String prefix, String displayName) {
        this.formatName = formatName;
        this.prefix = prefix;
        this.displayName = displayName;
    }

    public boolean isModded() {
        return this != VANILLA;
    }

    /** Metadata service path segment, e.g. {@code fabric} or {@code neo} (see LoaderMeta#METADATA_API). */
    public String formatName() {
        return formatName;
    }

    /** Directory prefix of installed profiles, e.g. {@code fabric-} -> {@code fabric-1.21.11}. */
    public String prefix() {
        return prefix;
    }

    public String displayName() {
        return displayName;
    }

    /** Modrinth loader/category name, e.g. {@code neoforge}, {@code fabric}. */
    public String modrinthName() {
        return switch (this) {
            case VANILLA -> "minecraft";
            case NEOFORGE -> "neoforge";
            default -> formatName;
        };
    }

    public static Loader[] moddedLoaders() {
        return new Loader[]{FABRIC, QUILT, FORGE, NEOFORGE};
    }

    public static List<String> displayNames() {
        return java.util.Arrays.stream(values()).map(Loader::displayName).toList();
    }

    public static Loader fromDisplayName(String name) {
        if (name == null) return VANILLA;
        for (Loader l : values()) {
            if (l.displayName.equalsIgnoreCase(name.trim())) return l;
        }
        return VANILLA;
    }

    /** Finds a loader by its profile prefix, e.g. {@code neoforge-} -> NEOFORGE. */
    public static Loader fromPrefix(String prefix) {
        if (prefix == null) return null;
        for (Loader l : values()) {
            if (l.prefix != null && prefix.equals(l.prefix)) return l;
        }
        return null;
    }
}
