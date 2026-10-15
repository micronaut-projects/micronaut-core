from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.http import HttpResponse, HttpStatus
from micronaut.scheduling import TaskExecutors
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

Flux = java.type("reactor.core.publisher.Flux")
String = java.type("java.lang.String")
# end::imports[]


@Requires(property="spec.name", value="WebSocketRoutesTest")
@Singleton
class WebSocketRoutes(HttpRoutes):

    def routes(self, routes: HttpRouteBuilder) -> None:
        # tag::echo[]
        routes.GET("/echo/{name}").webSocket(lambda ws: ws  # <1>
            .onOpen(lambda session, request:  # <2>
                    session.sendAsync("Hello " + session.getUriVariables().get("name", String).orElseThrow()))
            .onMessage(String, lambda session, message: session.sendAsync("echo " + message))  # <3>
            .onError(lambda session, error: session.sendAsync("error " + error.getMessage()))  # <4>
            .onClose(lambda session, reason: None))  # <5>
        # end::echo[]

        # tag::filter[]
        (routes.GET("/private")
            .beforeReplacing(lambda request:  # <1>
                             None if request.getHeaders().contains("X-Token")
                             else HttpResponse.status(HttpStatus.FORBIDDEN))
            .and_()
            .webSocket(lambda ws: ws
                .onOpen(lambda session, request: session.sendAsync("welcome"))))
        # end::filter[]

        # tag::streams[]
        routes.GET("/ticks").webSocket(lambda ws: ws
            .onOpen(lambda session, request: session.sendAllAsync(Flux.range(1, 3).map(lambda i: f"tick {i}"))))  # <1>
        routes.GET("/upper").webSocket(lambda ws: ws
            .onMessageStream(String, lambda session, messages:  # <2>
                        session.sendAllAsync(Flux.from_(messages).map(lambda message: message.upper()))))  # <3>
        # end::streams[]

        # tag::delivery[]
        (routes.GET("/jobs")
            .executeOn(TaskExecutors.BLOCKING)  # <1>
            .webSocket(lambda ws: ws
                .maxConcurrentMessages(4)  # <2>
                .maxPendingMessages(32)  # <3>
                .onMessage(String, lambda session, job: session.sendAsync("done " + job))))
        # end::delivery[]
