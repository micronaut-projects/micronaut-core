from micronaut.context.annotation import Requires
# tag::imports[]
import re
from dataclasses import dataclass

import java
from jakarta.inject import Singleton
from micronaut.http import HttpResponse, HttpStatus, MediaType
from micronaut.scheduling import TaskExecutors
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

from .Item import Item

List = java.type("java.util.List")
PropagatedContext = java.type("io.micronaut.core.propagation.PropagatedContext")
PropagatedContextElement = java.type("io.micronaut.core.propagation.PropagatedContextElement")
String = java.type("java.lang.String")
Thread = java.type("java.lang.Thread")
URI = java.type("java.net.URI")
# end::imports[]


@dataclass
class Tenant(PropagatedContextElement):
    """The tenant of a request, propagated to the filters and the handlers."""

    id: str


def text(text: str) -> HttpResponse:
    return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE)


@Requires(property="spec.name", value="GroupRoutesTest")
@Singleton
class GroupRoutes(HttpRoutes):

    def routes(self, routes: HttpRouteBuilder) -> None:
        # tag::groups[]
        def api_routes(api):  # <1>
            def tenant_filter(request, propagated_context):  # <2>
                tenant = request.getHeaders().get("X-Tenant")
                if tenant is None:
                    return HttpResponse.badRequest("Missing tenant")
                propagated_context.add(Tenant(tenant))
                return None

            api.beforeReplacing(tenant_filter)
            api.after(lambda request, response: response.header("X-Api", "v1"))  # <3>
            api.GET("/orders", lambda request, path_variables:
                    text("orders of " + PropagatedContext.get().get(Tenant).id))

            def admin_routes(admin):  # <4>
                admin.beforeReplacing(lambda request:
                                      None if request.getHeaders().get("X-Role") == "admin"
                                      else HttpResponse.status(HttpStatus.FORBIDDEN))
                admin.GET("/users", lambda request, path_variables: text("users"))

            api.path("/admin", admin_routes)
            (api.GET("/reports/{id}")
                .before(lambda request:  # <5>
                        request.getHeaders().add("X-Report-Format", "summary"))
                .and_()
                .afterReplacing(lambda request, response:  # <6>
                                HttpResponse.status(HttpStatus.GONE)
                                if response.getStatus() == HttpStatus.OK and request.getHeaders().contains("X-Legacy")
                                else None)
                .and_()
                .handle(lambda request, path_variables:
                        text(f"report {path_variables.getInt('id')} as {request.getHeaders().get('X-Report-Format')}")))
            api.GET("/", lambda request, path_variables:
                    text("api of " + PropagatedContext.get().get(Tenant).id))  # <7>

        routes.path("/api", api_routes)
        # end::groups[]
        # tag::groupSettings[]
        def notes_routes(notes):
            notes.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE)  # <1>
            notes.executeOn(TaskExecutors.BLOCKING)  # <2>
            notes.POST("/").body(String).handle(lambda request, path_variables, text: HttpResponse.ok("saved " + text))
            (notes.POST("/items")
                .consumes(MediaType.APPLICATION_JSON_TYPE)  # <3>
                .body(Item).handle(lambda request, path_variables, item: HttpResponse.ok("saved " + item.name)))
            (notes.GET("/count")
                .nonBlocking()  # <4>
                .handle(lambda request, path_variables: HttpResponse.ok("1")))

            def drafts_routes(drafts):
                drafts.GET("/", lambda request, path_variables: HttpResponse.ok(List.of(Item(1, "draft"))))
                drafts.produces(MediaType.APPLICATION_JSON_TYPE)  # <5>

            notes.path("/drafts", drafts_routes)

        routes.path("/notes", notes_routes)
        # end::groupSettings[]
        # tag::serverFilters[]
        (routes.filter("/api/**").order(100)  # <1>
            .after(lambda request, response: response.header("X-Served-By", "api")))
        (routes.filter("/v1/**").preMatching()  # <2>
            .before(lambda request: request.uri(URI.create(re.sub("^/v1", "/api", str(request.getUri()))))))
        # end::serverFilters[]
        # tag::filterExecutor[]
        (routes.GET("/audit/{id}")
            .before(lambda request:  # <1>
                    request.setAttribute("audit-thread", Thread.currentThread().getName()))
            .executeOn(TaskExecutors.BLOCKING)  # <2>
            .and_()  # <3>
            .after(lambda request, response: response.header("X-Audited", "true"))
            .and_()
            .handle(lambda request, path_variables:
                    text("audited on " + request.getAttribute("audit-thread", String).orElse("none"))))
        # end::filterExecutor[]
