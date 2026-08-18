package dev.totem.discord.integration;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Small synchronized TTL/LRU cache used only to suppress duplicate integration callbacks. */
final class BoundedEventIdDeduplicator {
    private final int capacity;
    private final long ttlMillis;
    private final LinkedHashMap<UUID, Long> acceptedAt = new LinkedHashMap<>(16, 0.75F, true);

    BoundedEventIdDeduplicator(int capacity, long ttlMillis) {
        if (capacity < 1 || ttlMillis < 1L) throw new IllegalArgumentException("positive bounds required");
        this.capacity = capacity;
        this.ttlMillis = ttlMillis;
    }

    synchronized boolean accept(UUID eventId, long nowMillis) {
        Objects.requireNonNull(eventId, "eventId");
        prune(nowMillis);
        Long previous = acceptedAt.get(eventId);
        if (previous != null && nowMillis - previous < ttlMillis) return false;
        acceptedAt.put(eventId, nowMillis);
        while (acceptedAt.size() > capacity) {
            Iterator<Map.Entry<UUID, Long>> iterator = acceptedAt.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    private void prune(long nowMillis) {
        Iterator<Map.Entry<UUID, Long>> iterator = acceptedAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (nowMillis - entry.getValue() >= ttlMillis) iterator.remove();
        }
    }
}
