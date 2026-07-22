package dev.totem.discord;

import dev.totem.discord.bootstrap.TotemDiscordBridgeBootstrap;
import dev.totem.discord.network.DiscordPayloadRegistration;
import dev.totem.discord.transport.DiscordTransportService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;

/** Standalone module entrypoint; DeadRecall compatibility wiring is added during cutover. */
public final class TotemDiscordBridge implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("TotemDiscordBridge");
    @Override
    public void onInitialize() {
        TotemDiscordBridgeBootstrap.register(FabricLoader.getInstance().getConfigDir());
        installLegacyTransportFacade();
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

    private static void installLegacyTransportFacade() {
        try {
            Class<?> facade = Class.forName("com.adaptor.deadrecall.core.api.DiscordEventTransport");
            Object adapter = Proxy.newProxyInstance(
                    TotemDiscordBridge.class.getClassLoader(),
                    new Class<?>[] {facade},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("send") && arguments != null && arguments.length == 3) {
                            DiscordTransportService.sendMinecraftEvent(
                                    String.valueOf(arguments[0]),
                                    String.valueOf(arguments[1]),
                                    String.valueOf(arguments[2]));
                        }
                        return null;
                    });
            facade.getMethod("register", facade).invoke(null, adapter);
        } catch (ClassNotFoundException ignored) {
            // Standalone Discord installation has no compatibility facade.
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Unable to install DeadRecall Discord transport facade", exception);
        }
    }
}
