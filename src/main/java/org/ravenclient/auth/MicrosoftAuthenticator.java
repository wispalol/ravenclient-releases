package org.ravenclient.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Microsoft account authentication for Minecraft: Java Edition.
 *
 * <p>Implements the full chain used by the official launcher:</p>
 * <ol>
 *   <li>OAuth 2.0 device-code flow against login.microsoftonline.com (consumers tenant).</li>
 *   <li>Exchange the Microsoft access token for an Xbox Live token (user.auth.xboxlive.com).</li>
 *   <li>Exchange the Xbox Live token for an XSTS token (xsts.auth.xboxlive.com).</li>
 *   <li>Exchange the XSTS token for a Minecraft token (api.minecraftservices.com).</li>
 *   <li>Fetch the player profile (uuid + username) and verify ownership.</li>
 * </ol>
 */
public final class MicrosoftAuthenticator {

    /**
     * A publicly-registered Azure app used by the open-source Minecraft launcher
     * community for the OAuth 2.0 device-code flow. Using this id lets users sign
     * in without anyone hosting a backend and without registering their own app.
     * If it ever stops working, create your own Azure app in
     * https://portal.azure.com (Personal Microsoft accounts only) and pass your
     * client id to {@link #MicrosoftAuthenticator(String)}.
     */
    public static final String DEFAULT_CLIENT_ID = "2305bcc4-e212-4bf4-8476-a135286ea9f6";

    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final HttpClient client;
    private final String clientId;

    public MicrosoftAuthenticator() {
        this(DEFAULT_CLIENT_ID);
    }

    public MicrosoftAuthenticator(String clientId) {
        this.clientId = clientId;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Starts the device-code flow. Show {@link DeviceCodeSession#userCode()} to the user. */
    public DeviceCodeSession startDeviceFlow() throws AuthException {
        HttpResponse<String> resp = postForm(DEVICE_CODE_URL, Map.of(
                "client_id", clientId,
                "scope", "XboxLive.signin offline_access"));
        JsonNode body = parse(resp.body());
        if (body.hasNonNull("error")) {
            throw new AuthException("Microsoft rejected the request: "
                    + body.path("error").asText() + " - " + body.path("error_description").asText());
        }
        return new DeviceCodeSession(
                body.path("device_code").asText(),
                body.path("user_code").asText(),
                body.path("verification_uri").asText(),
                body.path("message").asText(),
                body.path("expires_in").asInt(900),
                body.path("interval").asInt(5));
    }

    /**
     * Polls the token endpoint until the user has approved the login, then completes
     * the Xbox/Minecraft chain. Blocks until success, failure or timeout.
     */
    public Account waitForDeviceCode(DeviceCodeSession session) throws AuthException {
        long deadline = System.currentTimeMillis() + session.expiresIn() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(Math.max(1, session.interval()) * 1000L);
            } catch (InterruptedException e) {
                throw new AuthException("Login cancelled");
            }
            HttpResponse<String> resp = postForm(TOKEN_URL, Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", session.deviceCode()));
            JsonNode body = parse(resp.body());
            if (body.hasNonNull("access_token")) {
                return completeChain(body.get("access_token").asText(),
                        body.path("refresh_token").asText());
            }
            String error = body.path("error").asText("");
            switch (error) {
                case "authorization_pending", "bad_verification_code" -> { /* keep polling */ }
                case "authorization_declined" -> throw new AuthException("You declined the login request.");
                case "expired_token" -> throw new AuthException("The login request expired. Please try again.");
                default -> throw new AuthException("Unexpected login error: " + error);
            }
        }
        throw new AuthException("Login timed out. Please try again.");
    }

    /** Refreshes an existing account using its stored Microsoft refresh token. */
    public Account refresh(Account account) throws AuthException {
        if (account.msRefreshToken() == null || account.msRefreshToken().isEmpty()) {
            throw new AuthException("No saved session - please sign in again.");
        }
        HttpResponse<String> resp = postForm(TOKEN_URL, Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", account.msRefreshToken(),
                "scope", "XboxLive.signin offline_access"));
        JsonNode body = parse(resp.body());
        if (body.hasNonNull("error")) {
            throw new AuthException("Your session has expired. Please sign in again.");
        }
        return completeChain(body.get("access_token").asText(), body.path("refresh_token").asText());
    }

