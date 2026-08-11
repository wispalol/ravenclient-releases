package org.ravenclient.meta;

import com.fasterxml.jackson.databind.JsonNode;
import org.ravenclient.util.Http;
import org.ravenclient.util.Json;

import java.util.List;

/**
 * Live server status for the Featured Servers widget on the home screen.
 * Status is fetched from the free mcsrvstat.us API and never throws - any
 * failure resolves to an "offline/unknown" tile so the launcher UI stays snappy.
 */
public final class ServerStatus {

    private static final String STATUS_API = "https://api.mcsrvstat.us/3/%s";

    /** Curated list of famous Minecraft servers shown on the home screen. */
    public static final List<ServerStatus> FEATURED = List.of(
            new ServerStatus("Hypixel", "mc.hypixel.net"),
            new ServerStatus("DonutSMP", "donutsmp.net"),
            new ServerStatus("MCC Island", "mccisland.net"),
            new ServerStatus("Wynncraft", "play.wynncraft.com"),
            new ServerStatus("TubNet", "tubnet.net"),
            new ServerStatus("Minehut", "play.minehut.com"),
            new ServerStatus("Purple Prison", "play.purpleprison.net"),
            new ServerStatus("Cubic", "play.cubicmc.net"));

    private final String name;
    private final String host;
    private final boolean online;
    private final int onlinePlayers;
    private final int maxPlayers;
    private final String version;

    public ServerStatus(String name, String host) {
        this(name, host, false, 0, 0, "");
    }

    public ServerStatus(String name, String host, boolean online, int onlinePlayers, int maxPlayers, String version) {
        this.name = name;
        this.host = host;
        this.online = online;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.version = version;
    }

    public String name() {
        return name;
    }

    public String host() {
        return host;
    }

    public boolean online() {
        return online;
    }

    public int onlinePlayers() {
        return onlinePlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public String version() {
        return version;
    }

    /** Fetches live status for a server host. Returns an offline record on any failure. */
    public static ServerStatus fetch(String host) {
        try {
            String json = Http.getString(String.format(STATUS_API, host));
            JsonNode node = Json.mapper().readTree(json);
            boolean online = node != null && node.path("online").asBoolean(false);
            if (!online) return new ServerStatus(host, host, false, 0, 0, "");
            int players = node.path("players").path("online").asInt(0);
            int max = node.path("players").path("max").asInt(0);
            String version = node.path("version").asText("");
            return new ServerStatus(host, host, true, players, max, version);
        } catch (Exception e) {
            return new ServerStatus(host, host, false, 0, 0, "");
        }
    }
}
