from .TraceService import TraceService

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest, MutableHttpResponse
from micronaut.http.annotation import RequestFilter, ResponseFilter, ServerFilter
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn
# end::imports[]


@Requires(property="spec.filter", value="TraceFilterMethods")
# tag::clazz[]
@ServerFilter("/hello/**")  # <1>
class TraceFilter:
    def __init__(self, traceService: TraceService):  # <2>
        self.traceService = traceService

    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)  # <3>
    def filterRequest(self, request: HttpRequest) -> None:
        self.traceService.trace(request)  # <4>

    @ResponseFilter  # <5>
    def filterResponse(self, res: MutableHttpResponse) -> None:
        res.getHeaders().add("X-Trace-Enabled", "true")
# end::clazz[]
