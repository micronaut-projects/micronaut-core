import java

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.http import HttpRequest

LoggerFactory = java.type("org.slf4j.LoggerFactory")
# end::imports[]


# tag::class[]
@Singleton
class TraceService:
    LOG = LoggerFactory.getLogger("micronaut.docs.server.filters.filtermethods.TraceService")

    def trace(self, request: HttpRequest) -> None:
        self.LOG.debug("Tracing request: {}", request.getUri())
        # trace logic here, potentially performing I/O <1>
# end::class[]
