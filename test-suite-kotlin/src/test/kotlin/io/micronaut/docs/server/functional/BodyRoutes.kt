package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
// end::imports[]

@Requires(property = "spec.name", value = "BodyRoutesTest")
// tag::clazz[]
@Singleton
class BodyRoutes(
    private val items: ItemRepository,
    @Value("\${uploads.directory}") private val uploads: Path
) : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        routes.asyncPOST("/async/items") { request, pathVariables, body ->
            body.body(Item::class.java) // <1>
                .thenCompose { item -> items.saveAsync(item!!) }
                .thenApply { item -> HttpResponse.created(item) }
        }
        routes.asyncPOST("/async/items/import") { request, pathVariables, body ->
            body.elements(Item::class.java) // <2>
                .forEach(items::saveAsync)
                .thenApply { HttpResponse.accepted<Any>() }
        }
        routes.asyncPOST("/async/notes") { request, pathVariables, body ->
            body.text(1024) // <3>
                .thenApply { text -> HttpResponse.ok("received " + text.length + " characters") }
        }.consumes(MediaType.TEXT_PLAIN_TYPE)
        routes.asyncPUT("/async/files") { request, pathVariables, body ->
            val destination = uploads.resolve(UUID.randomUUID().toString() + ".bin")
            body.transferTo(destination) // <4>
                .thenApply { HttpResponse.created(destination.fileName.toString()) }
        }.consumes(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        routes.asyncPOST("/async/guarded") { request, pathVariables, body ->
            if (!request.headers.contains("X-Token")) {
                return@asyncPOST CompletableFuture.completedFuture(HttpResponse.status<Any>(HttpStatus.UNAUTHORIZED)) // <5>
            }
            body.bytes(64 * 1024)
                .thenApply { bytes -> HttpResponse.ok("accepted " + bytes.size + " bytes") }
        }.consumes(MediaType.APPLICATION_OCTET_STREAM_TYPE)
    }
}
// end::clazz[]
