from ..TraceService import TraceService

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest, MutableHttpResponse
from micronaut.http.annotation import RequestFilter, ServerFilter
from micronaut.http.filter import FilterContinuation
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn
# end::imports[]


@Requires(property="spec.filter", value="TraceFilterContinuation")
@ServerFilter("/hello/**")
class TraceFilter:
    def __init__(self, traceService: TraceService):  # <2>
        self.traceService = traceService

    # tag::doFilter[]
    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)  # <4>
    def filterRequest(self, request: HttpRequest, continuation: FilterContinuation) -> None:  # <1>
        self.traceService.trace(request)
        res = continuation.proceed()  # <2>
        res.getHeaders().add("X-Trace-Enabled", "true")  # <3>
    # end::doFilter[]
# tag::endclass[]
# end::endclass[]
