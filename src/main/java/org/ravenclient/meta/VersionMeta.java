package org.ravenclient.meta;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record VersionMeta(
        String id,
        String type,
        String mainClass,
        String minecraftArguments,
        JsonNode arguments,
        AssetIndexInfo assetIndex,
        Downloads downloads,
        List<Library> libraries,
        JsonNode javaVersion) {
}
