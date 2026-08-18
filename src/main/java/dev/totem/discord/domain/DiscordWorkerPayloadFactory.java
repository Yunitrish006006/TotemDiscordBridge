package dev.totem.discord.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Locale;

/** Builds Worker payloads and applies the module-owned lifetime policy exactly once. */
public final class DiscordWorkerPayloadFactory {
    private DiscordWorkerPayloadFactory() {
    }

    public static JsonObject event(
            String event,
            String username,
            String message,
            JsonArray channels
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("event", event == null ? "" : event);
        payload.addProperty("username", username == null ? "" : username);
        payload.addProperty("message", message == null ? "" : message);
        payload.add("channels", channels);
        DiscordTransientNotificationPolicy.applyDeletionHint(payload, event);
        return payload;
    }

    public static JsonObject serverStatus(
            String status,
            boolean serverOnline,
            int playersOnline,
            int playersMax,
            String version,
            double tps,
            JsonArray channels
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", status == null ? "" : status);
        payload.addProperty("players_online", playersOnline);
        payload.addProperty("players_max", playersMax);
        payload.addProperty("version", version == null ? "" : version);
        payload.addProperty("tps", tps);
        payload.add("channels", channels);
        payload.add("discord_presence", discordPresence(serverOnline, playersOnline, playersMax));
        DiscordTransientNotificationPolicy.applyDeletionHint(payload, "server_status");
        return payload;
    }

    public static JsonObject presence(boolean serverOnline, int playersOnline, int playersMax) {
        JsonObject payload = new JsonObject();
        payload.add("discord_presence", discordPresence(serverOnline, playersOnline, playersMax));
        return payload;
    }

    private static JsonObject discordPresence(boolean serverOnline, int playersOnline, int playersMax) {
        JsonObject presence = new JsonObject();
        presence.addProperty("status", serverOnline ? "online" : "idle");
        presence.addProperty(
                "activity_name",
                serverOnline
                        ? String.format(Locale.ROOT, "%d/%d 人在線", playersOnline, playersMax)
                        : "伺服器離線"
        );
        // Discord activity type 0 = Playing. The module owns this presentation decision.
        presence.addProperty("activity_type", 0);
        return presence;
    }
}
