from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.discovery import DiscoveryClient
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .OrdersClient import OrdersClient

Mono = java.type("reactor.core.publisher.Mono")


@Property(name="spec.name", value="DnsDiscoveryTest")
@Property(name="micronaut.discovery.dns.services.orders.hostname", value="orders.shop.svc.cluster.local")
@Property(name="micronaut.discovery.dns.services.orders.port", value="8080")
@MicronautTest
class DnsDiscoveryTest:
    client: Annotated[OrdersClient, Inject]
    discovery_client: Annotated[DiscoveryClient, Inject]

    @Test
    def theClientUsesTheDnsDiscoveryOfTheService(self) -> None:
        assert self.client is not None
        assert "orders" in Mono.from_(self.discovery_client.getServiceIds()).block()