    /** Runs the Xbox Live -> XSTS -> Minecraft -> profile chain. */
    public Account completeChain(String msAccessToken, String msRefreshToken) throws AuthException {
        JsonNode xbl = postJson(XBL_URL, node -> {
            ObjectNode props = node.putObject("Properties");
            props.put("AuthMethod", "RPS");
            props.put("SiteName", "user.auth.xboxlive.com");
            props.put("RpsTicket", "d=" + msAccessToken);
            node.put("RelyingParty", "http://auth.xboxlive.com");
            node.put("TokenType", "JWT");
        });
        String xblToken = xbl.path("Token").asText("");
        String uhs = xbl.path("DisplayClaims").path("xui").path(0).path("uhs").asText("");
        if (xblToken.isEmpty() || uhs.isEmpty()) {
            throw new AuthException("Xbox Live authentication failed.");
        }

        JsonNode xsts = postJson(XSTS_URL, node -> {
            ObjectNode props = node.putObject("Properties");
            props.put("SandboxId", "RETAIL");
            props.putArray("UserTokens").add(xblToken);
            node.put("RelyingParty", "rp://api.minecraftservices.com/");
            node.put("TokenType", "JWT");
        });
        String xstsToken = xsts.path("Token").asText("");
        String xuid = xsts.path("DisplayClaims").path("xui").path(0).path("xid").asText("");
        if (xstsToken.isEmpty()) {
            throw new AuthException("XSTS authentication failed.");
        }

        JsonNode mc = postJson(MC_LOGIN_URL,
                node -> node.put("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken));
        String mcToken = mc.path("access_token").asText("");
        if (mcToken.isEmpty()) {
            throw new AuthException("Minecraft authentication failed.");
        }

        JsonNode profile = getJson(MC_PROFILE_URL, mcToken);
        String uuid = profile.path("id").asText("");
        String username = profile.path("name").asText("");
        if (uuid.isEmpty() || username.isEmpty()) {
            throw new AuthException("This Microsoft account has no Minecraft: Java Edition profile. "
                    + "Open the official launcher and sign in once first.");
        }

        return new Account(msRefreshToken, msAccessToken, mcToken, xuid, uuid, username);
    }

    private JsonNode postJson(String url, Consumer<ObjectNode> bodyBuilder) throws AuthException {
        ObjectNode body = Json.mapper().createObjectNode();
        bodyBuilder.accept(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-xbl-contract-version", "1")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(60))
                .build();
        try {
            return handleResponse(client.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException e) {
            throw new AuthException("Network error while contacting " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("Login interrupted");
        }
    }

    private JsonNode getJson(String url, String bearer) throws AuthException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build();
        try {
            return handleResponse(client.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException e) {
            throw new AuthException("Network error while contacting " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("Login interrupted");
        }
    }

    private HttpResponse<String> postForm(String url, Map<String, String> form) throws AuthException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
                .timeout(Duration.ofSeconds(60))
                .build();
        try {
            return client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AuthException("Network error while contacting " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("Login interrupted");
        }
    }

    private JsonNode handleResponse(HttpResponse<String> resp) throws AuthException {
        if (resp.body() == null || resp.body().isBlank()) {
            if (resp.statusCode() >= 400) {
                throw new AuthException("Microsoft/Xbox rejected the request (HTTP " + resp.statusCode()
                        + "). If this follows a login, try again or sign in once in the official launcher.");
            }
            return Json.mapper().createObjectNode();
        }
        JsonNode body = parse(resp.body());
        if (resp.statusCode() >= 400) {
            long xerr = body.path("XErr").asLong(-1);
            if (xerr != -1) {
                throw new AuthException(describeXErr(xerr));
            }
            String error = body.path("error").asText("");
            String desc = body.path("error_description").asText("");
            if (!error.isEmpty()) {
                throw new AuthException(error + (desc.isEmpty() ? "" : ": " + desc));
            }
            throw new AuthException("Request failed with status " + resp.statusCode() + ": " + resp.body());
        }
        return body;
    }

    private static String encodeForm(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static JsonNode parse(String body) {
        try {
            return Json.mapper().readTree(body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String describeXErr(long xerr) {
        if (xerr == 2148916227L) return "This Microsoft account does not own Minecraft: Java Edition.";
        if (xerr == 2148916228L) return "This account is under 18 and must be added to a family on Xbox first.";
        if (xerr == 2148916233L) return "This account is banned from Xbox Live.";
        if (xerr == 2148916235L) return "This account is from a region where Xbox Live is not available.";
        if (xerr == 2148916236L || xerr == 2148916237L) return "This account requires adult verification on the Xbox website.";
        if (xerr == 2148916238L) return "This is a child account and cannot use a third-party launcher. Use the official Minecraft Launcher instead.";
        return "Xbox Live error " + xerr;
    }
}
