package org.ravenclient.skin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ravenclient.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Client for the Minecraft / Mojang skin APIs.
 *
 * <ul>
 *   <li>Own profile (active skin) via {@code api.minecraftservices.com/minecraft/profile}.</li>
 *   <li>Set the account skin from a URL (JSON) or a local file (multipart upload).</li>
 *   <li>Reset back to the default Steve / Alex skin.</li>
 *   <li>Resolve another player by in-game name and copy their skin.</li>
 * </ul>
 *
 * <p>All write operations require the account's Minecraft bearer token; lookups are public.</p>
 */
public final class SkinService {

    private static final String BASE = "https://api.minecraftservices.com";
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String MOJANG_NAME_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String USER_AGENT = "RavenClient/1.0.0";

    private final HttpClient client;

    public SkinService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** A skin on a player's own profile. {@code variant} is {@code CLASSIC} or {@code SLIM}. */
    public record SkinInfo(String id, String state, String url, String variant, String alias) {
    }

    /** The authenticated player's profile plus the skin list from the Minecraft services API. */
    public record OwnProfile(String uuid, String name, List<SkinInfo> skins) {

        public SkinInfo activeSkin() {
            if (skins == null) return null;
            for (SkinInfo s : skins) {
                if ("ACTIVE".equalsIgnoreCase(s.state())) return s;
            }
            return skins.isEmpty() ? null : skins.get(0);
        }
    }

    /** Result of a public name/UUID lookup. {@code uuid} is returned without dashes. */
    public record LookupProfile(String uuid, String name) {
    }

    /** A player's skin texture, decoded from the sessionserver profile. */
    public record SkinTexture(String url, String model) {
    }

    /** Fetches the signed-in player's profile (uuid, name and active skin). */
    public OwnProfile ownProfile(String token) throws IOException {
        JsonNode body = get(BASE + "/minecraft/profile", token);
        List<SkinInfo> skins = new ArrayList<>();
        JsonNode arr = body.path("skins");
        if (arr.isArray()) {
            for (JsonNode s : arr) {
                skins.add(new SkinInfo(
                        s.path("id").asText(""),
                        s.path("state").asText(""),
                        s.path("url").asText(""),
                        s.path("variant").asText(""),
                        s.path("alias").asText("")));
            }
        }
        return new OwnProfile(body.path("id").asText(""), body.path("name").asText(""), skins);
    }

