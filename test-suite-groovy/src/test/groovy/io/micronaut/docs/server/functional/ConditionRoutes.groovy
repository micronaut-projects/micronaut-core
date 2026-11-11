package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.web.router.RouteAttributes
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.RequestCondition
import io.micronaut.web.router.builder.ValueMatcher
import jakarta.inject.Singleton

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
// end::imports[]

@Requires(property = "spec.name", value = "ConditionRoutesSpec")
@Singleton
class ConditionRoutes implements HttpRoutes {

    private static final Set<String> SHOPS = ["north", "south"] as Set
    private static final Argument<Map<String, Object>> ORDER = Argument.mapOf(String, Object)

    private final int managementPort

    ConditionRoutes(@Value('${management.port}') int managementPort) {
        this.managementPort = managementPort
    }

    @Override
    void routes(HttpRouteBuilder routes) {
        // tag::where[]
        routes.GET("/search")
            .where(RequestCondition.any( // <1>
                RequestCondition.header("X-Beta"),
                RequestCondition.query("beta", "true")))
            .order(-1) // <2>
            .handle { request, pathVariables -> text("beta search") }
        routes.GET("/search") { request, pathVariables -> text("search") } // <3>
        // end::where[]
        // tag::constrain[]
        routes.path("/shops/{shop}") { shop ->
            shop.constrain("shop", SHOPS) // <1>
            shop.GET("/stock") { request, pathVariables -> text("stock of " + pathVariables.getString("shop")) }
        }
        routes.GET("/items/{id}")
            .constrain("id", Long) { Long id -> id > 0 } // <2>
            .order(-1)
            .handle { request, pathVariables -> text("item " + pathVariables.getLong("id")) }
        routes.GET("/items/{name}") { request, pathVariables -> text("item named " + pathVariables.getString("name")) } // <3>
        // end::constrain[]
        // tag::matchers[]
        routes.GET("/downloads/{file}")
            .where(RequestCondition.header("X-Channel", ValueMatcher.oneOf("beta", "canary").ignoringCase()) // <1>
                .or(RequestCondition.cookie("channel", ValueMatcher.equalTo("beta"))))
            .where(RequestCondition.peerAddress("127.0.0.0/8", "::1")) // <2>
            .where(RequestCondition.after(Instant.parse("2026-01-01T00:00:00Z"))) // <3>
            .constrain("file", ValueMatcher.endsWith(".zip")) // <4>
            .handle { request, pathVariables -> text("download " + pathVariables.getString("file")) }
        // end::matchers[]

        // tag::body[]
        routes.POST("/orders")
            .where(RequestCondition.body(ORDER) { Map<String, Object> order -> order.kind == "express" }) // <1>
            .order(-1) // <2>
            .body(ORDER)
            .handle { request, pathVariables, Map<String, Object> order -> text("express order " + order.id) }
        routes.POST("/orders").body(ORDER).handle { request, pathVariables, Map<String, Object> order -> text("order " + order.id) } // <3>
        routes.POST("/imports")
            .where(RequestCondition.bodyText { String text -> text.startsWith("id,") }.maxBytes(1024)) // <4>
            .consumesAll()
            .handle { request, pathVariables -> text("csv import") }
        routes.POST("/payments")
            .where(RequestCondition.bodyAsync(ORDER) { Map<String, Object> order -> isKnownCustomer(order.customer) }) // <5>
            .body(ORDER)
            .handle { request, pathVariables, Map<String, Object> order -> text("payment of " + order.customer) }
        // end::body[]
        // tag::attributes[]
        routes.path("/reports") { reports ->
            reports.attribute("role", "auditor") // <1>
            reports.beforeReplacing { request ->
                String role = RouteAttributes.getRouteInfo(request) // <2>
                    .flatMap { route -> route.getAttribute("role", String) }
                    .orElseThrow()
                role == request.headers.get("X-Role") ? null : HttpResponse.status(HttpStatus.FORBIDDEN)
            }
            reports.GET("/daily") { request, pathVariables -> text("daily report") }
            reports.GET("/salaries")
                .attribute("role", "admin") // <3>
                .handle { request, pathVariables -> text("salaries") }
        }
        // end::attributes[]
        // tag::port[]
        routes.path("/management") { management ->
            management.port(managementPort) // <1>
            management.GET("/health") { request, pathVariables -> text("UP") }
        }
        // end::port[]
    }

    private static CompletionStage<Boolean> isKnownCustomer(Object customer) {
        // e.g. a lookup in a database
        CompletableFuture.supplyAsync { customer == "alice" }
    }

    private static HttpResponse<?> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE)
    }
}
