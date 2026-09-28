from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory
from micronaut.http import HttpMethod, HttpResponse, MediaType
from micronaut.scheduling import TaskExecutors
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

from .Item import Item
from .ItemRepository import ItemRepository

Set = java.type("java.util.Set")
# end::imports[]


@Requires(property="spec.name", value="ItemRoutesTest")
# tag::clazz[]
@Factory
class ItemRoutes:

    @Singleton
    def item_routes(self, items: ItemRepository) -> HttpRoutes:  # <1>
        def routes(routes: HttpRouteBuilder) -> None:
            routes.GET("/items/{id}", lambda request, path_variables:  # <2>
                       HttpResponse.ok(items.find(path_variables.getLong("id"))))
            (routes.POST("/items")  # <3>
                .body(Item)  # <4>
                .handle(lambda request, path_variables, item: HttpResponse.created(items.save(item))))
            (routes.GET("/items/{id}/name")
                .produces(MediaType.TEXT_PLAIN_TYPE)  # <5>
                .handle(lambda request, path_variables: HttpResponse.ok(items.find(path_variables.getLong("id")).name)))

            def delete(request, path_variables):
                items.delete(path_variables.getLong("id"))
                return HttpResponse.noContent()

            (routes.DELETE("/items/{id}")
                .executeOn(TaskExecutors.BLOCKING)  # <6>
                .handle(delete))
            (routes.route(Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/items/{id}/touch")  # <7>
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .handle(lambda request, path_variables:
                        HttpResponse.ok(f"touched {path_variables.getLong('id')} with {request.getMethod()}")))
            routes.route("PROPFIND", "/items").handle(lambda request, path_variables:  # <8>
                                                      HttpResponse.ok("items: " + request.getMethodName()).contentType(MediaType.TEXT_PLAIN_TYPE))
            routes.GET("/items/count").handleAsync(lambda request, path_variables:  # <9>
                                                   items.count_async().thenApply(lambda count: HttpResponse.ok(count)))
            (routes.POST("/items/async")
                .body(Item)
                .handleAsync(lambda request, path_variables, item:
                             items.save_async(item).thenApply(lambda saved: HttpResponse.created(saved))))  # <10>
            (routes.POST("/items/optional")
                .body(HttpRouteBuilder.nullableBody(Item))  # <11>
                .handle(lambda request, path_variables, item:
                        HttpResponse.noContent() if item is None else HttpResponse.created(items.save(item))))

        return routes
# end::clazz[]
