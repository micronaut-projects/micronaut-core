package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
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
            routes.POST("/items") // <3>
                .body(Item.class) // <4>
                .handle((request, pathVariables, item) -> HttpResponse.created(items.save(item)));
            routes.GET("/items/{id}/name")
                .produces(MediaType.TEXT_PLAIN_TYPE) // <5>
                .handle((request, pathVariables) -> HttpResponse.ok(items.find(pathVariables.getLong("id")).name()));
            routes.DELETE("/items/{id}")
                .executeOn(TaskExecutors.BLOCKING) // <6>
                .handle((request, pathVariables) -> {
                    items.delete(pathVariables.getLong("id"));
                    return HttpResponse.noContent();
                });
            routes.route(Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/items/{id}/touch") // <7>
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .handle((request, pathVariables) ->
                    HttpResponse.ok("touched " + pathVariables.getLong("id") + " with " + request.getMethod()));
            routes.route("PROPFIND", "/items").handle((request, pathVariables) -> // <8>
                HttpResponse.ok("items: " + request.getMethodName()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/items/count").handleAsync((request, pathVariables) -> // <9>
                items.countAsync().thenApply(HttpResponse::ok));
            routes.POST("/items/async")
                .body(Item.class)
                .handleAsync((request, pathVariables, item) -> items.saveAsync(item).thenApply(HttpResponse::created)); // <10>
            routes.POST("/items/optional")
                .body(HttpRouteBuilder.nullableBody(Item.class)) // <11>
                .handle((request, pathVariables, item) -> item == null ? HttpResponse.noContent() : HttpResponse.created(items.save(item)));
        };
    }
}
// end::clazz[]
