from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.scheduling import TaskExecutors
from micronaut.web.router.builder import DirectRouteBuilder, HttpDirectRoutes

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
# end::imports[]

# tag::clazz[]
REPORTS = {"2026-q1": "Q1: 1200 orders"}
QUOTES = {"MNT": "42.00"}


# end::clazz[]
@Requires(property="spec.name", value="AsyncDirectRoutesTest")
# tag::clazz[]
@Singleton
class AsyncDirectRoutes(HttpDirectRoutes):
    def routes(self, routes: DirectRouteBuilder) -> None:
        def report(direct):
            text = self.load_report(direct.pathVariables().getString("id"))  # <1>
            return None if text is None else direct.responses().ok(text)

        routes.GET("/reports/{id}").executeOn(TaskExecutors.BLOCKING).respond(report)  # <2>
        routes.GET("/quotes/{symbol}").respondAsync(lambda direct:
                        self.fetch_quote(direct.pathVariables().getString("symbol"))  # <3>
                        .thenApply(lambda quote: None if quote is None else direct.responses().ok(quote)))  # <4>

    def load_report(self, report_id: str):
        """Blocks, e.g. on a database."""
        return REPORTS.get(report_id)

    def fetch_quote(self, symbol: str):
        """Completes later, e.g. with the response of a non-blocking client."""
        return CompletableFuture.supplyAsync(lambda: QUOTES.get(symbol))
# end::clazz[]
