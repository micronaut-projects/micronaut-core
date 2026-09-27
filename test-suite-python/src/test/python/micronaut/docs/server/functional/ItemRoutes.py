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
            routes.POST("/items", Item, lambda request, path_variables, item:  # <3>
                        HttpResponse.created(items.save(item)))
            routes.GET("/items/{id}/name", lambda request, path_variables:
                       HttpResponse.ok(items.find(path_variables.getLong("id")).name)) \
                .produces(MediaType.TEXT_PLAIN_TYPE)  # <4>

            def delete(request, path_variables):
                items.delete(path_variables.getLong("id"))
                return HttpResponse.noContent()

            routes.DELETE("/items/{id}", delete).executeOn(TaskExecutors.BLOCKING)  # <5>
            routes.handle(Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/items/{id}/touch", lambda request, path_variables:  # <6>
                          HttpResponse.ok(f"touched {path_variables.getLong('id')} with {request.getMethod()}")) \
                .produces(MediaType.TEXT_PLAIN_TYPE)
            routes.handle("PROPFIND", "/items", lambda request, path_variables:  # <7>
                          HttpResponse.ok("items: " + request.getMethodName()).contentType(MediaType.TEXT_PLAIN_TYPE))
            routes.asyncGET("/items/count", lambda request, path_variables:  # <8>
                            items.count_async().thenApply(lambda count: HttpResponse.ok(count)))
            routes.POST("/items/optional", HttpRouteBuilder.nullableBody(Item), lambda request, path_variables, item:  # <9>
                        HttpResponse.noContent() if item is None else HttpResponse.created(items.save(item)))

        return routes
# end::clazz[]
