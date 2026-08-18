package dev.totem.discord.integration;

import dev.totem.core.api.v1.event.AdminAuditEvent;
import dev.totem.core.api.v1.event.DeathBackpackCreatedEvent;
import dev.totem.core.api.v1.event.DeathBackpackRecoveredEvent;
import dev.totem.core.api.v1.event.LockedContainerNetworkBrokenEvent;
import dev.totem.core.api.v1.event.SpaceUnitPublicUpdateEvent;
import dev.totem.core.api.v1.event.TotemEventBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

                    @Override
                    public void lockedContainerNetworkBroken(LockedContainerNetworkBrokenEvent event) {
                        delivered.add("lock:" + event.actorName() + ":" + event.remainingLockedContainers()
                                + ":" + event.detachedUnlockedContainers());
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
            LockedContainerNetworkBrokenEvent broken = new LockedContainerNetworkBrokenEvent(
                    UUID.fromString("64c459dd-48d0-471e-9d70-f21bbb20d7cb"),
                    UUID.fromString("029dc2a8-1fc3-4c8c-954e-7f070ad2023e"),
                    UUID.fromString("9f3ab0bb-026d-4777-a578-dd6f4e50571c"),
                    "Alex",
                    UUID.fromString("1b0bf873-878c-4a60-8e75-376157249774"),
                    "Owner",
                    "hopper",
                    "minecraft:overworld",
                    1, 64, 2,
                    2, 3, false, false, 1L
            );
            TotemEventBus.publish(broken);
            TotemEventBus.publish(broken);
        }

        assertEquals(List.of(
                "created:Alex",
                "recovered:Alex",
                "space:Alex:公開了磁石",
                "admin:Admin:disable:node",
                "lock:Alex:2:3"
        ), delivered);
    }
}
