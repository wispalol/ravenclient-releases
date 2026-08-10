package org.ravenclient.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ravenclient.auth.Account;
import org.ravenclient.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AccountStore {

    public static final String ACCOUNTS_FILE = "accounts.json";

    private AccountStore() {
    }

    public static Account load(Path launcherDir) throws IOException {
        Path file = launcherDir.resolve(ACCOUNTS_FILE);
        if (!Files.exists(file)) return null;
        ObjectNode node = (ObjectNode) Json.mapper().readTree(file.toFile());
        return new Account(
                node.path("msRefreshToken").asText(null),
                node.path("msAccessToken").asText(null),
                node.path("minecraftToken").asText(null),
                node.path("xuid").asText(null),
                node.path("uuid").asText(null),
                node.path("username").asText(null));
    }

    public static void save(Path launcherDir, Account account) throws IOException {
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("msRefreshToken", account.msRefreshToken());
        node.put("msAccessToken", account.msAccessToken());
        node.put("minecraftToken", account.minecraftToken());
        node.put("xuid", account.xuid());
        node.put("uuid", account.uuid());
        node.put("username", account.username());
        Files.write(launcherDir.resolve(ACCOUNTS_FILE),
                Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(node));
    }

    public static void delete(Path launcherDir) throws IOException {
        Files.deleteIfExists(launcherDir.resolve(ACCOUNTS_FILE));
    }
}
