from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.http.context.event import HttpRequestTerminatedEvent
from micronaut.runtime.event.annotation import EventListener

ConcurrentHashMap = java.type("java.util.concurrent.ConcurrentHashMap")
LongAdder = java.type("java.util.concurrent.atomic.LongAdder")
# end::imports[]


@Requires(property="spec.name", value="RequestMetricsTest")
# tag::clazz[]
@Singleton
class RequestMetrics:
    def __init__(self) -> None:
        self.nanos_by_route = ConcurrentHashMap()

    @EventListener
    def on_terminated(self, event: HttpRequestTerminatedEvent) -> None:
        request_log = event.getRequestLog()  # <1>
        if not request_log.isPresent():
            return
        log = request_log.get()
        route = log.routeTemplate().orElse("UNMATCHED")  # <2>
        key = f"{route} {log.status().orElse(0)}" + ("" if log.isCompleted() else " incomplete")  # <3>
        total = log.totalDurationNanos()  # <4>
        if total.isPresent():
            self.nanos_by_route.computeIfAbsent(key, lambda k: LongAdder()).add(total.getAsLong())
# end::clazz[]
