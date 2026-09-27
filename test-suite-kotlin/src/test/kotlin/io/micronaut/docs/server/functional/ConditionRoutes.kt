package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.web.router.RouteAttributes
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.RouteCondition
import io.micronaut.web.router.builder.ValueMatcher
import jakarta.inject.Singleton
import java.time.Instant
// end::imports[]

@Requires(property = "spec.name", value = "ConditionRoutesTest")
@Singleton
class ConditionRoutes(@Value("\${management.port}") private val managementPort: Int) : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        // tag::where[]
        routes.GET("/search") { request, pathVariables -> text("beta search") }
            .where(RouteCondition.any( // <1>
                RouteCondition.header("X-Beta"),
                RouteCondition.query("beta", "true")))
            .order(-1) // <2>
        routes.GET("/search") { request, pathVariables -> text("search") } // <3>
        // end::where[]
        // tag::constrain[]
        routes.path("/shops/{shop}") { shop ->
            shop.constrain("shop", SHOPS) // <1>
            shop.GET("/stock") { request, pathVariables -> text("stock of " + pathVariables.getString("shop")) }
        }
        routes.GET("/items/{id}") { request, pathVariables -> text("item " + pathVariables.getLong("id")) }
            .constrain("id", Long::class.java) { id -> id > 0 } // <2>
            .order(-1)
        routes.GET("/items/{name}") { request, pathVariables -> text("item named " + pathVariables.getString("name")) } // <3>
        // end::constrain[]
        // tag::matchers[]
        routes.GET("/downloads/{file}") { request, pathVariables -> text("download " + pathVariables.getString("file")) }
            .where(RouteCondition.header("X-Channel", ValueMatcher.oneOf("beta", "canary").ignoringCase()) // <1>
                .or(RouteCondition.cookie("channel", ValueMatcher.equalTo("beta"))))
            .where(RouteCondition.peerAddress("127.0.0.0/8", "::1")) // <2>
            .where(RouteCondition.after(Instant.parse("2026-01-01T00:00:00Z"))) // <3>
            .constrain("file", ValueMatcher.endsWith(".zip")) // <4>
        // end::matchers[]
        // tag::attributes[]
        routes.path("/reports") { reports ->
            reports.attribute("role", "auditor") // <1>
            reports.beforeReplacing { request ->
                val role = RouteAttributes.getRouteInfo(request) // <2>
                    .flatMap { route -> route.getAttribute("role", String::class.java) }
                    .orElseThrow()
                if (role == request.headers["X-Role"]) null else HttpResponse.status<Any>(HttpStatus.FORBIDDEN)
            }
            reports.GET("/daily") { request, pathVariables -> text("daily report") }
            reports.GET("/salaries") { request, pathVariables -> text("salaries") }
                .attribute("role", "admin") // <3>
        }
        // end::attributes[]
        // tag::port[]
        routes.path("/management") { management ->
            management.port(managementPort) // <1>
            management.GET("/health") { request, pathVariables -> text("UP") }
        }
        // end::port[]
    }

    companion object {
        private val SHOPS = setOf("north", "south")

        private fun text(text: String): HttpResponse<*> = HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE)
    }
}
