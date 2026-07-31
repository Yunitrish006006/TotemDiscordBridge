package dev.totem.discord.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangVanillaLanguageDownloaderTest {
    @TempDir
    Path cacheDirectory;

    @Test
    void downloadsVerifiedLanguageObjectAndReusesValidCache() throws Exception {
        URI versionUri = URI.create("https://piston-meta.mojang.com/v1/packages/version/26.2.json");
        URI indexUri = URI.create("https://piston-meta.mojang.com/v1/packages/index/32.json");
        byte[] language = """
                {"advancements.story.mine_stone.title":"石器時代"}
                """.getBytes(StandardCharsets.UTF_8);
        String languageSha1 = sha1(language);
        URI languageUri = URI.create(
                "https://resources.download.minecraft.net/"
                        + languageSha1.substring(0, 2)
                        + "/"
                        + languageSha1
        );
        byte[] index = ("""
                {"objects":{"minecraft/lang/zh_tw.json":{"hash":"%s","size":%d}}}
                """.formatted(languageSha1, language.length)).getBytes(StandardCharsets.UTF_8);
        byte[] version = ("""
                {"assetIndex":{"url":"%s","sha1":"%s","size":%d}}
                """.formatted(indexUri, sha1(index), index.length)).getBytes(StandardCharsets.UTF_8);
        byte[] manifest = ("""
                {"versions":[{"id":"26.2","url":"%s","sha1":"%s"}]}
                """.formatted(versionUri, sha1(version))).getBytes(StandardCharsets.UTF_8);

        Map<URI, byte[]> responses = Map.of(
                MojangVanillaLanguageDownloader.VERSION_MANIFEST_URI, manifest,
                versionUri, version,
                indexUri, index,
                languageUri, language
        );
        AtomicInteger languageDownloads = new AtomicInteger();
        MojangVanillaLanguageDownloader.Fetcher fetcher = uri -> {
            if (languageUri.equals(uri)) {
                languageDownloads.incrementAndGet();
            }
            byte[] response = responses.get(uri);
            if (response == null) {
                throw new AssertionError("Unexpected URI: " + uri);
            }
            return response;
        };
        Path cache = cacheDirectory.resolve("minecraft-26.2-zh_tw.json");

        assertTrue(MojangVanillaLanguageDownloader.download("26.2", cache, fetcher));
        assertEquals(new String(language, StandardCharsets.UTF_8), Files.readString(cache));
        assertFalse(MojangVanillaLanguageDownloader.download("26.2", cache, fetcher));
        assertEquals(1, languageDownloads.get());
    }

    private static String sha1(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }
}
