package org.ravenclient.meta;

import java.util.Map;

/** A downloaded asset-index file (objects hash -> Asset). */
public record GameAssetIndex(Boolean virtual, Map<String, Asset> objects) {
}
