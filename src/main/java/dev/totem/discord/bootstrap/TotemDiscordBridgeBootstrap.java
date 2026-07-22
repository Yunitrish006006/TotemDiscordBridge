package dev.totem.discord.bootstrap;

import dev.totem.discord.transport.DiscordTransportService;
import dev.totem.discord.domain.DiscordLocalizationService;
import dev.totem.discord.domain.DiscordEventNotifications;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.stats.Stats;

import java.util.Locale;

import java.nio.file.Path;

/**
 * Owns registration for the future TotemDiscordBridge module.
 */
public final class TotemDiscordBridgeBootstrap {
    private static final int HEALTH_SAMPLE_INTERVAL_TICKS = 20 * 10;
    private static final int LOW_TPS_REQUIRED_SAMPLES = 3;
    private static final double LOW_TPS_THRESHOLD = 15.0D;
    private static final double RECOVERED_TPS_THRESHOLD = 18.0D;
    private static MinecraftServer statusOpenServer;
    private static long healthTickStartNanos;
    private static int healthSampleTicker;
    private static int lowTpsSamples;
    private static double averageTickMillis = 50.0D;
    private static boolean lowTpsAlertActive;
    private TotemDiscordBridgeBootstrap() {
    }

    public static void register(Path configDir) {
        DiscordLocalizationService.registerReloadListener();
        DiscordTransportService.init(configDir);
    }

