import threading

from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.http.gateway import GatewayObserver


@Requires(property="spec.name", value="GatewayDocsTest")
# tag::clazz[]
@Singleton
class RouteMetrics(GatewayObserver):

    def __init__(self):
        self.counts: dict[str, int] = {}
        self.lock = threading.Lock()

    def exchanged(self, exchange) -> None:
        # e.g. a Micrometer timer tagged with the route id, the status and the outcome
        key = f"{exchange.routeId()} {exchange.outcome()} {exchange.status()}"
        with self.lock:
            self.counts[key] = self.counts.get(key, 0) + 1

    def count(self, route_id: str, outcome, status: int) -> int:
        with self.lock:
            return self.counts.get(f"{route_id} {outcome} {status}", 0)
# end::clazz[]
