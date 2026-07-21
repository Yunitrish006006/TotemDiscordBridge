package dev.totem.discord;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Standalone module entrypoint; DeadRecall compatibility wiring is added during cutover. */
public final class TotemDiscordBridge implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("TotemDiscordBridge");
    @Override public void onInitialize() { LOGGER.info("TotemDiscordBridge initialized"); }
}
