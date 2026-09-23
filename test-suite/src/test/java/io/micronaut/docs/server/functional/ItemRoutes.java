package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.util.Set;
// end::imports[]

@Requires(property = "spec.name", value = "ItemRoutesTest")
// tag::clazz[]
@Factory
public class ItemRoutes {

    @Singleton
    HttpRoutes itemRoutes(ItemRepository items) { // <1>
        return routes -> {
            routes.GET("/items/{id}", (request, pathVariables) -> // <2>
                HttpResponse.ok(items.find(pathVariables.getLong("id"))));
            routes.POST("/items", Argument.of(Item.class), (request, pathVariables, item) -> // <3>
                HttpResponse.created(items.save(item)));
            routes.GET("/items/{id}/name", (request, pathVariables) ->
                    HttpResponse.ok(items.find(pathVariables.getLong("id")).name()))
                .produces(MediaType.TEXT_PLAIN_TYPE); // <4>
            routes.DELETE("/items/{id}", (request, pathVariables) -> {
                items.delete(pathVariables.getLong("id"));
                return HttpResponse.noContent();
            }).executeOn(TaskExecutors.BLOCKING); // <5>
            routes.handle(Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/items/{id}/touch", (request, pathVariables) -> // <6>
                    HttpResponse.ok("touched " + pathVariables.getLong("id") + " with " + request.getMethod()))
                .produces(MediaType.TEXT_PLAIN_TYPE);
            routes.handle("PROPFIND", "/items", (request, pathVariables) -> // <7>
                HttpResponse.ok("items: " + request.getMethodName()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.asyncGET("/items/count", (request, pathVariables) -> // <8>
                items.countAsync().thenApply(HttpResponse::ok));
            routes.POST("/items/optional", HttpRouteBuilder.nullableBody(Item.class), (request, pathVariables, item) -> // <9>
                item == null ? HttpResponse.noContent() : HttpResponse.created(items.save(item)));
        };
    }
}
// end::clazz[]
