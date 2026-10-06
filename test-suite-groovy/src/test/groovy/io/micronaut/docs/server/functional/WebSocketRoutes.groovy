package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
// end::imports[]

@Requires(property = "spec.name", value = "WebSocketRoutesSpec")
@Singleton
class WebSocketRoutes implements HttpRoutes {

    @Override
    void routes(HttpRouteBuilder routes) {
        // tag::echo[]
        routes.GET("/echo/{name}").webSocket { ws -> ws // <1>
            .onOpen { session, request -> // <2>
                session.sendAsync("Hello " + session.uriVariables.get("name", String).orElseThrow())
            }
            .onMessage(String) { session, message -> session.sendAsync("echo " + message) } // <3>
            .onError { session, error -> session.sendAsync("error " + error.message) } // <4>
            .onClose { session, reason -> null } // <5>
        }
        // end::echo[]

        // tag::filter[]
        routes.GET("/private")
            .beforeReplacing { request -> // <1>
                request.headers.contains("X-Token") ? null : HttpResponse.status(HttpStatus.FORBIDDEN)
            }
            .and()
            .webSocket { ws -> ws
                .onOpen { session, request -> session.sendAsync("welcome") }
            }
        // end::filter[]

        // tag::streams[]
        routes.GET("/ticks").webSocket { ws -> ws
            .onOpen { session, request -> session.sendAllAsync(Flux.range(1, 3).map { "tick " + it }) } // <1>
        }
        routes.GET("/upper").webSocket { ws -> ws
            .onMessageStream(String) { session, messages -> // <2>
                session.sendAllAsync(Flux.from(messages).map { it.toUpperCase() }) // <3>
            }
        }
        // end::streams[]

        // tag::delivery[]
        routes.GET("/jobs")
            .executeOn(TaskExecutors.BLOCKING) // <1>
            .webSocket { ws -> ws
                .maxConcurrentMessages(4) // <2>
                .maxPendingMessages(32) // <3>
                .onMessage(String) { session, job -> session.sendAsync("done " + job) }
            }
        // end::delivery[]
    }
}
