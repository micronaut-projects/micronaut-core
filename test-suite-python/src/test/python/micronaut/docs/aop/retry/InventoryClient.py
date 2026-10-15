from java.lang import RuntimeException
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.retry import CircuitBreakerRegistry

from .Warehouse import Warehouse


@Requires(property="spec.name", value="NamedCircuitBreakerSpec")
@Singleton
class InventoryClient:
    # tag::registry[]
    def __init__(self, registry: CircuitBreakerRegistry, warehouse: Warehouse):
        self.warehouse = warehouse
        self.inventory = registry.circuitBreaker("inventory")  # <1>
        self.shipping = registry.guard("shipping")  # <2>

    def stock(self, sku: str) -> int:
        return self.inventory.execute(lambda: self.warehouse.stock(sku))  # <3>
    # end::registry[]

    # tag::guard[]
    def ship(self, order: str) -> str:
        permit = self.shipping.acquire()  # <1>
        try:
            tracking = self.warehouse.ship(order)
            permit.onSuccess()  # <2>
            return tracking
        except RuntimeException as e:
            permit.onFailure(e)  # <3>
            raise
    # end::guard[]
