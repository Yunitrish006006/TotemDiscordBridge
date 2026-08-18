package dev.totem.discord.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordVillagerFormatterTest {
    @Test
    void localizesProfessionAndMerchantLevelsToTraditionalChinese() {
        assertEquals(
                "村民（農夫）升級：新手 → 學徒",
                DiscordEventFormatter.villagerLevelUpMessage("", "farmer", 1, 2)
        );
    }

    @Test
    void keepsCustomNameWhileLocalizingProfessionAndLevels() {
        assertEquals(
                "阿明（圖書管理員）升級：專家 → 大師",
                DiscordEventFormatter.villagerLevelUpMessage("阿明", "librarian", 4, 5)
        );
    }
}
