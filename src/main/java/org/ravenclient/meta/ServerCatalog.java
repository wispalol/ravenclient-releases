package org.ravenclient.meta;

import com.fasterxml.jackson.databind.JsonNode;
import org.ravenclient.util.Json;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The bundled Browse catalog (loaded from {@code /servers.json}). Entries are
 * curated offline and ranked by {@code playersAvg}; the Browse page refreshes
 * live player counts at runtime via {@link ServerStatus#fetch(String)}. Missing
 * or corrupt data simply yields an empty catalog so the UI never breaks.
 */
public final class ServerCatalog {

    public record Server(
            String name,
            String host,
            int port,
            List<String> categories,
            String version,
            int playersAvg
    ) {
        /** Host:port string, omitting the port when it is the default 25565. */
        public String address() {
            return port == 25565 ? host : host + ":" + port;
        }
    }

    private static final List<Server> SERVERS = load();

    private ServerCatalog() {
    }

    public static List<Server> all() {
        return SERVERS;
    }

    /** All categories that appear in the catalog, in first-seen order. */
    public static List<String> categories() {
        Set<String> cats = new LinkedHashSet<>();
        for (Server s : SERVERS) cats.addAll(s.categories());
        return new ArrayList<>(cats);
    }

    private static List<Server> load() {
        List<Server> out = new ArrayList<>();
        try (InputStream in = ServerCatalog.class.getResourceAsStream("/servers.json")) {
            if (in == null) return out;
            JsonNode root = Json.mapper().readTree(in);
            JsonNode arr = root.path("servers");
            if (!arr.isArray()) return out;
            for (JsonNode n : arr) {
                String name = n.path("name").asText("");
                String host = n.path("host").asText("");
                if (name.isBlank() || host.isBlank()) continue;
                int port = n.path("port").asInt(25565);
                List<String> cats = new ArrayList<>();
                for (JsonNode c : n.path("categories")) {
                    String v = c.asText();
                    if (v != null && !v.isBlank()) cats.add(v);
                }
                if (cats.isEmpty()) cats.add("Other");
                out.add(new Server(name, host, port, cats,
                        n.path("version").asText(""),
                        n.path("playersAvg").asInt(0)));
            }
        } catch (Exception e) {
            // No catalog available; the Browse page renders empty.
        }
        return out;
    }
}
