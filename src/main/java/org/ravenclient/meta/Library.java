package org.ravenclient.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * A classpath library entry. Loader manifests (Fabric/Quilt/Forge/NeoForge) may carry
 * {@code include_in_classpath} and {@code downloadable} flags; a missing flag means true,
 * which is what vanilla Mojang manifests assume.
 */
public record Library(
        String name,
        String url,
        LibraryDownloads downloads,
        JsonNode rules,
        Map<String, String> natives,
        @JsonProperty("include_in_classpath") Boolean includeInClasspathFlag,
        @JsonProperty("downloadable") Boolean downloadableFlag) {

    public boolean includeInClasspath() {
        return includeInClasspathFlag == null || includeInClasspathFlag;
    }

    public boolean downloadable() {
        return downloadableFlag == null || downloadableFlag;
    }
}
