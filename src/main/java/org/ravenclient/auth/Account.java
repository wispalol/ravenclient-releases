package org.ravenclient.auth;

public record Account(
        String msRefreshToken,
        String msAccessToken,
        String minecraftToken,
        String xuid,
        String uuid,
        String username) {

    public boolean isUsable() {
        return minecraftToken != null && uuid != null && username != null;
    }
}
