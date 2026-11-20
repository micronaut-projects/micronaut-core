import threading

import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
Optional = java.type("java.util.Optional")


@Requires(property="spec.name", value="ReportRoutesTest")
@Singleton
class Reports:
    """The reports of the documentation example: the test completes them."""

    def __init__(self):
        self._pending = {}
        self._outcomes = {}
        self._lock = threading.Lock()

    def generate(self, id: str):
        with self._lock:
            return self._pending.setdefault(id, CompletableFuture())

    def completed(self, id: str, failure) -> None:
        self.outcome(id).complete(Optional.ofNullable(failure))

    def outcome(self, id: str):
        with self._lock:
            return self._outcomes.setdefault(id, CompletableFuture())
