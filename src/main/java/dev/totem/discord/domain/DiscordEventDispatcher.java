package dev.totem.discord.domain;

import dev.totem.discord.transport.DiscordTransportService;

import java.util.Objects;
import java.util.function.Consumer;

public final class DiscordEventDispatcher {
    private static volatile Consumer<DiscordEventPayload> observerForTesting;
    private static volatile DiscordEventTransport transport = DiscordTransportService::sendMinecraftEvent;

    private DiscordEventDispatcher() {
    }

    public static void send(String event, String username, String message) {
        DiscordEventPayload payload = new DiscordEventPayload(event, username, message);
        if (payload.event().isEmpty() || payload.username().isEmpty() || payload.message().isEmpty()) {
            return;
        }

        Consumer<DiscordEventPayload> observer = observerForTesting;
        if (observer != null) {
            observer.accept(payload);
        }
        transport.send(payload.event(), payload.username(), payload.message());
    }

    public static void setTransport(DiscordEventTransport newTransport) {
        transport = Objects.requireNonNull(newTransport, "newTransport");
    }

    public static AutoCloseable observeForTesting(Consumer<DiscordEventPayload> observer) {
        Objects.requireNonNull(observer, "observer");
        if (observerForTesting != null) {
            throw new IllegalStateException("Discord event observer already installed");
        }
        observerForTesting = observer;
        return () -> observerForTesting = null;
    }
}
