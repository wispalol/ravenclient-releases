package org.ravenclient.meta;

import java.util.List;

public record VersionManifest(Latest latest, List<Version> versions) {

    public record Latest(String release, String snapshot) {
    }

    public record Version(String id, String type, String url, String sha1) {
    }
}