    /**
     * Resolves an in-game name to a profile. Uses the public Minecraft services identity
     * endpoint first, falling back to the legacy Mojang API.
     */
    public LookupProfile lookupName(String name) throws IOException {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) throw new IOException("Enter a player name to search.");
        if (clean.matches("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}")) {
            return new LookupProfile(clean.replace("-", ""), clean);
        }
        IOException last = null;
        try {
            JsonNode body = get(BASE + "/minecraft/profile/lookup/name/" + enc(clean), null);
            String id = body.path("id").asText("");
            String nameOut = body.path("name").asText("");
            if (!id.isEmpty() && !nameOut.isEmpty()) return new LookupProfile(id, nameOut);
        } catch (IOException e) {
            last = e;
        }
        try {
            JsonNode body = get(MOJANG_NAME_URL + enc(clean), null);
            String id = body.path("id").asText("");
            String nameOut = body.path("name").asText("");
            if (!id.isEmpty() && !nameOut.isEmpty()) return new LookupProfile(id, nameOut);
        } catch (IOException e) {
            last = e;
        }
        if (last != null) throw new IOException("Could not find player \"" + clean + "\" (" + last.getMessage() + ")", last);
        throw new IOException("Could not find player \"" + clean + "\".");
    }

    /**
     * Fetches another player's skin texture. {@code uuid} may be dashed or not.
     */
    public SkinTexture skinFor(String uuid) throws IOException {
        String clean = uuid == null ? "" : uuid.replace("-", "");
        JsonNode body = get(SESSION_URL + clean, null);
        JsonNode props = body.path("properties");
        if (props.isArray()) {
            for (JsonNode prop : props) {
                if (!"textures".equals(prop.path("name").asText())) continue;
                String value = prop.path("value").asText("");
                if (value.isEmpty()) break;
                JsonNode textures;
                try {
                    textures = Json.mapper().readTree(Base64.getDecoder().decode(value));
                } catch (Exception e) {
                    throw new IOException("Malformed skin data for " + clean, e);
                }
                JsonNode skin = textures.path("textures").path("SKIN");
                String url = skin.path("url").asText("");
                if (!url.isEmpty()) {
                    // The session server hands out http:// textures.minecraft.net links;
                    // the services API requires https, which that host always serves.
                    if (url.startsWith("http://textures.minecraft.net/")) {
                        url = "https://" + url.substring("http://".length());
                    }
                    String model = skin.path("metadata").path("model").asText("");
                    return new SkinTexture(url, model.isBlank() ? "classic" : model);
                }
            }
        }
        throw new IOException("This player does not have a visible Java Edition skin.");
    }

    /** Applies a skin from a public image URL. {@code variant} is "classic" or "slim". */
    public void setSkinUrl(String token, String url, String variant) throws IOException {
        ObjectNode body = Json.mapper().createObjectNode();
        body.put("url", url);
        body.put("variant", normalizeVariant(variant));
        post(BASE + "/minecraft/profile/skins", token, body.toString(), "application/json");
    }

    /** Uploads a local skin PNG. {@code variant} is "classic" or "slim". */
    public void setSkinFile(String token, Path file, String variant) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException("The selected file does not exist.");
        }
        String boundary = "----ravenclient" + Long.toHexString(System.currentTimeMillis());
        byte[] payload = multipart(boundary, Map.of("variant", normalizeVariant(variant)), file);
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/minecraft/profile/skins"))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .timeout(Duration.ofMinutes(2))
                .build();
        send(req);
    }

    /** Resets the account's skin to the default Steve / Alex skin. */
    public void resetSkin(String token) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/minecraft/profile/skins/active"))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", USER_AGENT)
                .DELETE()
                .timeout(Duration.ofSeconds(60))
                .build();
        send(req);
    }

    /**
     * Copies another player's skin onto the signed-in account: name → profile → texture,
     * then applies it via the services API.
     */
    public void copySkin(String token, String name) throws IOException {
        LookupProfile lookup = lookupName(name);
        SkinTexture texture = skinFor(lookup.uuid());
        setSkinUrl(token, texture.url(), texture.model());
    }

    private byte[] multipart(String boundary, Map<String, String> fields, Path file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n");
            write(out, e.getValue() + "\r\n");
        }
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFilename(file) + "\"\r\n");
        write(out, "Content-Type: image/png\r\n\r\n");
        out.write(Files.readAllBytes(file));
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write(b, 0, b.length);
    }

    private static String safeFilename(Path file) {
        String name = file.getFileName() == null ? "skin.png" : file.getFileName().toString();
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private JsonNode get(String url, String token) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(60));
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        return parse(send(b.build()));
    }

    private void post(String url, String token, String json, String contentType) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(60))
                .build();
        send(req);
    }

    private String send(HttpRequest req) throws IOException {
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body() == null ? "" : resp.body();
            }
            throw describe(req, resp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted.", e);
        }
    }

    private IOException describe(HttpRequest req, HttpResponse<String> resp) {
        String detail = "";
        String body = resp.body();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = Json.mapper().readTree(body);
                if (node != null) {
                    String em = node.path("errorMessage").asText("");
                    String err = node.path("error").asText("");
                    if (!em.isEmpty()) detail = em;
                    else if (!err.isEmpty()) detail = err;
                }
            } catch (Exception ignored) {
                detail = body.length() > 200 ? body.substring(0, 200) : body;
            }
        }
        String base = req.uri().toString().replace(BASE, "").replace(SESSION_URL, "").replace(MOJANG_NAME_URL, "");
        String msg = "HTTP " + resp.statusCode() + " from Minecraft skin API (" + base + ")";
        if (!detail.isEmpty()) msg += ": " + detail;
        if (resp.statusCode() == 401) msg += ". Your session may have expired - try signing in again.";
        return new IOException(msg);
    }

    private static JsonNode parse(String body) throws IOException {
        if (body == null || body.isBlank()) return Json.mapper().createObjectNode();
        return Json.mapper().readTree(body);
    }

    private static String normalizeVariant(String variant) {
        if (variant == null) return "classic";
        String v = variant.trim().toLowerCase(java.util.Locale.ROOT);
        return v.equals("slim") || v.startsWith("slim") ? "slim" : "classic";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
