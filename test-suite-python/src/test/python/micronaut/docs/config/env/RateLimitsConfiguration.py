# tag::clazz[]
from typing import Annotated

from micronaut.context.annotation import EachProperty
from micronaut.context.annotation import Parameter
from micronaut.core.order import Ordered

from java.time import Duration


@EachProperty(value="ratelimits", list=True)  # <1>
class RateLimitsConfiguration(Ordered):  # <2>
    period: Duration = None
    limit: int = None

    def __init__(self, index: Annotated[int, Parameter]):  # <3>
        self.index = index

    def getOrder(self) -> int:
        return self.index

    def get_period(self) -> Duration:
        return self.period

    def set_period(self, period: Duration) -> None:
        self.period = period

    def get_limit(self) -> int:
        return self.limit

    def set_limit(self, limit: int) -> None:
        self.limit = limit
# end::clazz[]
