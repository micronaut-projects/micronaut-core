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
import java.util.concurrent.CompletableFuture
// end::imports[]

@Requires(property = "spec.name", value = "BodyRoutesSpec")
// tag::clazz[]
@Singleton
class BodyRoutes implements HttpRoutes {

    private final ItemRepository items
    private final Path uploads

    BodyRoutes(ItemRepository items, @Value('${uploads.directory}') Path uploads) {
        this.items = items
        this.uploads = uploads
    }

    @Override
    void routes(HttpRouteBuilder routes) {
        routes.asyncPOST("/async/items") { request, pathVariables, body ->
            body.body(Item) // <1>
                .thenCompose(items.&saveAsync)
                .thenApply { item -> HttpResponse.created(item) }
        }
        routes.asyncPOST("/async/items/import") { request, pathVariables, body ->
            body.elements(Item) // <2>
                .forEach(items.&saveAsync)
                .thenApply { done -> HttpResponse.accepted() }
        }
        routes.asyncPOST("/async/notes") { request, pathVariables, body ->
            body.text(1024) // <3>
                .thenApply { String text -> HttpResponse.ok("received " + text.length() + " characters") }
        }.consumes(MediaType.TEXT_PLAIN_TYPE)
        routes.asyncPUT("/async/files") { request, pathVariables, body ->
            Path destination = uploads.resolve(UUID.randomUUID().toString() + ".bin")
            body.transferTo(destination) // <4>
                .thenApply { done -> HttpResponse.created(destination.fileName.toString()) }
        }.consumes(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        routes.asyncPOST("/async/guarded") { request, pathVariables, body ->
            if (!request.headers.contains("X-Token")) {
                return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.UNAUTHORIZED)) // <5>
            }
            body.bytes(64 * 1024)
                .thenApply { byte[] bytes -> HttpResponse.ok("accepted " + bytes.length + " bytes") }
        }.consumes(MediaType.APPLICATION_OCTET_STREAM_TYPE)
    }
}
// end::clazz[]
