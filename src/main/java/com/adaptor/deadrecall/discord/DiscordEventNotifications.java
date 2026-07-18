package com.adaptor.deadrecall.discord;

import net.minecraft.network.chat.Component;

public final class DiscordEventNotifications {
    private DiscordEventNotifications() {
    }

    public static void advancement(String playerName, Component title, String frameType) {
        String message = DiscordEventFormatter.advancementMessage(playerName, title, frameType);
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
        DiscordEventDispatcher.send("villager_level_up", "系統", message);
    }
}
