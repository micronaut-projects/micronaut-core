from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from . import InventoryController
from .InventoryClient import InventoryClient

# the client of the service sends its requests to the embedded server of the test
@Property(name="spec.name", value="InventoryClientTest")
@Property(name="micronaut.server.port", value="${random.port}")
@Property(name="micronaut.retry.policies.inventory.delay", value="10ms")
@Property(name="micronaut.http.services.inventory.url", value="http://localhost:${micronaut.server.port}")
@Property(name="micronaut.http.services.inventory.retry.enabled", value="true")
@Property(name="micronaut.http.services.inventory.retry.policy", value="inventory")
@MicronautTest
class InventoryClientTest:
    client: Annotated[InventoryClient, Inject]

    @Test
    def theExchangeIsRetried(self) -> None:
        InventoryController.CALLS.set(0)
        assert self.client.stock("apples") == "42"
        assert InventoryController.CALLS.get() == 2
