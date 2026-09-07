package dev.totem.discord.network;

import dev.totem.discord.transport.DiscordTransportService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public final class DiscordPayloadRegistration {
    private static final Logger LOGGER = LoggerFactory.getLogger("TotemDiscordBridge");
    private DiscordPayloadRegistration() {
    }

    public static void registerServerboundTypes() {
        PayloadTypeRegistry.serverboundPlay().register(
                RequestDiscordConfigPayload.TYPE, RequestDiscordConfigPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SaveDiscordConfigPayload.TYPE, SaveDiscordConfigPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                ManageDiscordChannelPayload.TYPE, ManageDiscordChannelPayload.CODEC);
    }

    public static void registerClientboundTypes() {
        PayloadTypeRegistry.clientboundPlay().register(
                DiscordConfigSyncPayload.TYPE, DiscordConfigSyncPayload.CODEC);
    }

    public static void registerReceivers() {
        // 收到客戶端請求時，回傳目前設定
        ServerPlayNetworking.registerGlobalReceiver(RequestDiscordConfigPayload.TYPE,
                (payload, context) -> handleConfigRequest(context.player()));

        // 收到客戶端儲存請求時，更新設定（需要 OP 權限）
        ServerPlayNetworking.registerGlobalReceiver(SaveDiscordConfigPayload.TYPE,
                (payload, context) -> handleConfigSave(
                        context.player(), payload.enabled(), payload.workerUrl(), payload.apiKey()));

        // 收到頻道管理請求時，添加或移除頻道
        ServerPlayNetworking.registerGlobalReceiver(ManageDiscordChannelPayload.TYPE,
                (payload, context) -> handleChannelManagement(
                        context.player(), payload.action(), payload.channelId(), payload.channelName()));
    }

    private static void handleConfigRequest(ServerPlayer player) {
        if (!PayloadPermissionChecks.canManageServerConfiguration(player)) {
            player.sendSystemMessage(Component.translatable("message.totem.discord_config.permission_view").withStyle(ChatFormatting.RED));
            LOGGER.warn("[DiscordBridge] 玩家 {} 嘗試未授權讀取設定", player.getName().getString());
            return;
        }
        sendDiscordConfigTo(player);
    }

    private static void handleConfigSave(
            ServerPlayer player,
            boolean enabled,
            String workerUrl,
            String apiKey
    ) {
        if (!PayloadPermissionChecks.canManageServerConfiguration(player)) {
            player.sendSystemMessage(Component.translatable("message.totem.discord_config.permission_modify").withStyle(ChatFormatting.RED));
            LOGGER.warn("[DiscordBridge] 玩家 {} 嘗試未授權修改設定", player.getName().getString());
            return;
        }
        try {
            DiscordTransportService.updateConfig(enabled, workerUrl, apiKey);
            player.sendSystemMessage(Component.translatable("message.totem.discord_config.settings_updated").withStyle(ChatFormatting.GREEN));
            sendDiscordConfigTo(player);
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
        } catch (Exception exception) {
            player.sendSystemMessage(Component.translatable("message.totem.discord_config.update_failed", exception.getMessage()).withStyle(ChatFormatting.RED));
            LOGGER.error("[DiscordBridge] 更新設定失敗", exception);
        }
    }

    private static void handleChannelManagement(
            ServerPlayer player,
            String action,
            String channelId,
            String channelName
    ) {
        if (!PayloadPermissionChecks.canManageServerConfiguration(player)) {
            player.sendSystemMessage(Component.translatable("message.totem.discord_config.permission_channels").withStyle(ChatFormatting.RED));
            LOGGER.warn("[DiscordBridge] 玩家 {} 嘗試未授權管理頻道", player.getName().getString());
            return;
        }
        try {
            if ("add".equals(action)) {
                DiscordTransportService.addChannel(channelId, channelName);
                player.sendSystemMessage(Component.translatable("message.totem.discord_config.channel_added", channelName).withStyle(ChatFormatting.GREEN));
            } else if ("remove".equals(action)) {
                DiscordTransportService.removeChannel(channelId);
                player.sendSystemMessage(Component.translatable("message.totem.discord_config.channel_removed", channelId).withStyle(ChatFormatting.GREEN));
            } else {
                throw new IllegalArgumentException("Unsupported channel operation");
            }
            sendDiscordConfigTo(player);
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
        } catch (Exception exception) {
            player.sendSystemMessage(Component.translatable("message.totem.discord_config.operation_failed", exception.getMessage()).withStyle(ChatFormatting.RED));
            LOGGER.error("[DiscordBridge] 管理頻道失敗", exception);
        }
    }

    private static void sendDiscordConfigTo(ServerPlayer player) {
        var channels = DiscordTransportService.getChannels();
        var syncedChannels = new ArrayList<DiscordConfigSyncPayload.ChannelData>(channels.size());
        for (var channel : channels) {
            syncedChannels.add(new DiscordConfigSyncPayload.ChannelData(channel.id, channel.name));
        }
        if (ServerPlayNetworking.canSend(player, DiscordConfigSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new DiscordConfigSyncPayload(
                    DiscordTransportService.isEnabled(), DiscordTransportService.getWorkerUrl(), "", syncedChannels));
        }
    }
}

