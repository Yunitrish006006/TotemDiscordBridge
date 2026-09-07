package dev.totem.discord.client;

import dev.totem.discord.network.DiscordConfigSyncPayload;
import dev.totem.discord.network.ManageDiscordChannelPayload;
import dev.totem.discord.network.RequestDiscordConfigPayload;
import dev.totem.discord.network.SaveDiscordConfigPayload;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Owns client registration for the future TotemDiscordBridge module.
 */
public final class TotemDiscordBridgeClientBootstrap {
    private static final int CONFIG_OPEN_TIMEOUT_TICKS = 100;
    private static KeyMapping openConfigKey;
    private static int pendingConfigOpenTicks = 0;

    private TotemDiscordBridgeClientBootstrap() {
    }

    public static KeyMapping createKeyMapping(KeyMapping.Category category) {
        openConfigKey = new KeyMapping(
                "key.totem.discord_config",
                GLFW.GLFW_KEY_UNKNOWN,
                category
        );
        return openConfigKey;
    }

    public static void registerRuntime() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pendingConfigOpenTicks > 0) {
                pendingConfigOpenTicks--;
            }
            while (openConfigKey.consumeClick()) {
                openDiscordConfigUi(mc);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(DiscordConfigSyncPayload.TYPE,
                (payload, context) -> applyServerConfig(context.client(), payload.enabled(), payload.workerUrl(), payload.apiKey(), payload.channels()));
    }

    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("discordbridgeui")
                        .executes(context -> {
                            Minecraft mc = context.getSource().getClient();
                            mc.execute(() -> openDiscordConfigUi(mc));
                            return 1;
                        })));
    }

    private static void applyServerConfig(
            Minecraft mc,
            boolean enabled,
            String workerUrl,
            String apiKey,
            java.util.List<DiscordConfigSyncPayload.ChannelData> channels
    ) {
        mc.execute(() -> {
            DiscordConfigScreen screen = DiscordConfigScreen.CURRENT;
            if (screen == null && pendingConfigOpenTicks > 0) {
                screen = new DiscordConfigScreen();
                mc.setScreenAndShow(screen);
            }
            pendingConfigOpenTicks = 0;
            if (screen != null) {
                screen.applyServerConfig(enabled, workerUrl, apiKey);
                screen.applyChannels(channels);
            }
        });
    }

    public static boolean sendConfigSave(boolean enabled, String workerUrl, String apiKey) {
        if (ClientPlayNetworking.canSend(SaveDiscordConfigPayload.TYPE)) {
            ClientPlayNetworking.send(new SaveDiscordConfigPayload(enabled, workerUrl, apiKey));
            return true;
        }
        return false;
    }

    public static boolean sendChannelManagement(String action, String channelId, String channelName) {
        if (ClientPlayNetworking.canSend(ManageDiscordChannelPayload.TYPE)) {
            ClientPlayNetworking.send(new ManageDiscordChannelPayload(action, channelId, channelName));
            return true;
        }
        return false;
    }

    private static void openDiscordConfigUi(Minecraft mc) {
        if (ClientPlayNetworking.canSend(RequestDiscordConfigPayload.TYPE)) {
            pendingConfigOpenTicks = CONFIG_OPEN_TIMEOUT_TICKS;
            ClientPlayNetworking.send(new RequestDiscordConfigPayload());
        } else if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("message.totem.discord_config.request_failed").withStyle(ChatFormatting.RED));
        }
    }
}
