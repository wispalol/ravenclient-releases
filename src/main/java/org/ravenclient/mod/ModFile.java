package org.ravenclient.mod;

/**
 * A single downloadable file of a Modrinth project version. Modrinth nests the hashes
 * under {@code hashes} (sha1 + sha512); sha1 is the identity used for verification and
 * update lookups, matching OneLauncher's file-identity handling.
 */
public record ModFile(String url, String filename, boolean primary, long size, ModHashes hashes) {

    public record ModHashes(String sha1, String sha512) {
    }

    public String sha1() {
        return hashes != null ? hashes.sha1() : null;
    }

    public String sha512() {
        return hashes != null ? hashes.sha512() : null;
    }
}
