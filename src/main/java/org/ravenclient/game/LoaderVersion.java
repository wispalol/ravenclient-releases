package org.ravenclient.game;

/**
 * A single loader build inside a {@link LoaderManifest}: its version id, the URL of its
 * (partial) version profile, and whether it is stable. Mirrors interfrost's
 * {@code LoaderVersion} used by OneLauncher.
 */
public record LoaderVersion(String id, String url, boolean stable) {
}
