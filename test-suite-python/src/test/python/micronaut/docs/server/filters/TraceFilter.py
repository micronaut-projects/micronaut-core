import java

from .TraceService import TraceService

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest, MutableHttpResponse
from micronaut.http.annotation import Filter
from micronaut.http.filter import HttpServerFilter, ServerFilterChain

Flux = java.type("reactor.core.publisher.Flux")
Publisher = java.type("org.reactivestreams.Publisher")
# end::imports[]


@Requires(property="spec.filter", value="TraceFilter")
# tag::clazz[]
@Filter("/hello/**")  # <1>
class TraceFilter(HttpServerFilter):  # <2>
    def __init__(self, traceService: TraceService):  # <3>
        self.traceService = traceService

    def doFilter(
        self,
        request: HttpRequest,
        chain: ServerFilterChain,
    ) -> Publisher:
        return getattr(Flux, "from")(self.traceService.trace(request)).switchMap(  # <4>
            lambda _: chain.proceed(request)  # <5>
        ).doOnNext(
            lambda res: res.getHeaders().add("X-Trace-Enabled", "true")  # <6>
        )
# end::clazz[]
