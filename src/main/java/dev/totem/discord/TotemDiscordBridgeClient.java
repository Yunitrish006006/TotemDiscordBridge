package dev.totem.discord;

import dev.totem.discord.client.TotemDiscordBridgeClientBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class TotemDiscordBridgeClient implements ClientModInitializer {
    private static KeyMapping openConfigKey;

    public static KeyMapping openConfigKey() {
        return openConfigKey;
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("deadrecall", "category"));
        openConfigKey = TotemDiscordBridgeClientBootstrap.createKeyMapping(category);
        TotemDiscordBridgeClientBootstrap.registerRuntime();
        TotemDiscordBridgeClientBootstrap.registerCommands();
    }
}
