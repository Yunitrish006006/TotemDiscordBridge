package com.adaptor.deadrecall.bootstrap;

import java.nio.file.Path;

/**
 * Composes the server-side feature bootstraps in the legacy registration order.
 */
public final class DeadRecallServerBootstrap {
    private DeadRecallServerBootstrap() {
    }

    public static void register(Path configDir) {
        LegacyGameplayBootstrap.registerContent();
        TotemAutomataBootstrap.register();
        TotemNexusBootstrap.register();
        LegacyGameplayBootstrap.registerRecipes();
        TotemDiscordBridgeBootstrap.register(configDir);
    }
}
