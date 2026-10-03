import threading

import java
from jakarta.inject import Singleton
from micronaut.http import HttpStatus
from micronaut.http.exceptions import HttpStatusException

from .Item import Item

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
CompletionStage = java.type("java.util.concurrent.CompletionStage")


@Singleton
class ItemRepository:
    """An in-memory repository of the documentation examples."""

    def __init__(self):
        self._items: dict[int, Item] = {}
        self._ids = 0
        self._lock = threading.Lock()

    def find(self, id: int) -> Item:
        item = self._items.get(id)
        if item is None:
            raise HttpStatusException(HttpStatus.NOT_FOUND, f"No item {id}")
        return item

    def save(self, item: Item) -> Item:
        with self._lock:
            if item.id == 0:
                self._ids += 1
                id = self._ids
            else:
                id = item.id
            saved = Item(id, item.name)
            self._items[id] = saved
            return saved

    def save_async(self, item: Item) -> CompletionStage:
        return CompletableFuture.completedFuture(self.save(item))

    def delete(self, id: int) -> None:
        self._items.pop(id, None)

    def count_async(self) -> CompletionStage:
        return CompletableFuture.completedFuture(len(self._items))

    def clear(self) -> None:
        self._items.clear()
