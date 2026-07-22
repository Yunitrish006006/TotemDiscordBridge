package dev.totem.discord.domain;

/** Internal Discord Bridge delivery contract; it is deliberately not a TotemCore API. */
@FunctionalInterface
public interface DiscordEventTransport {
    void send(String event, String username, String message);
}
