# tag::clazz[]
from micronaut.core.annotation import Order
from micronaut.context.annotation import Factory
from jakarta.inject import Singleton
from java.time import Duration
from .LowRateLimit import LowRateLimit
from .HighRateLimit import HighRateLimit

@Factory
class RateLimitsFactory:
    @Singleton
    @Order(20)
    def rate_limit2(self) -> LowRateLimit:
        return LowRateLimit(Duration.ofMinutes(50), 100)

    @Singleton
    @Order(10)
    def rate_limit1(self) -> HighRateLimit:
        return HighRateLimit(Duration.ofMinutes(50), 1000)

# end::clazz[]
