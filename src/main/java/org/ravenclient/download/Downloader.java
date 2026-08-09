package org.ravenclient.download;

import org.ravenclient.util.Http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public final class Downloader {

    public interface Progress {
        void onProgress(long done, long total, String currentFile);
    }

    public record Entry(String url, Path target, String sha1) {
    }

    private Downloader() {
    }

    public static void download(Entry entry) throws IOException {
        Path target = entry.target();
        if (entry.sha1() != null && !entry.sha1().isBlank() && Files.exists(target)) {
            try {
                if (sha1(target).equalsIgnoreCase(entry.sha1())) return;
            } catch (IOException ignored) {
                // corrupted / unreadable file -> redownload
            }
        }
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        Http.download(entry.url(), tmp);
        if (entry.sha1() != null && !entry.sha1().isBlank()) {
            String actual = sha1(tmp);
            if (!actual.equalsIgnoreCase(entry.sha1())) {
                Files.deleteIfExists(tmp);
                throw new IOException("Checksum mismatch for " + entry.url()
                        + " (expected " + entry.sha1() + ", got " + actual + ")");
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void downloadAll(List<Entry> entries, int threads, Progress progress) throws IOException {
        if (entries.isEmpty()) return;
        // Collapse duplicate targets: asset indexes can reference the same file under
        // several names, and two threads must never share a .part file.
        Map<Path, Entry> unique = new LinkedHashMap<>();
        for (Entry e : entries) unique.putIfAbsent(e.target(), e);
        List<Entry> list = new ArrayList<>(unique.values());
        AtomicLong done = new AtomicLong();
        long total = list.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Future<?>[] futures = new Future<?>[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Entry e = list.get(i);
                futures[i] = pool.submit(() -> {
                    try {
                        download(e);
                    } catch (IOException ex) {
                        throw new CompletionException(ex);
                    } finally {
                        if (progress != null) {
                            progress.onProgress(done.incrementAndGet(), total,
                                    e.target().getFileName().toString());
                        }
                    }
                });
            }
            for (Future<?> f : futures) f.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Downloads interrupted");
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() instanceof CompletionException && ee.getCause().getCause() != null
                    ? ee.getCause().getCause() : ee.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("Download failed", cause);
        } finally {
            pool.shutdownNow();
        }
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available", e);
        }
    }
}
