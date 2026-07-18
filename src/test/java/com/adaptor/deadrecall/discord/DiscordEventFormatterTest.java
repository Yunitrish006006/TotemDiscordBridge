package com.adaptor.deadrecall.discord;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiscordEventFormatterTest {
    @Test
    void rendersVanillaAdvancementTitleInTraditionalChinese() {
        assertEquals(
                "Alex 完成了進度「石器時代」",
                DiscordEventFormatter.advancementMessage(
                        "Alex",
                        Component.translatable("advancements.story.mine_stone.title"),
                        "task"
                )
        );
    }

    @Test
    void mapsEveryAdvancementFrameTypeToChinese() {
        Component title = Component.translatable("advancements.story.mine_diamond.title");
        assertEquals("Alex 完成了進度「鑽石！」", DiscordEventFormatter.advancementMessage("Alex", title, "task"));
        assertEquals("Alex 完成了目標「鑽石！」", DiscordEventFormatter.advancementMessage("Alex", title, "goal"));
        assertEquals("Alex 完成了挑戰「鑽石！」", DiscordEventFormatter.advancementMessage("Alex", title, "challenge"));
    }

    @Test
    void rendersNestedComponentArgumentsAndPreservesLiteralNames() {
        Component nested = Component.translatable(
                "discord.deadrecall.test.nested",
                Component.literal("PlayerOne"),
                Component.literal("Excalibur-E")
        );
        assertEquals("PlayerOne 使用 Excalibur-E", DiscordLocalizationService.render(nested));
    }

    @Test
    void unknownTranslationKeyDoesNotLeakRawKey() {
        String rendered = DiscordLocalizationService.render(
                Component.translatable("advancements.example.missing.title")
        );
        assertEquals("未知進度", rendered);
        assertFalse(rendered.contains("advancements.example"));
    }

    @Test
    void formatsUnnamedLibrarianLevelUpWithChineseCareerNames() {
        assertEquals(
                "村民（圖書管理員）升級：新手 → 學徒",
                DiscordEventFormatter.villagerLevelUpMessage("", "librarian", 1, 2)
        );
    }

    @Test
    void preservesCustomVillagerNameWhileLocalizingProfessionAndLevels() {
        assertEquals(
                "Archivist E（圖書管理員）升級：學徒 → 老手",
                DiscordEventFormatter.villagerLevelUpMessage("Archivist E", "librarian", 2, 3)
        );
    }
}
