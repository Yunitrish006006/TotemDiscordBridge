package dev.totem.discord.domain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

final class MojangVanillaLanguageDownloader {
    static final URI VERSION_MANIFEST_URI =
            URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String METADATA_HOST = "piston-meta.mojang.com";
    private static final String ASSET_HOST = "resources.download.minecraft.net";
    private static final String LANGUAGE_OBJECT_KEY = "minecraft/lang/zh_tw.json";
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private MojangVanillaLanguageDownloader() {
    }

    static void refreshAsync(
            String minecraftVersion,
            Path cacheFile,
            Logger logger,
            Runnable onUpdated
    ) {
        Thread.ofVirtual().name("discord-bridge-zh-tw-downloader").start(() -> {
            try {
                if (download(minecraftVersion, cacheFile, new HttpFetcher())) {
                    logger.info("[DiscordBridge] 已下載並快取 Minecraft {} 官方 zh_tw 語言檔", minecraftVersion);
                    onUpdated.run();
                } else {
                    logger.info("[DiscordBridge] Minecraft {} 官方 zh_tw 快取已是最新版本", minecraftVersion);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logger.warn("[DiscordBridge] Minecraft {} 官方 zh_tw 下載遭中斷，使用目前 fallback", minecraftVersion);
            } catch (Exception exception) {
                logger.warn(
                        "[DiscordBridge] 無法下載 Minecraft {} 官方 zh_tw，使用快取或目前 fallback",
                        minecraftVersion,
                        exception
                );
            }
        });
    }

    static boolean download(String minecraftVersion, Path cacheFile, Fetcher fetcher)
            throws IOException, InterruptedException {
        String normalizedVersion = requireNonBlank(minecraftVersion, "minecraft version");

        JsonObject manifest = parseObject(fetch(fetcher, VERSION_MANIFEST_URI), "version manifest");
        JsonObject versionEntry = findVersion(manifest, normalizedVersion);
        URI versionUri = trustedUri(requiredString(versionEntry, "url"), METADATA_HOST);
        String versionSha1 = requiredSha1(versionEntry, "sha1");
        byte[] versionBytes = fetch(fetcher, versionUri);
        verifySha1(versionBytes, versionSha1, "version metadata");

        JsonObject versionMetadata = parseObject(versionBytes, "version metadata");
        JsonObject assetIndex = requiredObject(versionMetadata, "assetIndex");
        URI assetIndexUri = trustedUri(requiredString(assetIndex, "url"), METADATA_HOST);
        String assetIndexSha1 = requiredSha1(assetIndex, "sha1");
        long assetIndexSize = requiredPositiveLong(assetIndex, "size");
        byte[] assetIndexBytes = fetch(fetcher, assetIndexUri);
        verifySizeAndSha1(assetIndexBytes, assetIndexSize, assetIndexSha1, "asset index");

        JsonObject objects = requiredObject(parseObject(assetIndexBytes, "asset index"), "objects");
        JsonObject languageObject = requiredObject(objects, LANGUAGE_OBJECT_KEY);
        String languageSha1 = requiredSha1(languageObject, "hash");
        long languageSize = requiredPositiveLong(languageObject, "size");

        if (Files.isRegularFile(cacheFile)) {
            byte[] cached = Files.readAllBytes(cacheFile);
            if (cached.length == languageSize
                    && languageSha1.equals(sha1(cached))
                    && isTranslationTable(cached)) {
                return false;
            }
        }

        URI languageUri = trustedUri(
                "https://" + ASSET_HOST + "/" + languageSha1.substring(0, 2) + "/" + languageSha1,
                ASSET_HOST
        );
        byte[] languageBytes = fetch(fetcher, languageUri);
        verifySizeAndSha1(languageBytes, languageSize, languageSha1, "zh_tw language object");
        if (!isTranslationTable(languageBytes)) {
            throw new IOException("Mojang zh_tw language object is not a JSON translation table");
        }
        writeAtomically(cacheFile, languageBytes);
        return true;
    }

    private static JsonObject findVersion(JsonObject manifest, String minecraftVersion) throws IOException {
        JsonElement versions = manifest.get("versions");
        if (versions == null || !versions.isJsonArray()) {
            throw new IOException("Mojang version manifest has no versions array");
        }
        for (JsonElement element : versions.getAsJsonArray()) {
            if (element.isJsonObject()) {
                JsonObject candidate = element.getAsJsonObject();
                JsonElement id = candidate.get("id");
                if (id != null && id.isJsonPrimitive() && minecraftVersion.equals(id.getAsString())) {
                    return candidate;
                }
            }
        }
        throw new IOException("Minecraft version is absent from Mojang manifest: " + minecraftVersion);
    }

    private static byte[] fetch(Fetcher fetcher, URI uri) throws IOException, InterruptedException {
        byte[] response = fetcher.fetch(uri);
        if (response == null || response.length == 0) {
            throw new IOException("Empty response from " + uri);
        }
        if (response.length > MAX_RESPONSE_BYTES) {
            throw new IOException("Response exceeds " + MAX_RESPONSE_BYTES + " bytes: " + uri);
        }
        return response;
    }

    private static JsonObject parseObject(byte[] bytes, String description) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IOException(description + " must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid " + description + " JSON", exception);
        }
    }

    private static JsonObject requiredObject(JsonObject parent, String key) throws IOException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IOException("Missing JSON object: " + key);
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject parent, String key) throws IOException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Missing JSON string: " + key);
        }
        return requireNonBlank(value.getAsString(), key);
    }

    private static String requiredSha1(JsonObject parent, String key) throws IOException {
        String value = requiredString(parent, key).toLowerCase();
        if (!value.matches("[0-9a-f]{40}")) {
            throw new IOException("Invalid SHA-1 in field " + key);
        }
        return value;
    }

    private static long requiredPositiveLong(JsonObject parent, String key) throws IOException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Missing JSON number: " + key);
        }
        long result = value.getAsLong();
        if (result <= 0 || result > MAX_RESPONSE_BYTES) {
            throw new IOException("Invalid size in field " + key + ": " + result);
        }
        return result;
    }

    private static URI trustedUri(String rawUri, String expectedHost) throws IOException {
        URI uri;
        try {
            uri = URI.create(rawUri);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Mojang URI", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !expectedHost.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null) {
            throw new IOException("Untrusted Mojang URI: " + uri);
        }
        return uri;
    }

    private static void verifySizeAndSha1(
            byte[] bytes,
            long expectedSize,
            String expectedSha1,
            String description
    ) throws IOException {
        if (bytes.length != expectedSize) {
            throw new IOException(
                    description + " size mismatch: expected " + expectedSize + ", received " + bytes.length
            );
        }
        verifySha1(bytes, expectedSha1, description);
    }

    private static void verifySha1(byte[] bytes, String expectedSha1, String description)
            throws IOException {
        String actualSha1 = sha1(bytes);
        if (!expectedSha1.equals(actualSha1)) {
            throw new IOException(
                    description + " SHA-1 mismatch: expected " + expectedSha1 + ", received " + actualSha1
            );
        }
    }

    private static String sha1(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-1 is unavailable", exception);
        }
    }

    private static boolean isTranslationTable(byte[] bytes) {
        try {
            JsonObject table = parseObject(bytes, "translation table");
            return table.entrySet().stream().allMatch(entry ->
                    !entry.getKey().isBlank()
                            && entry.getValue().isJsonPrimitive()
                            && entry.getValue().getAsJsonPrimitive().isString()
            );
        } catch (IOException exception) {
            return false;
        }
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path directory = target.toAbsolutePath().normalize().getParent();
        if (directory == null) {
            throw new IOException("Language cache has no parent directory");
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String requireNonBlank(String value, String description) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Missing " + description);
        }
        return value.trim();
    }

    @FunctionalInterface
    interface Fetcher {
        byte[] fetch(URI uri) throws IOException, InterruptedException;
    }

    private static final class HttpFetcher implements Fetcher {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        @Override
        public byte[] fetch(URI uri) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "TotemDiscordBridge/zh_tw")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri);
            }
            return response.body();
        }
    }
}
