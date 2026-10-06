package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import java.net.URI
// end::imports[]

@Requires(property = "spec.name", value = "GroupRoutesTest")
@Singleton
class GroupRoutes : HttpRoutes {

    /**
     * The tenant of a request, propagated to the filters and the handlers.
     *
     * @param id The tenant id
     */
    data class Tenant(val id: String) : PropagatedContextElement

    override fun routes(routes: HttpRouteBuilder) {
        // tag::groups[]
        routes.path("/api") { api -> // <1>
            api.beforeReplacing { request: MutableHttpRequest<*>, propagatedContext: MutablePropagatedContext -> // <2>
                val tenant = request.headers["X-Tenant"]
                    ?: return@beforeReplacing HttpResponse.badRequest("Missing tenant")
                propagatedContext.add(Tenant(tenant))
                null
            }
            api.after { request: HttpRequest<*>, response: MutableHttpResponse<*> -> response.header("X-Api", "v1") } // <3>
            api.GET("/orders") { request, pathVariables ->
                text("orders of " + PropagatedContext.get().get(Tenant::class.java).id)
            }
            api.path("/admin") { admin -> // <4>
                admin.beforeReplacing { request: MutableHttpRequest<*> ->
                    if ("admin" == request.headers["X-Role"]) null
                    else HttpResponse.status<Any>(HttpStatus.FORBIDDEN)
                }
                admin.GET("/users") { request, pathVariables -> text("users") }
            }
            api.GET("/reports/{id}")
                .before { request: MutableHttpRequest<*> -> // <5>
                    request.headers.add("X-Report-Format", "summary")
                }
                .and()
                .afterReplacing { request: HttpRequest<*>, response: MutableHttpResponse<*> -> // <6>
                    if (response.status == HttpStatus.OK && request.headers.contains("X-Legacy")) HttpResponse.status<Any>(HttpStatus.GONE)
                    else null
                }
                .and()
                .handle { request, pathVariables ->
                    text("report " + pathVariables.getInt("id") + " as " + request.headers["X-Report-Format"])
                }
            api.GET("/") { request, pathVariables -> text("api of " + PropagatedContext.get().get(Tenant::class.java).id) } // <7>
        }
        // end::groups[]
        // tag::groupSettings[]
        routes.path("/notes") { notes ->
            notes.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE) // <1>
            notes.executeOn(TaskExecutors.BLOCKING) // <2>
            notes.POST("/").body(String::class.java).handle { request, pathVariables, text -> HttpResponse.ok("saved $text") }
            notes.POST("/items")
                .consumes(MediaType.APPLICATION_JSON_TYPE) // <3>
                .body(Item::class.java).handle { request, pathVariables, item -> HttpResponse.ok("saved " + item.name) }
            notes.GET("/count")
                .nonBlocking() // <4>
                .handle { request, pathVariables -> HttpResponse.ok("1") }
            notes.path("/drafts") { drafts ->
                drafts.GET("/") { request, pathVariables -> HttpResponse.ok(listOf(Item(1, "draft"))) }
                drafts.produces(MediaType.APPLICATION_JSON_TYPE) // <5>
            }
        }
        // end::groupSettings[]
        // tag::serverFilters[]
        routes.serverFilter("/api/**").order(100) // <1>
            .after { request: HttpRequest<*>, response: MutableHttpResponse<*> -> response.header("X-Served-By", "api") }
        routes.serverFilter("/v1/**").preMatching() // <2>
            .before { request: MutableHttpRequest<*> ->
                request.uri(URI.create(request.uri.toString().replaceFirst(Regex("^/v1"), "/api")))
            }
        // end::serverFilters[]
        // tag::filterExecutor[]
        routes.GET("/audit/{id}")
            .before { request: MutableHttpRequest<*> -> // <1>
                request.setAttribute("audit-thread", Thread.currentThread().name)
            }
            .executeOn(TaskExecutors.BLOCKING) // <2>
            .and() // <3>
            .after { request: HttpRequest<*>, response: MutableHttpResponse<*> -> response.header("X-Audited", "true") }
            .and()
            .handle { request, pathVariables ->
                text("audited on " + request.getAttribute("audit-thread", String::class.java).orElse("none"))
            }
        // end::filterExecutor[]
    }

    companion object {
        private fun text(text: String): HttpResponse<*> = HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE)
    }
}
