package io.micronaut.docs.server.functional

import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * An in-memory repository of the documentation examples.
 */
@Singleton
class ItemRepository {

    private final Map<Long, Item> items = new ConcurrentHashMap<>()
    private final AtomicLong ids = new AtomicLong()

    Item find(long id) {
        Item item = items.get(id)
        if (item == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "No item " + id)
        }
        return item
    }

    Item save(Item item) {
        long id = item.id() == 0 ? ids.incrementAndGet() : item.id()
        Item saved = new Item(id, item.name())
        items.put(id, saved)
        return saved
    }

    CompletionStage<Item> saveAsync(Item item) {
        return CompletableFuture.completedFuture(save(item))
    }

    void delete(long id) {
        items.remove(id)
    }

    CompletionStage<Integer> countAsync() {
        return CompletableFuture.completedFuture(items.size())
    }

    void clear() {
        items.clear()
    }
}
