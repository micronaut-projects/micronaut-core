from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import LoadBalancerResolver
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Mono = java.type("reactor.core.publisher.Mono")


@Property(name="spec.name", value="PrimaryStrategyTest")
@Property(name="micronaut.http.services.foo.urls", value="http://foo1,http://foo2")
@Property(name="micronaut.http.services.foo.load-balancer-strategy", value="primary")
@MicronautTest
class PrimaryStrategyTest:
    resolver: Annotated[LoadBalancerResolver, Inject]

    @Test
    def theNamedStrategyBeanPicksTheInstance(self) -> None:
        load_balancer = self.resolver.resolve("foo").orElseThrow()
        for _ in range(3):
            assert Mono.from_(load_balancer.select()).block().getURI().getHost() == "foo1"
