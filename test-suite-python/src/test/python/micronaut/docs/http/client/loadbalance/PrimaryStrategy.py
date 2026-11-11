from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Named
from micronaut.context.annotation import Prototype
from micronaut.discovery import ServiceInstance
from micronaut.http.client.loadbalance import LoadBalancerStrategy
# end::imports[]


@Requires(property="spec.name", value="PrimaryStrategyTest")
# tag::class[]
@Prototype  # <1>
@Named("primary")  # <2>
class PrimaryStrategy(LoadBalancerStrategy):
    def select(self, available: list[ServiceInstance], discriminator: object | None) -> ServiceInstance:
        return available[0]  # <3>
# end::class[]
