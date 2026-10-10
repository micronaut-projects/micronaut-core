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

@Requires(property = "spec.name", value = "WebSocketRoutesTest")
@Singleton
class WebSocketRoutes : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        // tag::echo[]
        routes.GET("/echo/{name}").webSocket { ws -> ws // <1>
            .onOpen { session, _ -> // <2>
                session.sendAsync("Hello " + session.uriVariables.get("name", String::class.java).orElseThrow())
            }
            .onMessage(String::class.java) { session, message -> session.sendAsync("echo $message") } // <3>
            .onError { session, error -> session.sendAsync("error " + error.message) } // <4>
            .onClose { _, _ -> null } // <5>
        }
        // end::echo[]

        // tag::filter[]
        routes.GET("/private")
            .beforeReplacing { request -> // <1>
                if (request.headers.contains("X-Token")) null else HttpResponse.status<Any>(HttpStatus.FORBIDDEN)
            }
            .and()
            .webSocket { ws -> ws
                .onOpen { session, _ -> session.sendAsync("welcome") }
            }
        // end::filter[]

        // tag::streams[]
        routes.GET("/ticks").webSocket { ws -> ws
            .onOpen { session, _ -> session.sendAllAsync(Flux.range(1, 3).map { "tick $it" }) } // <1>
        }
        routes.GET("/upper").webSocket { ws -> ws
            .onMessageStream(String::class.java) { session, messages -> // <2>
                session.sendAllAsync(Flux.from(messages).map { it.uppercase() }) // <3>
            }
        }
        // end::streams[]

        // tag::delivery[]
        routes.GET("/jobs")
            .executeOn(TaskExecutors.BLOCKING) // <1>
            .webSocket { ws -> ws
                .maxConcurrentMessages(4) // <2>
                .maxPendingMessages(32) // <3>
                .onMessage(String::class.java) { session, job -> session.sendAsync("done $job") }
            }
        // end::delivery[]
    }
}
