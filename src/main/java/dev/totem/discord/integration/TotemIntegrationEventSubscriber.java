package dev.totem.discord.integration;

import dev.totem.core.api.v1.event.AdminAuditEvent;
import dev.totem.core.api.v1.event.DeathBackpackCreatedEvent;
import dev.totem.core.api.v1.event.DeathBackpackRecoveredEvent;
import dev.totem.core.api.v1.event.SpaceUnitPublicUpdateEvent;
import dev.totem.core.api.v1.event.TotemEventBus;
import dev.totem.discord.transport.DiscordTransportService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Routes neutral Core events to Discord without linking feature modules. */
public final class TotemIntegrationEventSubscriber {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static AutoCloseable subscription;

    private TotemIntegrationEventSubscriber() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        subscription = subscribe(new DiscordEventSink() {
            @Override
            public void deathBackpackCreated(String playerName) {
                DiscordTransportService.sendDeathBackpackCreated(playerName);
            }

            @Override
            public void deathBackpackRecovered(String playerName) {
                DiscordTransportService.sendDeathBackpackRecovered(playerName);
            }

            @Override
            public void spaceUnitPublicUpdate(String actor, String message) {
                DiscordTransportService.sendSpaceUnitPublicUpdate(actor, message);
            }

            @Override
            public void adminAudit(String actor, String action, String targetSummary) {
                DiscordTransportService.sendAdminAction(actor, action, targetSummary);
            }
        });
    }

    static AutoCloseable subscribe(DiscordEventSink sink) {
        List<TotemEventBus.Subscription> subscriptions = new ArrayList<>();
        subscriptions.add(TotemEventBus.subscribe(
                DeathBackpackCreatedEvent.class,
                event -> sink.deathBackpackCreated(event.playerName())
        ));
        subscriptions.add(TotemEventBus.subscribe(
                DeathBackpackRecoveredEvent.class,
                event -> sink.deathBackpackRecovered(event.playerName())
        ));
        subscriptions.add(TotemEventBus.subscribe(
                SpaceUnitPublicUpdateEvent.class,
                event -> sink.spaceUnitPublicUpdate(event.actor(), event.message())
        ));
        subscriptions.add(TotemEventBus.subscribe(
                AdminAuditEvent.class,
                event -> sink.adminAudit(event.actor(), event.action(), event.targetSummary())
        ));
        return () -> {
            for (int index = subscriptions.size() - 1; index >= 0; index--) {
                subscriptions.get(index).close();
            }
        };
    }

    interface DiscordEventSink {
        void deathBackpackCreated(String playerName);
        void deathBackpackRecovered(String playerName);
        void spaceUnitPublicUpdate(String actor, String message);
        void adminAudit(String actor, String action, String targetSummary);
    }
}
