package org.ravenclient.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {
    }

    public static byte[] getBytes(String url) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "RavenClient/1.0.0")
                    .GET()
                    .timeout(Duration.ofMinutes(2))
                    .build();
            HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IOException("GET " + url + " returned HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }

    public static String getString(String url) throws IOException {
        return new String(getBytes(url), StandardCharsets.UTF_8);
    }

    /** POSTs a JSON body and returns the response body as a UTF-8 string. */
    public static String postJson(String url, String jsonBody) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "RavenClient/1.0.0")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .timeout(Duration.ofMinutes(2))
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IOException("POST " + url + " returned HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while POSTing to " + url, e);
        }
    }

    /** Streams a URL to disk (safe for large files). The caller owns the target path. */
    public static void download(String url, Path target) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "RavenClient/1.0.0")
                    .GET()
                    .timeout(Duration.ofMinutes(15))
                    .build();
            HttpResponse<Path> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(target));
            if (resp.statusCode() != 200) {
                Files.deleteIfExists(target);
                throw new IOException("GET " + url + " returned HTTP " + resp.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }
}
