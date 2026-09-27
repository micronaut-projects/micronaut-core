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
            routes.POST("/items", Item) { request, pathVariables, Item item -> // <3>
                HttpResponse.created(items.save(item))
            }
            routes.GET("/items/{id}/name") { request, pathVariables ->
                HttpResponse.ok(items.find(pathVariables.getLong("id")).name())
            }.produces(MediaType.TEXT_PLAIN_TYPE) // <4>
            routes.DELETE("/items/{id}") { request, pathVariables ->
                items.delete(pathVariables.getLong("id"))
                HttpResponse.noContent()
            }.executeOn(TaskExecutors.BLOCKING) // <5>
            routes.handle([HttpMethod.PUT, HttpMethod.PATCH] as Set, "/items/{id}/touch") { request, pathVariables -> // <6>
                HttpResponse.ok("touched " + pathVariables.getLong("id") + " with " + request.method)
            }.produces(MediaType.TEXT_PLAIN_TYPE)
            routes.handle("PROPFIND", "/items") { request, pathVariables -> // <7>
                HttpResponse.ok("items: " + request.methodName).contentType(MediaType.TEXT_PLAIN_TYPE)
            }
            routes.asyncGET("/items/count") { request, pathVariables -> // <8>
                items.countAsync().thenApply { count -> HttpResponse.ok(count) }
            }
            routes.POST("/items/optional", HttpRouteBuilder.nullableBody(Item)) { request, pathVariables, Item item -> // <9>
                item == null ? HttpResponse.noContent() : HttpResponse.created(items.save(item))
            }
        } as HttpRoutes
    }
}
// end::clazz[]
