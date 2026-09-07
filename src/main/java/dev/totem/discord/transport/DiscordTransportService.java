package dev.totem.discord.transport;

import dev.totem.core.api.v1.event.LockedContainerNetworkBrokenEvent;
import dev.totem.discord.domain.DiscordEventNotifications;
import dev.totem.discord.domain.DiscordLocalizationService;
import dev.totem.discord.domain.DiscordWorkerPayloadFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discord 聊天橋接工具
 * 負責將 Minecraft 聊天訊息透過 Cloudflare Worker API 傳送到 Discord
 * 支援多頻道管理
 */
public class DiscordTransportService {
    private static final Logger LOGGER = LoggerFactory.getLogger("TotemDiscordTransportService");
    private static final int DELIVERY_FAILURE_ALERT_THRESHOLD = 3;
    private static final int MAX_CHANNELS = 10;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DiscordTransportService-Worker");
        t.setDaemon(true);
        return t;
    });

    private static String workerUrl = "";
    private static String apiKey = "";
    private static boolean enabled = false;
    private static List<DiscordChannel> channels = new ArrayList<>();
    private static Path configFilePath;
    private static final Set<String> announcedRaidKeys = new HashSet<>();
    private static int consecutiveDeliveryFailures = 0;
    private static boolean deliveryFailureAlertReported = false;

    public static class DiscordChannel {
        public String id;
        public String name;

        public DiscordChannel(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public DiscordChannel(String id) {
            this(id, id);
        }
    }

    /**
     * 初始化設定（從 config 檔讀取）
     */
    public static void init(Path configDir) {
        configFilePath = configDir.resolve("discord-bridge.json");

        if (!Files.exists(configFilePath)) {
            createDefaultConfig(configFilePath);
            LOGGER.info("[DiscordTransportService] 已建立預設設定檔: {}", configFilePath);
            LOGGER.info("[DiscordTransportService] 請編輯設定檔填入 Worker URL 和 API Key");
            return;
        }

        loadConfigFromFile();
    }

    public static synchronized void updateConfig(boolean newEnabled, String newWorkerUrl, String newApiKey) throws IOException {
        if (configFilePath == null) {
            throw new IllegalStateException("DiscordTransportService 尚未初始化");
        }
        String normalizedWorkerUrl = normalizeWorkerUrl(newWorkerUrl);
        String normalizedApiKey = newApiKey == null ? "" : newApiKey.trim();
        String existingApiKey = apiKey == null ? "" : apiKey.trim();
        String effectiveApiKey = normalizedApiKey.isEmpty() ? existingApiKey : normalizedApiKey;
        if (newEnabled && (normalizedWorkerUrl.isEmpty() || effectiveApiKey.isEmpty())) {
            throw new IllegalArgumentException("啟用 Discord Bridge 時，workerUrl 與 apiKey 不能為空");
        }

        JsonObject config = new JsonObject();
        config.addProperty("enabled", newEnabled);
        config.addProperty("workerUrl", normalizedWorkerUrl);
        config.addProperty("apiKey", effectiveApiKey);

        // 保留現有的 channels
        JsonArray channelArray = new JsonArray();
        for (DiscordChannel ch : channels) {
            JsonObject chObj = new JsonObject();
            chObj.addProperty("id", ch.id);
            chObj.addProperty("name", ch.name);
            channelArray.add(chObj);
        }
        config.add("channels", channelArray);

        Files.createDirectories(configFilePath.getParent());
        Files.writeString(configFilePath, config.toString(), StandardCharsets.UTF_8);
        loadConfigFromFile();
    }

    public static synchronized void addChannel(String channelId, String channelName) throws IOException {
        if (configFilePath == null) {
            throw new IllegalStateException("DiscordTransportService 尚未初始化");
        }
        String normalizedId = normalizeChannelId(channelId);
        validateChannelId(normalizedId);
        if (channels.size() >= MAX_CHANNELS) {
            throw new IllegalArgumentException("最多只能配置 " + MAX_CHANNELS + " 個 Discord 頻道");
        }
        String normalizedName = normalizeChannelName(channelName, normalizedId);

        // 檢查是否已存在
        for (DiscordChannel ch : channels) {
            if (ch.id.equals(normalizedId)) {
                throw new IllegalArgumentException("頻道 " + normalizedId + " 已存在");
            }
        }

        channels.add(new DiscordChannel(normalizedId, normalizedName));
        saveChannelsToFile();
        LOGGER.info("[DiscordTransportService] 已添加頻道: {} ({})", normalizedName, normalizedId);
    }

    public static synchronized void removeChannel(String channelId) throws IOException {
        if (configFilePath == null) {
            throw new IllegalStateException("DiscordTransportService 尚未初始化");
        }
        String normalizedId = normalizeChannelId(channelId);
        validateChannelId(normalizedId);

        boolean removed = channels.removeIf(ch -> ch.id.equals(normalizedId));
        if (!removed) {
            throw new IllegalArgumentException("頻道 " + normalizedId + " 不存在");
        }

        saveChannelsToFile();
        LOGGER.info("[DiscordTransportService] 已移除頻道: {}", normalizedId);
    }

    public static synchronized void reload() {
        if (configFilePath == null) {
            return;
        }
        loadConfigFromFile();
    }

    public static List<DiscordChannel> getChannels() {
        return new ArrayList<>(channels);
    }

    private static synchronized void saveChannelsToFile() throws IOException {
        String content = Files.readString(configFilePath, StandardCharsets.UTF_8);
        JsonObject config = JsonParser.parseString(content).getAsJsonObject();

        JsonArray channelArray = new JsonArray();
        for (DiscordChannel ch : channels) {
            JsonObject chObj = new JsonObject();
            chObj.addProperty("id", ch.id);
            chObj.addProperty("name", ch.name);
            channelArray.add(chObj);
        }
        config.add("channels", channelArray);

        Files.writeString(configFilePath, config.toString(), StandardCharsets.UTF_8);
    }

    private static synchronized void loadConfigFromFile() {
        if (configFilePath == null) {
            enabled = false;
            workerUrl = "";
            apiKey = "";
            channels.clear();
            return;
        }
        try {
            String content = Files.readString(configFilePath, StandardCharsets.UTF_8);
            JsonObject config = JsonParser.parseString(content).getAsJsonObject();

            enabled = config.has("enabled") && config.get("enabled").getAsBoolean();
            workerUrl = normalizeWorkerUrl(config.has("workerUrl") ? config.get("workerUrl").getAsString() : "");
            apiKey = config.has("apiKey") ? config.get("apiKey").getAsString() : "";

            channels.clear();
            if (config.has("channels") && config.get("channels").isJsonArray()) {
                JsonArray channelArray = config.getAsJsonArray("channels");
                Set<String> seenChannelIds = new HashSet<>();
                for (int i = 0; i < channelArray.size(); i++) {
                    JsonObject chObj = channelArray.get(i).getAsJsonObject();
                    String id = normalizeChannelId(chObj.has("id") ? chObj.get("id").getAsString() : "");
                    if (!isValidChannelId(id) || !seenChannelIds.add(id)) {
                        continue;
                    }
                    String name = normalizeChannelName(chObj.has("name") ? chObj.get("name").getAsString() : id, id);
                    channels.add(new DiscordChannel(id, name));
                    if (channels.size() >= MAX_CHANNELS) {
                        break;
                    }
                }
            }

            if (enabled && (workerUrl.isEmpty() || apiKey.isEmpty())) {
                LOGGER.warn("[DiscordTransportService] 已啟用但缺少 workerUrl 或 apiKey，停用功能");
                enabled = false;
                return;
            }

            if (enabled) {
                LOGGER.info("[DiscordTransportService] 已啟用，Worker URL: {}", workerUrl);
                LOGGER.info("[DiscordTransportService] 已配置 {} 個頻道", channels.size());
            } else {
                LOGGER.info("[DiscordTransportService] 功能已停用");
            }
        } catch (Exception e) {
            LOGGER.error("[DiscordTransportService] 讀取設定檔失敗", e);
            enabled = false;
        }
    }

    /**
     * 傳送聊天訊息到 Discord（非同步）
     */
    public static void sendChatMessage(String username, String message) {
        sendMinecraftEvent("chat", username, message);
    }

    public static void sendMinecraftEvent(String event, String username, String message) {
        if (!enabled || username == null || username.isBlank() || message == null || message.isBlank()) return;

        EXECUTOR.submit(() -> {
            try {
                String json = DiscordWorkerPayloadFactory.event(
                        event,
                        username,
                        message,
                        channelsToJsonArray()
                ).toString();

                String url = workerUrl + "/api/mc/chat";
                LOGGER.info("[DiscordTransportService] 發送請求到: {}", url);

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

                int responseCode = conn.getResponseCode();

                InputStream responseStream = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
                String responseBody = responseStream != null
                    ? new String(responseStream.readAllBytes(), StandardCharsets.UTF_8)
                    : "N/A";

                if (responseCode >= 200 && responseCode < 300) {
                    if (responseHasDiscordFailures(responseBody)) {
                        recordDeliveryFailure(event);
                        LOGGER.warn("[DiscordTransportService] Discord 端部分或全部發送失敗 (HTTP {}): {}", responseCode, responseBody);
                    } else {
                        recordDeliverySuccess();
                        LOGGER.info("[DiscordTransportService] 發送成功 (HTTP {}): {}", responseCode, responseBody);
                    }
                } else {
                    recordDeliveryFailure(event);
                    LOGGER.warn("[DiscordTransportService] 發送失敗 (HTTP {}): {}", responseCode, responseBody);
                }

                conn.disconnect();
            } catch (Exception e) {
                recordDeliveryFailure(event);
                LOGGER.error("[DiscordTransportService] 發送訊息失敗: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 回報伺服器狀態到 Discord（非同步）
     */
    public static void sendServerStatus(String status, boolean serverOnline, int playersOnline, int playersMax, String version, double tps) {
        if (!enabled) return;

        EXECUTOR.submit(() -> postServerStatus(status, serverOnline, playersOnline, playersMax, version, tps));
    }

    /**
     * 回報伺服器狀態到 Discord（同步，供關閉流程使用）
     */
    public static void sendServerStatusImmediately(String status, boolean serverOnline, int playersOnline, int playersMax, String version, double tps) {
        if (!enabled) return;

        postServerStatus(status, serverOnline, playersOnline, playersMax, version, tps);
    }

    private static void postServerStatus(String status, boolean serverOnline, int playersOnline, int playersMax, String version, double tps) {
        try {
            String json = DiscordWorkerPayloadFactory.serverStatus(
                    status,
                    serverOnline,
                    playersOnline,
                    playersMax,
                    version,
                    tps,
                    channelsToJsonArray()
            ).toString();

            String url = workerUrl + "/api/mc/server/status";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int responseCode = conn.getResponseCode();
            InputStream responseStream = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String responseBody = responseStream != null
                    ? new String(responseStream.readAllBytes(), StandardCharsets.UTF_8)
                    : "N/A";

            if (responseCode >= 200 && responseCode < 300) {
                if (responseHasDiscordFailures(responseBody)) {
                    recordDeliveryFailure("server_status");
                    LOGGER.warn("[DiscordTransportService] Discord 端狀態回報部分或全部失敗 (HTTP {}): {}", responseCode, responseBody);
                } else {
                    recordDeliverySuccess();
                    LOGGER.info("[DiscordTransportService] 狀態回報成功 (HTTP {}): {}", responseCode, responseBody);
                }
            } else {
                recordDeliveryFailure("server_status");
                LOGGER.warn("[DiscordTransportService] 狀態回報失敗 (HTTP {}): {}", responseCode, responseBody);
            }
            conn.disconnect();
        } catch (Exception e) {
            recordDeliveryFailure("server_status");
            LOGGER.warn("[DiscordTransportService] 回報狀態失敗: {}", e.getMessage());
        }
    }

    /** Updates only the Discord bot presence without creating a channel message. */
    public static void sendPresence(boolean serverOnline, int playersOnline, int playersMax) {
        if (!enabled) return;
        EXECUTOR.submit(() -> postPresence(serverOnline, playersOnline, playersMax));
    }

    private static void postPresence(boolean serverOnline, int playersOnline, int playersMax) {
        HttpURLConnection conn = null;
        try {
            String json = DiscordWorkerPayloadFactory.presence(serverOnline, playersOnline, playersMax).toString();
            String url = workerUrl + "/api/mc/presence";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int responseCode = conn.getResponseCode();
            InputStream responseStream = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String responseBody = responseStream != null
                    ? new String(responseStream.readAllBytes(), StandardCharsets.UTF_8)
                    : "N/A";

            if (responseCode >= 200 && responseCode < 300) {
                recordDeliverySuccess();
                LOGGER.debug("[DiscordTransportService] Presence 更新成功 (HTTP {})", responseCode);
            } else {
                recordDeliveryFailure("presence");
                LOGGER.warn("[DiscordTransportService] Presence 更新失敗 (HTTP {}): {}", responseCode, responseBody);
            }
        } catch (Exception e) {
            recordDeliveryFailure("presence");
            LOGGER.warn("[DiscordTransportService] Presence 更新失敗: {}", e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 通知村民升級到 Discord（非同步）
     */
    public static void sendVillagerLevelUp(String villagerName, int oldLevel, int newLevel) {
        if (!enabled) return;

        String name = (villagerName == null || villagerName.isBlank())
                ? DiscordLocalizationService.translate("entity.minecraft.villager")
                : villagerName.trim();
        String message = DiscordLocalizationService.format(
                "discord.totem.villager.level_up",
                name,
                String.valueOf(oldLevel),
                String.valueOf(newLevel)
        );
        sendMinecraftEvent("villager_level_up", DiscordLocalizationService.translate("discord.totem.system"), message);
    }

    /**
     * 通知玩家死亡訊息到 Discord（非同步）
     */
    public static void sendDeathMessage(String deathMessage) {
        if (deathMessage == null || deathMessage.isBlank()) return;
        DiscordEventNotifications.death(net.minecraft.network.chat.Component.literal(deathMessage.trim()));
    }

    /**
     * 通知玩家加入伺服器到 Discord（非同步）
     */
    public static void sendPlayerJoined(String playerName) {
        String name = normalizePlayerName(playerName);
        if (name.isEmpty()) return;

        sendMinecraftEvent("player_join", name, DiscordLocalizationService.translate("discord.totem.player.joined"));
    }

    /**
     * 通知玩家離開伺服器到 Discord（非同步）
     */
    public static void sendPlayerLeft(String playerName) {
        String name = normalizePlayerName(playerName);
        if (name.isEmpty()) return;

        sendMinecraftEvent("player_leave", name, DiscordLocalizationService.translate("discord.totem.player.left"));
    }

    public static void sendPlayerFirstJoined(String playerName) {
        String name = normalizePlayerName(playerName);
        if (name.isEmpty()) return;

        sendMinecraftEvent(
                "player_first_join",
                name,
                DiscordLocalizationService.translate("discord.totem.player.first_joined")
        );
    }

    public static void sendAdvancement(String playerName, String advancementTitle, String advancementType) {
        String name = normalizePlayerName(playerName);
        String title = normalizeText(advancementTitle);
        if (name.isEmpty() || title.isEmpty()) return;

        String type = normalizeText(advancementType);
        String localizedType = type.isEmpty()
                ? DiscordLocalizationService.translate("discord.totem.advancement.task")
                : type;
        String message = DiscordLocalizationService.format(
                "discord.totem.advancement.message",
                name,
                localizedType,
                title
        );
        sendMinecraftEvent("advancement", name, message);
    }

    public static void sendAdminAction(String actor, String action, String targetSummary) {
        String normalizedAction = normalizeText(action);
        String target = normalizeText(targetSummary);
        if (normalizedAction.isEmpty() || target.isEmpty()) return;

        String source = normalizeActor(actor);
        sendMinecraftEvent(
                "admin_action",
                source,
                DiscordLocalizationService.format("discord.totem.admin.action", source, normalizedAction, target)
        );
    }

    public static void sendServerHealthAlert(String message) {
        String text = normalizeText(message);
        if (text.isEmpty()) return;

        sendMinecraftEvent(
                "server_health_alert",
                DiscordLocalizationService.translate("discord.totem.system"),
                text
        );
    }

    public static void sendDeathBackpackCreated(String playerName) {
        String name = normalizePlayerName(playerName);
        if (name.isEmpty()) return;

        sendMinecraftEvent(
                "death_backpack_created",
                name,
                DiscordLocalizationService.format("discord.totem.death_backpack.created", name)
        );
    }

    public static void sendDeathBackpackRecovered(String playerName) {
        String name = normalizePlayerName(playerName);
        if (name.isEmpty()) return;

        sendMinecraftEvent(
                "death_backpack_recovered",
                name,
                DiscordLocalizationService.format("discord.totem.death_backpack.recovered", name)
        );
    }

    public static void sendSpaceUnitPublicUpdate(String actor, String message) {
        String text = normalizeText(message);
        if (text.isEmpty()) return;

        sendMinecraftEvent(
                "space_unit_public_update",
                normalizeActor(actor),
                DiscordLocalizationService.format("discord.totem.space_unit.public_update", text)
        );
    }

    public static void sendLockedContainerNetworkBroken(LockedContainerNetworkBrokenEvent event) {
        if (event == null) return;
        String actor = normalizeActor(event.actorName());
        String owner = normalizeActor(event.ownerName());
        String location = event.dimension() + " " + event.x() + " " + event.y() + " " + event.z();
        String message;
        if (event.lockRemoved()) {
            message = DiscordLocalizationService.format(
                    "discord.totem.locked_network.broken.last",
                    actor,
                    owner,
                    location
            );
        } else {
            message = DiscordLocalizationService.format(
                    "discord.totem.locked_network.broken.member",
                    actor,
                    owner,
                    localizedLockedMemberKind(event.brokenMemberKind()),
                    location,
                    String.valueOf(event.remainingLockedContainers()),
                    String.valueOf(event.detachedUnlockedContainers())
            );
        }
        sendMinecraftEvent("locked_container_network_broken", actor, message);
    }

    private static String localizedLockedMemberKind(String kind) {
        return switch (normalizeText(kind)) {
            case "chest" -> DiscordLocalizationService.translate("discord.totem.container.chest");
            case "trapped_chest" -> DiscordLocalizationService.translate("discord.totem.container.trapped_chest");
            case "barrel" -> DiscordLocalizationService.translate("discord.totem.container.barrel");
            case "hopper" -> DiscordLocalizationService.translate("discord.totem.container.hopper");
            default -> DiscordLocalizationService.translate("discord.totem.container.generic");
        };
    }

    public static void sendBossDefeated(String bossName, String killerName) {
        String boss = normalizeText(bossName);
        if (boss.isEmpty()) return;
        DiscordEventNotifications.bossDefeated(
                net.minecraft.network.chat.Component.literal(boss),
                killerName
        );
    }

    public static void sendRaidStarted(String playerName) {
        String name = normalizePlayerName(playerName);
        String message = name.isEmpty()
                ? DiscordLocalizationService.translate("discord.totem.raid.started")
                : DiscordLocalizationService.format("discord.totem.raid.started.by", name);
        sendMinecraftEvent(
                "raid_started",
                name.isEmpty() ? DiscordLocalizationService.translate("discord.totem.system") : name,
                message
        );
    }

    public static synchronized void sendRaidStarted(String raidKey, String playerName) {
        String key = normalizeText(raidKey);
        if (key.isEmpty() || !announcedRaidKeys.add(key)) {
            return;
        }
        sendRaidStarted(playerName);
    }

    public static void sendRaidEnded(String result) {
        DiscordEventNotifications.raidEnded(result);
    }

    public static synchronized void sendRaidEnded(String raidKey, String result) {
        String key = normalizeText(raidKey);
        if (!key.isEmpty()) {
            announcedRaidKeys.remove(key);
        }
        sendRaidEnded(result);
    }

    public static void sendDifficultyChanged(String actor, String difficulty) {
        if (normalizeText(difficulty).isEmpty()) return;
        DiscordEventNotifications.difficultyChanged(actor, difficulty);
    }

    public static void sendGameruleChanged(String actor, String rule, String value) {
        String normalizedRule = normalizeText(rule);
        String normalizedValue = normalizeText(value);
        if (normalizedRule.isEmpty() || normalizedValue.isEmpty()) return;

        String source = normalizeActor(actor);
        sendMinecraftEvent(
                "gamerule_changed",
                source,
                DiscordLocalizationService.format(
                        "discord.totem.gamerule.changed",
                        source,
                        normalizedRule,
                        normalizedValue
                )
        );
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String getWorkerUrl() {
        return workerUrl;
    }

    /** Returns an immutable credential snapshot for module-owned Worker clients. */
    static synchronized WorkerEndpoint workerEndpoint() {
        return new WorkerEndpoint(enabled, workerUrl, apiKey);
    }

    record WorkerEndpoint(boolean enabled, String baseUrl, String apiKey) {
        boolean available() {
            return enabled && baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }
    }

    private static JsonArray channelsToJsonArray() {
        JsonArray arr = new JsonArray();
        for (DiscordChannel ch : channels) {
            arr.add(ch.id);
        }
        return arr;
    }

    private static String normalizeWorkerUrl(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePlayerName(String playerName) {
        return playerName == null ? "" : playerName.trim();
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeChannelId(String channelId) {
        return channelId == null ? "" : channelId.trim();
    }

    private static String normalizeChannelName(String channelName, String fallback) {
        String normalized = normalizeText(channelName);
        if (normalized.isEmpty()) {
            return fallback;
        }
        return normalized.length() > 64 ? normalized.substring(0, 64).trim() : normalized;
    }

    private static void validateChannelId(String channelId) {
        if (channelId.isEmpty()) {
            throw new IllegalArgumentException("頻道 ID 不能為空");
        }
        if (!isValidChannelId(channelId)) {
            throw new IllegalArgumentException("頻道 ID 格式不正確，必須是 Discord snowflake");
        }
    }

    private static boolean isValidChannelId(String channelId) {
        if (channelId == null || channelId.length() < 17 || channelId.length() > 20) {
            return false;
        }
        for (int i = 0; i < channelId.length(); i++) {
            if (!Character.isDigit(channelId.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeActor(String actor) {
        String normalized = normalizeText(actor);
        return normalized.isEmpty() ? DiscordLocalizationService.translate("discord.totem.server") : normalized;
    }

    private static void recordDeliverySuccess() {
        consecutiveDeliveryFailures = 0;
        deliveryFailureAlertReported = false;
    }

    private static void recordDeliveryFailure(String event) {
        consecutiveDeliveryFailures++;
        if (deliveryFailureAlertReported || "server_health_alert".equals(event)) {
            return;
        }
        if (consecutiveDeliveryFailures >= DELIVERY_FAILURE_ALERT_THRESHOLD) {
            deliveryFailureAlertReported = true;
            LOGGER.warn("[DiscordTransportService] 連續 {} 次傳送失敗", consecutiveDeliveryFailures);
            sendServerHealthAlert(DiscordLocalizationService.format(
                    "discord.totem.health.delivery_failures",
                    String.valueOf(consecutiveDeliveryFailures)
            ));
        }
    }

    private static boolean responseHasDiscordFailures(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!root.has("data") || !root.get("data").isJsonObject()) {
                return false;
            }
            JsonObject data = root.getAsJsonObject("data");
            return data.has("failed") && data.get("failed").getAsInt() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void createDefaultConfig(Path configFile) {
        String defaultConfig = """
                {
                  "enabled": false,
                  "workerUrl": "",
                  "apiKey": "",
                  "channels": []
                }
                """;
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, defaultConfig, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[DiscordTransportService] 建立設定檔失敗", e);
        }
    }
}
