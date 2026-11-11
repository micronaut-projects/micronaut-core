package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "ItemRoutesSpec")
// tag::clazz[]
@Factory
class ItemRoutes {

    @Singleton
    HttpRoutes itemRoutes(ItemRepository items) { // <1>
        return { HttpRouteBuilder routes ->
            routes.GET("/items/{id}") { request, pathVariables -> // <2>
                HttpResponse.ok(items.find(pathVariables.getLong("id")))
            }
            routes.POST("/items") // <3>
                .body(Item) // <4>
                .handle { request, pathVariables, Item item -> HttpResponse.created(items.save(item)) }
            routes.GET("/items/{id}/name")
                .produces(MediaType.TEXT_PLAIN_TYPE) // <5>
                .handle { request, pathVariables -> HttpResponse.ok(items.find(pathVariables.getLong("id")).name()) }
            routes.DELETE("/items/{id}")
                .executeOn(TaskExecutors.BLOCKING) // <6>
                .handle { request, pathVariables ->
                    items.delete(pathVariables.getLong("id"))
                    HttpResponse.noContent()
                }
            routes.route([HttpMethod.PUT, HttpMethod.PATCH] as Set, "/items/{id}/touch") // <7>
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .handle { request, pathVariables ->
                    HttpResponse.ok("touched " + pathVariables.getLong("id") + " with " + request.method)
                }
            routes.route("PROPFIND", "/items").handle { request, pathVariables -> // <8>
                HttpResponse.ok("items: " + request.methodName).contentType(MediaType.TEXT_PLAIN_TYPE)
            }
            routes.GET("/items/count").handleAsync { request, pathVariables -> // <9>
                items.countAsync().thenApply { count -> HttpResponse.ok(count) }
            }
            routes.POST("/items/async")
                .body(Item)
                .handleAsync { request, pathVariables, Item item -> items.saveAsync(item).thenApply { saved -> HttpResponse.created(saved) } } // <10>
            routes.POST("/items/optional")
                .body(HttpRouteBuilder.nullableBody(Item)) // <11>
                .handle { request, pathVariables, Item item -> item == null ? HttpResponse.noContent() : HttpResponse.created(items.save(item)) }
        } as HttpRoutes
    }
}
// end::clazz[]
