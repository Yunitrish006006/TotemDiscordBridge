package dev.totem.discord.domain;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordAdvancementFormatterTest {
    @Test
    void missingTranslationUsesComponentFallback() {
        assertEquals(
                "Alex 完成了挑戰「Sky High」",
                DiscordEventFormatter.advancementMessage(
                        "Alex",
                        "example:quests/sky_high",
                        Component.translatableWithFallback(
                                "advancements.example.sky_high.title",
                                "Sky High"
                        ),
                        "challenge"
                )
        );
    }

    @Test
    void missingTranslationUsesReadableAdvancementId() {
        assertEquals(
                "Alex 完成了進度「Sky High」",
                DiscordEventFormatter.advancementMessage(
                        "Alex",
                        "example:quests/sky_high",
                        Component.translatable("advancements.example.sky_high.title"),
                        "task"
                )
        );
    }

    @Test
    void notificationCarriesAdvancementIdIntoFallback() throws Exception {
        List<DiscordEventPayload> captured = new ArrayList<>();
        try (AutoCloseable ignored = DiscordEventDispatcher.observeForTesting(captured::add)) {
            DiscordEventNotifications.advancement(
                    "Alex",
                    "example:quests/sky_high",
                    Component.translatable("advancements.example.sky_high.title"),
                    "task"
            );
        }
        assertEquals(List.of(new DiscordEventPayload(
                "advancement",
                "Alex",
                "Alex 完成了進度「Sky High」"
        )), captured);
    }
}
