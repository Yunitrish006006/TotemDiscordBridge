package dev.totem.discord.domain;

import net.minecraft.network.chat.Component;

public final class DiscordEventNotifications {
    private DiscordEventNotifications() {
    }

    public static void advancement(String playerName, Component title, String frameType) {
        advancement(playerName, "", title, frameType);
    }

    public static void advancement(
            String playerName,
            String advancementId,
            Component title,
            String frameType
    ) {
        String message = DiscordEventFormatter.advancementMessage(
                playerName,
                advancementId,
                title,
                frameType
        );
        DiscordEventDispatcher.send("advancement", playerName, message);
    }

    public static void villagerLevelUp(
            String customName,
            String professionPath,
            int previousLevel,
            int currentLevel
    ) {
        String message = DiscordEventFormatter.villagerLevelUpMessage(
                customName,
                professionPath,
                previousLevel,
                currentLevel
        );
        DiscordEventDispatcher.send(
                "villager_level_up",
                DiscordLocalizationService.translate("discord.deadrecall.system"),
                message
        );
    }

    public static void death(Component deathMessage) {
        DiscordEventDispatcher.send(
                "player_death",
                DiscordLocalizationService.translate("discord.deadrecall.death.unknown"),
                DiscordEventFormatter.deathMessage(deathMessage)
        );
    }

    public static void bossDefeated(Component bossName, String killerName) {
        String normalizedKiller = normalize(killerName);
        DiscordEventDispatcher.send(
                "boss_defeated",
                normalizedKiller.isEmpty()
                        ? DiscordLocalizationService.translate("discord.deadrecall.system")
                        : normalizedKiller,
                DiscordEventFormatter.bossDefeatedMessage(bossName, normalizedKiller)
        );
    }

    public static void raidEnded(String result) {
        DiscordEventDispatcher.send(
                "raid_ended",
                DiscordLocalizationService.translate("discord.deadrecall.system"),
                DiscordEventFormatter.raidEndedMessage(result)
        );
    }

    public static void difficultyChanged(String actor, String difficultyPath) {
        String normalizedActor = normalize(actor);
        String source = normalizedActor.isEmpty() ? "server" : normalizedActor;
        DiscordEventDispatcher.send(
                "difficulty_changed",
                source,
                DiscordEventFormatter.difficultyChangedMessage(source, difficultyPath)
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
