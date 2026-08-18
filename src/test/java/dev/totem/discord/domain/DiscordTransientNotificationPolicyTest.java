package dev.totem.discord.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordTransientNotificationPolicyTest {
    @Test
    void moduleOwnsTheCompleteTemporaryEventPolicy() {
        for (String event : new String[] {
                "player_join",
                "player_first_join",
                "player_leave",
                "death_backpack_created",
                "death_backpack_recovered",
                "server_status"
        }) {
            assertTrue(DiscordTransientNotificationPolicy.isTemporaryEvent(event), event);
            assertEquals(600, DiscordTransientNotificationPolicy.deleteAfterSeconds(event), event);
        }

        assertFalse(DiscordTransientNotificationPolicy.isTemporaryEvent("chat"));
        assertFalse(DiscordTransientNotificationPolicy.isTemporaryEvent("player_death"));
        assertFalse(DiscordTransientNotificationPolicy.isTemporaryEvent(null));
    }

    @Test
    void appliesDeletionInstructionOnlyForTemporaryEvents() {
        JsonObject temporary = new JsonObject();
        DiscordTransientNotificationPolicy.applyDeletionHint(temporary, "player_leave");
        assertEquals(600, temporary.get("delete_after_seconds").getAsInt());

        JsonObject permanent = new JsonObject();
        permanent.addProperty("delete_after_seconds", 123);
        DiscordTransientNotificationPolicy.applyDeletionHint(permanent, "chat");
        assertFalse(permanent.has("delete_after_seconds"));
    }

    @Test
    void workerPayloadsCarryOnlyTheModuleDecision() {
        JsonArray channels = new JsonArray();
        channels.add("101");

        JsonObject leave = DiscordWorkerPayloadFactory.event(
                "player_leave", "Alex", "離開伺服器", channels
        );
        assertEquals(600, leave.get("delete_after_seconds").getAsInt());

        JsonObject chat = DiscordWorkerPayloadFactory.event(
                "chat", "Alex", "hello", channels
        );
        assertFalse(chat.has("delete_after_seconds"));

        JsonObject status = DiscordWorkerPayloadFactory.serverStatus(
                "online", true, 1, 20, "26.2", 20.0D, channels
        );
        assertEquals(600, status.get("delete_after_seconds").getAsInt());
        assertEquals("online", status.getAsJsonObject("discord_presence").get("status").getAsString());
        assertEquals("1/20 人在線", status.getAsJsonObject("discord_presence").get("activity_name").getAsString());
        assertEquals(0, status.getAsJsonObject("discord_presence").get("activity_type").getAsInt());

        JsonObject offline = DiscordWorkerPayloadFactory.presence(false, 0, 20);
        assertEquals("idle", offline.getAsJsonObject("discord_presence").get("status").getAsString());
        assertEquals("伺服器離線", offline.getAsJsonObject("discord_presence").get("activity_name").getAsString());
    }
}
