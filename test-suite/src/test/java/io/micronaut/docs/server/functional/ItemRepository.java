package io.micronaut.docs.server.functional;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory repository of the documentation examples.
 */
@Singleton
public class ItemRepository {
    private final Map<Long, Item> items = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public Item find(long id) {
        Item item = items.get(id);
        if (item == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "No item " + id);
        }
        return item;
    }

    public Item save(Item item) {
        long id = item.id() == 0 ? ids.incrementAndGet() : item.id();
        Item saved = new Item(id, item.name());
        items.put(id, saved);
        return saved;
    }

    public CompletionStage<Item> saveAsync(Item item) {
        return CompletableFuture.completedFuture(save(item));
    }

    public void delete(long id) {
        items.remove(id);
    }

    public CompletionStage<Integer> countAsync() {
        return CompletableFuture.completedFuture(items.size());
    }

    public void clear() {
        items.clear();
    }
}
