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
open class ItemRepository {

    private val items = ConcurrentHashMap<Long, Item>()
    private val ids = AtomicLong()

    open fun find(id: Long): Item =
        items[id] ?: throw HttpStatusException(HttpStatus.NOT_FOUND, "No item $id")

    open fun save(item: Item): Item {
        val id = if (item.id == 0L) ids.incrementAndGet() else item.id
        val saved = Item(id, item.name)
        items[id] = saved
        return saved
    }

    open fun saveAsync(item: Item): CompletionStage<Item> = CompletableFuture.completedFuture(save(item))

    open fun delete(id: Long) {
        items.remove(id)
    }

    open fun countAsync(): CompletionStage<Int> = CompletableFuture.completedFuture(items.size)

    open fun clear() {
        items.clear()
    }
}
