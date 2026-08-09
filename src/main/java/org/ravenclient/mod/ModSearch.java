package org.ravenclient.mod;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A search-result page from the Modrinth v2 search endpoint. OneLauncher's provider
 * maps the raw hit onto its own summary model; this record carries the same fields the
 * browser needs to render rows and the installer needs to resolve versions.
 */
public record ModSearch(List<Hit> hits, int offset, int limit,
                        @JsonProperty("total_hits") int total) {

    public record Hit(
            @JsonProperty("project_id") String project_id,
            String slug,
            String title,
            String description,
            @JsonProperty("project_type") String project_type,
            String author,
            int downloads,
            int follows,
            List<String> categories,
            @JsonProperty("icon_url") String icon_url,
            @JsonAlias("versions") List<String> game_versions
    ) {
    }
}