    /** Registers Discord-owned runtime hooks without involving the compatibility root initializer. */
    public static void registerRuntime() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                DiscordTransportService.sendChatMessage(sender.getName().getString(), message.decoratedContent().getString()));
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            ServerPlayer player = listener.getPlayer();
            if (player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) <= 0) {
                DiscordTransportService.sendPlayerFirstJoined(player.getName().getString());
            } else {
                DiscordTransportService.sendPlayerJoined(player.getName().getString());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                DiscordTransportService.sendPlayerLeft(listener.getPlayer().getName().getString()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (server.isDedicatedServer()) sendServerStatus(server, "伺服器已開啟", 20.0D, false);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> sendServerStatus(server, "伺服器已關閉", 0.0D, true));
        ServerTickEvents.START_SERVER_TICK.register(server -> healthTickStartNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(TotemDiscordBridgeBootstrap::sampleServerHealth);
    }

    /** Optional adapter invoked by the compatibility bundle's shared death hook. */
    public static void onEntityDeath(Entity entity, Entity damageSourceEntity) {
        if (entity instanceof ServerPlayer player) {
            DiscordEventNotifications.death(player.getCombatTracker().getDeathMessage());
        } else if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
            String killer = damageSourceEntity instanceof ServerPlayer player ? player.getName().getString() : "";
            DiscordEventNotifications.bossDefeated(entity.getDisplayName(), killer);
        }
    }

    /** Optional adapter for integrated-server publish lifecycle notifications. */
    public static void onServerPublished(MinecraftServer server) {
        if (server != null) {
            sendServerStatus(server, "伺服器已開啟", 20.0D, false);
        }
    }

    /** Optional adapter for integrated-server unpublish lifecycle notifications. */
    public static void onServerUnpublished(MinecraftServer server) {
        if (server != null) {
            sendServerStatus(server, "伺服器已關閉", 0.0D, true);
        }
    }

    /** Adds the complete Discord command tree to the compatibility bundle dispatcher. */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("discordbridge").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN));
        root.then(Commands.literal("reload").executes(context -> {
                    DiscordTransportService.reload();
                    context.getSource().sendSuccess(() -> Component.translatable("message.deadrecall.discord_config.reloaded").withStyle(ChatFormatting.GREEN), true);
                    return 1;
                }));
        root.then(Commands.literal("set").then(Commands.argument("enabled", BoolArgumentType.bool())
                        .then(Commands.argument("workerUrl", StringArgumentType.string())
                                .then(Commands.argument("apiKey", StringArgumentType.string()).executes(context -> updateConfig(context))))));
        var channel = Commands.literal("channel");
        channel.then(Commands.literal("add").then(Commands.argument("channelId", StringArgumentType.string())
                .then(Commands.argument("channelName", StringArgumentType.string()).executes(context -> addChannel(context)))));
        channel.then(Commands.literal("remove").then(Commands.argument("channelId", StringArgumentType.string()).executes(context -> removeChannel(context))));
        channel.then(Commands.literal("list").executes(context -> listChannels(context)));
        root.then(channel);
        dispatcher.register(root);
    }

    private static int updateConfig(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        try {
            DiscordTransportService.updateConfig(BoolArgumentType.getBool(context, "enabled"), StringArgumentType.getString(context, "workerUrl"), StringArgumentType.getString(context, "apiKey"));
            context.getSource().sendSuccess(() -> Component.translatable("message.deadrecall.discord_config.settings_updated").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception exception) { return commandFailure(context, "message.deadrecall.discord_config.update_failed", exception); }
    }

    private static int addChannel(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "channelName");
            DiscordTransportService.addChannel(StringArgumentType.getString(context, "channelId"), name);
            context.getSource().sendSuccess(() -> Component.translatable("message.deadrecall.discord_config.channel_added", name).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception exception) { return commandFailure(context, "message.deadrecall.discord_config.channel_add_failed", exception); }
    }

    private static int removeChannel(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "channelId");
        try { DiscordTransportService.removeChannel(id); context.getSource().sendSuccess(() -> Component.translatable("message.deadrecall.discord_config.channel_removed", id).withStyle(ChatFormatting.GREEN), true); return 1;
        } catch (Exception exception) { return commandFailure(context, "message.deadrecall.discord_config.channel_remove_failed", exception); }
    }

    private static int listChannels(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        var channels = DiscordTransportService.getChannels();
        if (channels.isEmpty()) context.getSource().sendSuccess(() -> Component.translatable("message.deadrecall.discord_config.no_channels_configured").withStyle(ChatFormatting.RED), true);
        else for (var channel : channels) context.getSource().sendSuccess(() -> Component.literal("  - " + channel.name + " (" + channel.id + ")"), false);
        return 1;
    }

    private static int commandFailure(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String key, Exception exception) {
        context.getSource().sendFailure(Component.translatable(key, exception.getMessage()).withStyle(ChatFormatting.RED));
        return 0;
    }

    private static void sendServerStatus(MinecraftServer server, String status, double tps, boolean immediate) {
        if (server == null || ("伺服器已開啟".equals(status) && statusOpenServer == server)
                || ("伺服器已關閉".equals(status) && statusOpenServer != server)) return;
        statusOpenServer = "伺服器已開啟".equals(status) ? server : null;
        if (immediate) DiscordTransportService.sendServerStatusImmediately(status, server.getPlayerList().getPlayerCount(), server.getPlayerList().getMaxPlayers(), server.getServerVersion(), tps);
        else DiscordTransportService.sendServerStatus(status, server.getPlayerList().getPlayerCount(), server.getPlayerList().getMaxPlayers(), server.getServerVersion(), tps);
    }

    private static void sampleServerHealth(MinecraftServer server) {
        if (healthTickStartNanos <= 0L || ++healthSampleTicker < HEALTH_SAMPLE_INTERVAL_TICKS) return;
        averageTickMillis = (averageTickMillis * .95D) + ((System.nanoTime() - healthTickStartNanos) / 1_000_000.0D * .05D);
        healthSampleTicker = 0;
        double tps = Math.min(20.0D, 1000.0D / Math.max(1.0D, averageTickMillis));
        if (tps < LOW_TPS_THRESHOLD && ++lowTpsSamples >= LOW_TPS_REQUIRED_SAMPLES && !lowTpsAlertActive) {
            lowTpsAlertActive = true;
            DiscordTransportService.sendServerHealthAlert(String.format(Locale.ROOT, "TPS 持續偏低：%.1f TPS", tps));
        } else if (tps >= RECOVERED_TPS_THRESHOLD && lowTpsAlertActive) {
            lowTpsSamples = 0;
            lowTpsAlertActive = false;
            DiscordTransportService.sendServerHealthAlert(String.format(Locale.ROOT, "TPS 已恢復：%.1f TPS", tps));
        }
    }
}


