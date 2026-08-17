package dev.totem.discord.integration;

import dev.totem.core.api.v1.event.AdminAuditEvent;
import dev.totem.core.api.v1.event.DeathBackpackCreatedEvent;
import dev.totem.core.api.v1.event.DeathBackpackRecoveredEvent;
import dev.totem.core.api.v1.event.SpaceUnitPublicUpdateEvent;
import dev.totem.core.api.v1.event.TotemEventBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TotemIntegrationEventSubscriberTest {
    @Test
    void routesCoreEventsToDiscordSinkWithoutFeatureModuleClasses() throws Exception {
        List<String> delivered = new ArrayList<>();
        TotemIntegrationEventSubscriber.DiscordEventSink sink =
                new TotemIntegrationEventSubscriber.DiscordEventSink() {
                    @Override
                    public void deathBackpackCreated(String playerName) {
                        delivered.add("created:" + playerName);
                    }

                    @Override
                    public void deathBackpackRecovered(String playerName) {
                        delivered.add("recovered:" + playerName);
                    }

                    @Override
                    public void spaceUnitPublicUpdate(String actor, String message) {
                        delivered.add("space:" + actor + ":" + message);
                    }

                    @Override
                    public void adminAudit(String actor, String action, String targetSummary) {
                        delivered.add("admin:" + actor + ":" + action + ":" + targetSummary);
                    }
                };

        try (AutoCloseable ignored = TotemIntegrationEventSubscriber.subscribe(sink)) {
            TotemEventBus.publish(new DeathBackpackCreatedEvent(
                    "Alex",
                    3,
                    "minecraft:overworld",
                    1,
                    64,
                    2
            ));
            TotemEventBus.publish(new DeathBackpackRecoveredEvent("Alex"));
            TotemEventBus.publish(new SpaceUnitPublicUpdateEvent("Alex", "公開了磁石"));
            TotemEventBus.publish(new AdminAuditEvent("Admin", "disable", "node"));
        }

        assertEquals(List.of(
                "created:Alex",
                "recovered:Alex",
                "space:Alex:公開了磁石",
                "admin:Admin:disable:node"
        ), delivered);
    }
}
