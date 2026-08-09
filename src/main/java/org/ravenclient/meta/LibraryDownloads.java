package org.ravenclient.meta;

import java.util.Map;

public record LibraryDownloads(Artifact artifact, Map<String, Artifact> classifiers) {
}
