from typing import Annotated

from jakarta.inject import Inject
from java.lang import IllegalStateException
from java.math import BigDecimal
from java.util import NoSuchElementException
from org.junit.jupiter.api import Test

from micronaut.context.annotation import Property
from micronaut.retry import CircuitBreakerRegistry, CircuitState
from micronaut.retry.exception import CircuitOpenException
from micronaut.test.extensions.junit5.annotation import MicronautTest

from .InventoryClient import InventoryClient
from .InventoryService import InventoryService
from .PricingService import PricingService
from .Warehouse import Warehouse


def raises(exception_type, call) -> None:
    try:
        call()
    except exception_type:
        return
    assert False, f"{exception_type} expected"


# the configuration of the guide
@MicronautTest
@Property(name="spec.name", value="NamedCircuitBreakerSpec")
@Property(name="micronaut.retry.circuit-breakers.inventory.reset", value="30s")
@Property(name="micronaut.retry.circuit-breakers.inventory.attempts", value="1")
@Property(name="micronaut.retry.circuit-breakers.shipping.reset", value="30s")
@Property(name="micronaut.retry.circuit-breakers.shipping.request-volume-threshold", value="4")
@Property(name="micronaut.retry.circuit-breakers.shipping.failure-ratio", value="0.5")
@Property(name="micronaut.retry.circuit-breakers.shipping.success-threshold", value="2")
@Property(name="micronaut.retry.circuit-breakers.shipping.skip-on", value="java.util.NoSuchElementException")
class NamedCircuitBreakerSpec:
    inventory_service: Annotated[InventoryService, Inject] = None
    inventory_client: Annotated[InventoryClient, Inject] = None
    pricing_service: Annotated[PricingService, Inject] = None
    warehouse: Annotated[Warehouse, Inject] = None
    registry: Annotated[CircuitBreakerRegistry, Inject] = None

    @Test
    def test_methods_of_one_name_share_the_circuit(self) -> None:
        service = self.inventory_service
        warehouse = self.warehouse
        warehouse.reset()

        # the attempt and its two retries fail and open the circuit
        raises(IllegalStateException, lambda: service.stock("apple"))
        assert warehouse.calls == 3
        assert self.registry.findState("inventory").orElseThrow() == CircuitState.OPEN

        # the other method and the registry fail fast, without calling the warehouse
        warehouse.available = True
        try:
            service.reserve("apple")
        except IllegalStateException as e:
            assert e.getMessage() == "Warehouse unavailable"
        else:
            assert False, "IllegalStateException expected"
        raises(IllegalStateException, lambda: self.inventory_client.stock("apple"))
        assert warehouse.calls == 3

    @Test
    def test_guard_reports_outcomes_to_a_rolling_window(self) -> None:
        client = self.inventory_client
        warehouse = self.warehouse
        warehouse.reset()

        # two failures: the window of four calls is not full
        raises(IllegalStateException, lambda: client.ship("order-1"))
        raises(IllegalStateException, lambda: client.ship("order-1"))
        assert self.registry.findState("shipping").orElseThrow() == CircuitState.CLOSED

        # a skipOn exception counts as a success
        warehouse.available = True
        raises(NoSuchElementException, lambda: client.ship("unknown"))
        snapshot = self.registry.findSnapshot("shipping").orElseThrow()
        assert snapshot.calls() == 3
        assert snapshot.failures() == 2
        assert snapshot.state() == CircuitState.CLOSED

        # the fourth call fills the window: two failures of four reach the ratio 0.5
        assert client.ship("order-1") == "tracking-order-1"
        assert self.registry.findState("shipping").orElseThrow() == CircuitState.OPEN

        # the guard fails fast
        raises(CircuitOpenException, lambda: client.ship("order-1"))
        assert warehouse.calls == 4

    @Test
    def test_rolling_window_on_the_annotation(self) -> None:
        service = self.pricing_service
        warehouse = self.warehouse
        warehouse.reset()

        # two calls that fail after their retry: the window of four calls is not full
        raises(IllegalStateException, lambda: service.price("apple"))
        raises(IllegalStateException, lambda: service.price("apple"))
        assert warehouse.calls == 4
        window = self.registry.findSnapshot("pricing").orElseThrow()
        assert window.state() == CircuitState.CLOSED
        assert window.calls() == 2
        assert window.failures() == 2

        # a skipOn exception counts as a success
        warehouse.available = True
        raises(NoSuchElementException, lambda: service.price("unknown"))
        assert self.registry.findState("pricing").orElseThrow() == CircuitState.CLOSED

        # the fourth call fills the window: two failures of four reach the ratio 0.5
        assert service.price("apple") == BigDecimal.TEN
        snapshot = self.registry.findSnapshot("pricing").orElseThrow()
        assert snapshot.state() == CircuitState.OPEN
        assert snapshot.requestVolumeThreshold() == 4
        assert snapshot.openedCount() == 1

        # the open circuit fails fast
        calls = warehouse.calls
        raises(IllegalStateException, lambda: service.price("apple"))
        assert warehouse.calls == calls
