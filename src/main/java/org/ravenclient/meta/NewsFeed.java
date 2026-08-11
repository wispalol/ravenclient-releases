package org.ravenclient.meta;

import com.fasterxml.jackson.databind.JsonNode;
import org.ravenclient.util.Http;
import org.ravenclient.util.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the latest news cards shown on the home screen. The list lives as a
 * small news.json in the public wispalol/ravenclient-releases repo (same infra
 * as update.json). Any failure falls back to the built-in cards so the launcher
 * never shows a broken section.
 */
public final class NewsFeed {

    private static final String NEWS_URL =
            "https://raw.githubusercontent.com/wispalol/ravenclient-releases/main/news.json";

    public record NewsItem(String title, String body, String tag, long timestamp) {
    }

    private NewsFeed() {
    }

    public static List<NewsItem> fetch() {
        try {
            String json = Http.getString(NEWS_URL);
            JsonNode node = Json.mapper().readTree(json);
            List<NewsItem> items = new ArrayList<>();
            if (node != null && node.isArray()) {
                for (JsonNode n : node) {
                    items.add(new NewsItem(
                            n.path("title").asText(),
                            n.path("body").asText(),
                            n.path("tag").asText(""),
                            n.path("timestamp").asLong(0)));
                }
            }
            if (!items.isEmpty()) return items;
        } catch (Exception ignored) {
            // fall through to the default cards
        }
        return defaultNews();
    }

    public static List<NewsItem> defaultNews() {
        return List.of(
                new NewsItem("RavenClient Update",
                        "New animated UI with enhanced visuals, smooth page transitions, and a central player skin display. Check it out!",
                        "Update", 0),
                new NewsItem("New Minecraft Release",
                        "The latest Minecraft update is now supported. Download the new version from the Versions tab.",
                        "Minecraft", 0));
    }
}
