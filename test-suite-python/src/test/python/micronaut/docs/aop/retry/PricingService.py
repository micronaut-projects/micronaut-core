from java.math import BigDecimal
from java.util import NoSuchElementException
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.retry.annotation import CircuitBreaker

from .Warehouse import Warehouse


@Requires(property="spec.name", value="NamedCircuitBreakerSpec")
@Singleton
class PricingService:
    def __init__(self, warehouse: Warehouse):
        self.warehouse = warehouse

    # tag::window[]
    @CircuitBreaker(name="pricing",
                    attempts="1",  # <1>
                    reset="30s",
                    requestVolumeThreshold="4",  # <2>
                    failureRatio="0.5",  # <3>
                    successThreshold="2",  # <4>
                    skipOn=[NoSuchElementException])  # <5>
    def price(self, sku: str) -> BigDecimal:
        return self.warehouse.price(sku)
    # end::window[]
