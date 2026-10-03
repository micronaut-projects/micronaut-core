from java.lang import IllegalStateException
from java.math import BigDecimal
from java.util import NoSuchElementException
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="NamedCircuitBreakerSpec")
@Singleton
class Warehouse:
    """A remote warehouse of the circuit breaker examples: it fails while it is unavailable."""

    def __init__(self):
        self.reset()

    def reset(self) -> None:
        self.calls = 0
        self.available = False

    def stock(self, sku: str) -> int:
        self._call()
        self._find(sku)
        return 10

    def reserve(self, sku: str) -> None:
        self._call()
        self._find(sku)

    def ship(self, order: str) -> str:
        self._call()
        return "tracking-" + self._find(order)

    def price(self, sku: str) -> BigDecimal:
        self._call()
        self._find(sku)
        return BigDecimal.TEN

    def _call(self) -> None:
        self.calls += 1
        if not self.available:
            raise IllegalStateException("Warehouse unavailable")

    def _find(self, id: str) -> str:
        if id == "unknown":
            raise NoSuchElementException("Unknown: " + id)
        return id
