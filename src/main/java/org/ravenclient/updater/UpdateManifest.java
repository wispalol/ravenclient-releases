package org.ravenclient.updater;

/**
 * Update manifest served at {@link AppUpdater#manifestUrl()}. The zip contains
 * the files to replace in the install dir (at minimum app.jar, typically also
 * libs/*). sha256 is the zip's checksum, used to verify the download.
 */
public record UpdateManifest(String version, String notes, String url, String sha256) {
}
