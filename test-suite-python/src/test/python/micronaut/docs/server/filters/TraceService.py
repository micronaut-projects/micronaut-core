import java

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.http import HttpRequest

Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")
LoggerFactory = java.type("org.slf4j.LoggerFactory")
Schedulers = java.type("reactor.core.scheduler.Schedulers")
# end::imports[]


# tag::class[]
@Singleton
class TraceService:
    LOG = LoggerFactory.getLogger("micronaut.docs.server.filters.TraceService")

    def trace(self, request: HttpRequest) -> Publisher:
        def trace_request():  # <1>
            self.LOG.debug("Tracing request: {}", request.getUri())
            # trace logic here, potentially performing I/O <2>
            return True

        return Mono.fromCallable(trace_request).subscribeOn(
            Schedulers.boundedElastic()
        ).flux()  # <3>
# end::class[]
