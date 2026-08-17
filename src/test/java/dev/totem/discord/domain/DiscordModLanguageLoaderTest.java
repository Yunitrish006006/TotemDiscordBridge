package dev.totem.discord.domain;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordModLanguageLoaderTest {
    @TempDir
    Path modRoot;

    @Test
    void loadsEnglishFallbackAndLetsTraditionalChineseOverrideIt() throws Exception {
        Path languageDirectory = Files.createDirectories(modRoot.resolve("assets/example/lang"));
        Files.writeString(
                languageDirectory.resolve("en_us.json"),
                """
                {
                  "advancements.example.sky_high.title": "Sky High",
                  "advancements.example.english_only.title": "English Only"
                }
                """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                languageDirectory.resolve("zh_tw.json"),
                """
                {
                  "advancements.example.sky_high.title": "飛向天際"
                }
                """,
                StandardCharsets.UTF_8
        );

        Map<String, String> previous = DiscordLocalizationService.snapshotForTesting();
        Map<String, String> loaded = new LinkedHashMap<>(previous);
        assertEquals(3, DiscordLocalizationService.mergeModLanguageRoots(List.of(modRoot), loaded));
        assertEquals("飛向天際", loaded.get("advancements.example.sky_high.title"));
        assertEquals("English Only", loaded.get("advancements.example.english_only.title"));

        try {
            DiscordLocalizationService.replaceSnapshotForTesting(loaded);
            assertEquals(
                    "Alex 完成了進度「飛向天際」",
                    DiscordEventFormatter.advancementMessage(
                            "Alex",
                            "example:quests/sky_high",
                            Component.translatable("advancements.example.sky_high.title"),
                            "task"
                    )
            );
        } finally {
            DiscordLocalizationService.replaceSnapshotForTesting(previous);
        }
    }
}
