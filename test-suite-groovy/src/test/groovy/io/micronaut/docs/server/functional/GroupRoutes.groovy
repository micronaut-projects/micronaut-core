package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "GroupRoutesSpec")
@Singleton
class GroupRoutes implements HttpRoutes {

    /**
     * The tenant of a request, propagated to the filters and the handlers.
     *
     * @param id The tenant id
     */
    static record Tenant(String id) implements PropagatedContextElement {
    }

    @Override
    void routes(HttpRouteBuilder routes) {
        // tag::groups[]
        routes.path("/api") { api -> // <1>
            api.beforeReplacing { MutableHttpRequest<?> request, MutablePropagatedContext propagatedContext -> // <2>
                String tenant = request.headers.get("X-Tenant")
                if (tenant == null) {
                    return HttpResponse.badRequest("Missing tenant")
                }
                propagatedContext.add(new Tenant(tenant))
                return null
            }
            api.after { request, MutableHttpResponse<?> response -> response.header("X-Api", "v1") } // <3>
            api.GET("/orders") { request, pathVariables ->
                text("orders of " + PropagatedContext.get().get(Tenant).id())
            }
            api.path("/admin") { admin -> // <4>
                admin.beforeReplacing { MutableHttpRequest<?> request ->
                    "admin" == request.headers.get("X-Role")
                        ? null
                        : HttpResponse.status(HttpStatus.FORBIDDEN)
                }
                admin.GET("/users") { request, pathVariables -> text("users") }
            }
            api.GET("/reports/{id}")
                .before { MutableHttpRequest<?> request -> // <5>
                    request.headers.add("X-Report-Format", "summary")
                }
                .and()
                .afterReplacing { request, MutableHttpResponse<?> response -> // <6>
                    response.status == HttpStatus.OK && request.headers.contains("X-Legacy")
                        ? HttpResponse.status(HttpStatus.GONE)
                        : null
                }
                .and()
                .handle { request, pathVariables ->
                    text("report " + pathVariables.getInt("id") + " as " + request.headers.get("X-Report-Format"))
                }
            api.GET("/") { request, pathVariables -> text("api of " + PropagatedContext.get().get(Tenant).id()) } // <7>
        }
        // end::groups[]
        // tag::groupSettings[]
        routes.path("/notes") { notes ->
            notes.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE) // <1>
            notes.executeOn(TaskExecutors.BLOCKING) // <2>
            notes.POST("/").body(String).handle { request, pathVariables, String text -> HttpResponse.ok("saved " + text) }
            notes.POST("/items")
                .consumes(MediaType.APPLICATION_JSON_TYPE) // <3>
                .body(Item).handle { request, pathVariables, Item item -> HttpResponse.ok("saved " + item.name()) }
            notes.GET("/count")
                .nonBlocking() // <4>
                .handle { request, pathVariables -> HttpResponse.ok("1") }
            notes.path("/drafts") { drafts ->
                drafts.GET("/") { request, pathVariables -> HttpResponse.ok([new Item(1, "draft")]) }
                drafts.produces(MediaType.APPLICATION_JSON_TYPE) // <5>
            }
        }
        // end::groupSettings[]
        // tag::serverFilters[]
        routes.serverFilter("/api/**").order(100) // <1>
            .after { request, MutableHttpResponse<?> response -> response.header("X-Served-By", "api") }
        routes.serverFilter("/v1/**").preMatching() // <2>
            .before { MutableHttpRequest<?> request ->
                request.uri(URI.create(request.uri.toString().replaceFirst("^/v1", "/api")))
            }
        // end::serverFilters[]
        // tag::filterExecutor[]
        routes.GET("/audit/{id}")
            .before { MutableHttpRequest<?> request -> // <1>
                request.setAttribute("audit-thread", Thread.currentThread().name)
            }
            .executeOn(TaskExecutors.BLOCKING) // <2>
            .and() // <3>
            .after { request, MutableHttpResponse<?> response -> response.header("X-Audited", "true") }
            .and()
            .handle { request, pathVariables ->
                text("audited on " + request.getAttribute("audit-thread", String).orElse("none"))
            }
        // end::filterExecutor[]
    }

    private static HttpResponse<?> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE)
    }
}
