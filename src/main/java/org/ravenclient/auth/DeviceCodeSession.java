package org.ravenclient.auth;

public record DeviceCodeSession(
        String deviceCode,
        String userCode,
        String verificationUri,
        String message,
        int expiresIn,
        int interval) {
}
