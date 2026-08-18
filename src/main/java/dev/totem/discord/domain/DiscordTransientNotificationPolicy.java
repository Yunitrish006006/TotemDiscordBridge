package dev.totem.discord.domain;

import com.google.gson.JsonObject;

import java.util.Set;

/**
 * The module-owned source of truth for Discord notification lifetimes.
 *
 * <p>The Worker only executes the {@code delete_after_seconds} instruction attached here; it does
 * not maintain its own event allowlist.</p>
 */
public final class DiscordTransientNotificationPolicy {
    public static final int DELETE_AFTER_SECONDS = 10 * 60;

    private static final Set<String> TEMPORARY_EVENTS = Set.of(
            "player_join",
            "player_first_join",
            "player_leave",
            "death_backpack_created",
            "death_backpack_recovered",
            "server_status"
    );

    private DiscordTransientNotificationPolicy() {
    }

    public static boolean isTemporaryEvent(String event) {
        return event != null && TEMPORARY_EVENTS.contains(event);
    }

    public static int deleteAfterSeconds(String event) {
        return isTemporaryEvent(event) ? DELETE_AFTER_SECONDS : 0;
    }

    public static void applyDeletionHint(JsonObject payload, String event) {
        int seconds = deleteAfterSeconds(event);
        if (seconds > 0) {
            payload.addProperty("delete_after_seconds", seconds);
        } else {
            payload.remove("delete_after_seconds");
        }
    }
}
