from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.retry.annotation import CircuitBreaker

from .Warehouse import Warehouse


@Requires(property="spec.name", value="NamedCircuitBreakerSpec")
@Singleton
class InventoryService:
    def __init__(self, warehouse: Warehouse):
        self.warehouse = warehouse

    # tag::named[]
    @CircuitBreaker(name="inventory", attempts="2")  # <1>
    def stock(self, sku: str) -> int:
        return self.warehouse.stock(sku)

    @CircuitBreaker(name="inventory")  # <2>
    def reserve(self, sku: str) -> None:
        self.warehouse.reserve(sku)
    # end::named[]
