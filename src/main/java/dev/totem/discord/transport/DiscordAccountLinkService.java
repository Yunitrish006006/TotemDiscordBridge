package dev.totem.discord.transport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.totem.discord.TotemDiscordBridge;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Asynchronous, server-authoritative client for the Worker's account-link API. */
public final class DiscordAccountLinkService {
    private static final int RESPONSE_LIMIT_BYTES = 64 * 1024;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "DiscordAccountLinkService-Worker");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<UUID> IN_FLIGHT = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private DiscordAccountLinkService() {
    }

    public static CompletableFuture<DiscordAccountLinkResult> verify(
            UUID playerId,
            String playerName,
            String bindCode
    ) {
        String normalizedCode = normalizeCode(bindCode);
        if (playerId == null || !validMinecraftName(playerName) || normalizedCode.isEmpty()) {
            return CompletableFuture.completedFuture(
                    DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.INVALID_REQUEST));
        }
        return exclusive(playerId, () -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("mc_uuid", playerId.toString());
            payload.addProperty("mc_name", playerName);
            payload.addProperty("bind_code", normalizedCode);
            return request("POST", "/api/mc/players/bind", payload, Operation.VERIFY);
        });
    }

    public static CompletableFuture<DiscordAccountLinkResult> status(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(
                    DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.INVALID_REQUEST));
        }
        return exclusive(playerId, () ->
                request("GET", "/api/mc/players/" + playerId, null, Operation.STATUS));
    }

    public static CompletableFuture<DiscordAccountLinkResult> unlink(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(
                    DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.INVALID_REQUEST));
        }
        return exclusive(playerId, () ->
                request("DELETE", "/api/mc/players/" + playerId, null, Operation.UNLINK));
    }

    static String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 8) return "";
        for (int index = 0; index < code.length(); index++) {
            if (CODE_ALPHABET.indexOf(code.charAt(index)) < 0) return "";
        }
        return code;
    }

    static DiscordAccountLinkResult parseResponse(int responseCode, String responseBody, Operation operation) {
        JsonObject root;
        try {
            root = JsonParser.parseString(responseBody == null ? "" : responseBody).getAsJsonObject();
        } catch (Exception ignored) {
            return DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.UNAVAILABLE);
        }

        if (responseCode >= 200 && responseCode < 300
                && root.has("success") && root.get("success").getAsBoolean()) {
            String discordName = "";
            if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject data = root.getAsJsonObject("data");
                if (data.has("discord_name") && data.get("discord_name").isJsonPrimitive()) {
                    discordName = data.get("discord_name").getAsString();
                }
            }
            return new DiscordAccountLinkResult(
                    operation == Operation.UNLINK
                            ? DiscordAccountLinkResult.Status.UNLINKED
                            : DiscordAccountLinkResult.Status.LINKED,
                    discordName,
                    0
            );
        }

        String code = root.has("code") && root.get("code").isJsonPrimitive()
                ? root.get("code").getAsString()
                : "";
        DiscordAccountLinkResult.Status status = switch (code) {
            case "not_linked" -> DiscordAccountLinkResult.Status.NOT_LINKED;
            case "invalid_or_expired" -> DiscordAccountLinkResult.Status.INVALID_CODE;
            case "invalid_request" -> DiscordAccountLinkResult.Status.INVALID_REQUEST;
            case "minecraft_name_mismatch" -> DiscordAccountLinkResult.Status.MINECRAFT_NAME_MISMATCH;
            case "minecraft_already_bound" -> DiscordAccountLinkResult.Status.MINECRAFT_ALREADY_BOUND;
            case "rate_limited" -> DiscordAccountLinkResult.Status.RATE_LIMITED;
            default -> DiscordAccountLinkResult.Status.UNAVAILABLE;
        };
        int retryAfterSeconds = 0;
        if (root.has("retry_after_seconds") && root.get("retry_after_seconds").isJsonPrimitive()) {
            try {
                retryAfterSeconds = root.get("retry_after_seconds").getAsInt();
            } catch (RuntimeException ignored) {
                retryAfterSeconds = 0;
            }
        }
        return new DiscordAccountLinkResult(status, "", retryAfterSeconds);
    }

    private static CompletableFuture<DiscordAccountLinkResult> exclusive(
            UUID playerId,
            Supplier<DiscordAccountLinkResult> operation
    ) {
        if (!IN_FLIGHT.add(playerId)) {
            return CompletableFuture.completedFuture(
                    DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.BUSY));
        }
        return CompletableFuture.supplyAsync(operation, EXECUTOR)
                .exceptionally(error -> {
                    TotemDiscordBridge.LOGGER.warn("Discord account-link request failed without exposing request data");
                    return DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.UNAVAILABLE);
                })
                .whenComplete((ignored, error) -> IN_FLIGHT.remove(playerId));
    }

    private static DiscordAccountLinkResult request(
            String method,
            String path,
            JsonObject requestBody,
            Operation operation
    ) {
        DiscordTransportService.WorkerEndpoint endpoint = DiscordTransportService.workerEndpoint();
        if (!endpoint.available() || !isAllowedEndpoint(endpoint.baseUrl())) {
            return DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.UNAVAILABLE);
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(endpoint.baseUrl() + path).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-API-Key", endpoint.apiKey());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (requestBody != null) {
                byte[] encoded = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(encoded);
                }
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            byte[] bytes;
            if (stream == null) {
                bytes = new byte[0];
            } else {
                try (stream) {
                    bytes = stream.readNBytes(RESPONSE_LIMIT_BYTES + 1);
                }
            }
            if (bytes.length > RESPONSE_LIMIT_BYTES) {
                return DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.UNAVAILABLE);
            }
            DiscordAccountLinkResult parsed = parseResponse(
                    responseCode,
                    new String(bytes, StandardCharsets.UTF_8),
                    operation
            );
            if (parsed.status() == DiscordAccountLinkResult.Status.RATE_LIMITED) {
                String retryAfter = connection.getHeaderField("Retry-After");
                if (retryAfter != null) {
                    try {
                        return new DiscordAccountLinkResult(parsed.status(), "", Integer.parseInt(retryAfter));
                    } catch (NumberFormatException ignored) {
                        // Keep the bounded JSON fallback, if any.
                    }
                }
            }
            return parsed;
        } catch (Exception ignored) {
            return DiscordAccountLinkResult.of(DiscordAccountLinkResult.Status.UNAVAILABLE);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    enum Operation {
        VERIFY,
        STATUS,
        UNLINK
    }

    static boolean isAllowedEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || host.isEmpty()) {
                return false;
            }
            if ("https".equals(scheme)) return true;
            return "http".equals(scheme) && ("localhost".equals(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || "[::1]".equals(host));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean validMinecraftName(String value) {
        if (value == null || value.length() < 3 || value.length() > 16) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean asciiLetter = character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z';
            boolean asciiDigit = character >= '0' && character <= '9';
            if (!(asciiLetter || asciiDigit || character == '_')) return false;
        }
        return true;
    }
}
