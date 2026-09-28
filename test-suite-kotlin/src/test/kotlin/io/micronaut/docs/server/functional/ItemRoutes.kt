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

@Requires(property = "spec.name", value = "ItemRoutesTest")
// tag::clazz[]
@Factory
class ItemRoutes {

    @Singleton
    fun itemRoutes(items: ItemRepository): HttpRoutes = HttpRoutes { routes -> // <1>
        routes.GET("/items/{id}") { request, pathVariables -> // <2>
            HttpResponse.ok(items.find(pathVariables.getLong("id")))
        }
        routes.POST("/items") // <3>
            .body(Item::class.java) // <4>
            .handle { request, pathVariables, item -> HttpResponse.created(items.save(item)) }
        routes.GET("/items/{id}/name")
            .produces(MediaType.TEXT_PLAIN_TYPE) // <5>
            .handle { request, pathVariables -> HttpResponse.ok(items.find(pathVariables.getLong("id")).name) }
        routes.DELETE("/items/{id}")
            .executeOn(TaskExecutors.BLOCKING) // <6>
            .handle { request, pathVariables ->
                items.delete(pathVariables.getLong("id"))
                HttpResponse.noContent<Any>()
            }
        routes.route(setOf(HttpMethod.PUT, HttpMethod.PATCH), "/items/{id}/touch") // <7>
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
            .body(Item::class.java)
            .handleAsync { request, pathVariables, item -> items.saveAsync(item).thenApply { saved -> HttpResponse.created(saved) } } // <10>
        routes.POST("/items/optional")
            .body(HttpRouteBuilder.nullableBody(Item::class.java)) // <11>
            .handle { request, pathVariables, item: Item? -> if (item == null) HttpResponse.noContent<Any>() else HttpResponse.created(items.save(item)) }
    }
}
// end::clazz[]
