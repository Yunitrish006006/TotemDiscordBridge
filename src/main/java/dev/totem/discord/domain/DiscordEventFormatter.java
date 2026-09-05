package dev.totem.discord.domain;

import net.minecraft.network.chat.Component;

public final class DiscordEventFormatter {
    private DiscordEventFormatter() {
    }

    public static String advancementMessage(String playerName, Component title, String frameType) {
        return advancementMessage(playerName, "", title, frameType);
    }

    public static String advancementMessage(
            String playerName,
            String advancementId,
            Component title,
            String frameType
    ) {
        String name = normalize(playerName);
        String localizedTitle = DiscordLocalizationService.renderAdvancementTitle(title, advancementId);
        if (localizedTitle.isEmpty()) {
            localizedTitle = DiscordLocalizationService.translate("discord.deadrecall.advancement.unknown");
        }
        String type = DiscordLocalizationService.translate(switch (normalize(frameType)) {
            case "goal" -> "discord.deadrecall.advancement.goal";
            case "challenge" -> "discord.deadrecall.advancement.challenge";
            default -> "discord.deadrecall.advancement.task";
        });
        return DiscordLocalizationService.format(
                "discord.deadrecall.advancement.message",
                name,
                type,
                localizedTitle
        );
    }

    public static String villagerLevelUpMessage(
            String customName,
            String professionPath,
            int previousLevel,
            int currentLevel
    ) {
        String normalizedCustomName = normalize(customName);
        String villagerName = normalizedCustomName.isEmpty()
                ? DiscordLocalizationService.translate("entity.minecraft.villager")
                : normalizedCustomName;
        String normalizedProfessionPath = normalizeProfessionPath(professionPath);
        String professionKey = "entity.minecraft.villager." + normalizedProfessionPath;
        String profession = DiscordLocalizationService.translate(professionKey);
        String previous = DiscordLocalizationService.translate("merchant.level." + clampLevel(previousLevel));
        String current = DiscordLocalizationService.translate("merchant.level." + clampLevel(currentLevel));

        if ("none".equals(normalizedProfessionPath) || !DiscordLocalizationService.hasTranslation(professionKey)) {
            return DiscordLocalizationService.format(
                    "discord.deadrecall.villager.level_up",
                    villagerName,
                    previous,
                    current
            );
        }
        return DiscordLocalizationService.format(
                "discord.deadrecall.villager.level_up.with_profession",
                villagerName,
                profession,
                previous,
                current
        );
    }

    public static String deathMessage(Component deathMessage) {
        String localized = DiscordLocalizationService.render(deathMessage);
        return localized.isEmpty()
                ? DiscordLocalizationService.translate("discord.deadrecall.death.unknown")
                : localized;
    }

    public static String bossDefeatedMessage(Component bossName, String killerName) {
        String boss = DiscordLocalizationService.render(bossName);
        if (boss.isEmpty()) {
            boss = DiscordLocalizationService.translate("discord.deadrecall.entity.unknown");
        }

        String killer = normalize(killerName);
        return killer.isEmpty()
                ? DiscordLocalizationService.format("discord.deadrecall.boss.defeated", boss)
                : DiscordLocalizationService.format("discord.deadrecall.boss.defeated.by", killer, boss);
    }

    public static String raidEndedMessage(String result) {
        String resultKey = switch (normalize(result)) {
            case "victory" -> "event.minecraft.raid.victory";
            case "defeat", "loss" -> "event.minecraft.raid.defeat";
            case "stopped" -> "discord.deadrecall.raid.stopped";
            default -> "discord.deadrecall.raid.ended";
        };
        return DiscordLocalizationService.format(
                "discord.deadrecall.raid.ended.message",
                DiscordLocalizationService.translate(resultKey)
        );
    }

    public static String difficultyChangedMessage(String actor, String difficultyPath) {
        String source = normalize(actor);
        if (source.isEmpty()) {
            source = DiscordLocalizationService.translate("discord.deadrecall.server");
        }
        String difficulty = DiscordLocalizationService.translate(
                "options.difficulty." + normalize(difficultyPath).toLowerCase(java.util.Locale.ROOT)
        );
        return DiscordLocalizationService.format("discord.deadrecall.difficulty.changed", source, difficulty);
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(5, level));
    }

    private static String normalizeProfessionPath(String professionPath) {
        String normalized = normalize(professionPath);
        return normalized.isEmpty() ? "none" : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
