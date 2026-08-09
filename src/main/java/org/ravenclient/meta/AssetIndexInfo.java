package org.ravenclient.meta;

public record AssetIndexInfo(String id, String sha1, Long size, Long totalSize, String url, Boolean virtual) {
}
