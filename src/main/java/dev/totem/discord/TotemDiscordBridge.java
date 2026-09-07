package dev.totem.discord;

import dev.totem.discord.bootstrap.TotemDiscordBridgeBootstrap;
import dev.totem.discord.integration.TotemIntegrationEventSubscriber;
import dev.totem.discord.network.DiscordPayloadRegistration;
import dev.totem.discord.transport.DiscordTransportService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Standalone module entrypoint. */
public final class TotemDiscordBridge implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("TotemDiscordBridge");
    @Override
    public void onInitialize() {
        TotemDiscordBridgeBootstrap.register(FabricLoader.getInstance().getConfigDir());
        TotemIntegrationEventSubscriber.register();
        TotemDiscordBridgeBootstrap.registerRuntime();
        DiscordPayloadRegistration.registerServerboundTypes();
        DiscordPayloadRegistration.registerClientboundTypes();
        DiscordPayloadRegistration.registerReceivers();
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) ->
                TotemDiscordBridgeBootstrap.onEntityDeath(entity, damageSource.getEntity()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TotemDiscordBridgeBootstrap.registerCommands(dispatcher));
        LOGGER.info("TotemDiscordBridge initialized");
    }
}
