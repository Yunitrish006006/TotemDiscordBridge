package dev.totem.discord.integration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedEventIdDeduplicatorTest {
    @Test
    void deduplicatesWithinTtlAndEvictsAtCapacity() {
        BoundedEventIdDeduplicator cache = new BoundedEventIdDeduplicator(2, 100L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        assertTrue(cache.accept(first, 0L));
        assertFalse(cache.accept(first, 1L));
        assertTrue(cache.accept(second, 2L));
        assertTrue(cache.accept(third, 3L));
        assertTrue(cache.accept(first, 4L));
        assertTrue(cache.accept(first, 200L));
    }
}
