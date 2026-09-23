package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
// end::imports[]

@Requires(property = "spec.name", value = "BodyRoutesTest")
// tag::clazz[]
@Singleton
public class BodyRoutes implements HttpRoutes {
    private final ItemRepository items;
    private final Path uploads;

    BodyRoutes(ItemRepository items, @Value("${uploads.directory}") Path uploads) {
        this.items = items;
        this.uploads = uploads;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.asyncPOST("/async/items", (request, pathVariables) ->
            request.body(Item.class) // <1>
                .thenCompose(items::saveAsync)
                .thenApply(HttpResponse::created));

        routes.asyncPOST("/async/items/import", (request, pathVariables) ->
            request.elements(Item.class) // <2>
                .forEach(items::saveAsync)
                .thenApply(done -> HttpResponse.accepted()));

        routes.asyncPOST("/async/notes", (request, pathVariables) ->
                request.text(1024) // <3>
                    .thenApply(text -> HttpResponse.ok("received " + text.length() + " characters")))
            .consumes(MediaType.TEXT_PLAIN_TYPE);

        routes.asyncPUT("/async/files", (request, pathVariables) -> {
            Path destination = uploads.resolve(UUID.randomUUID() + ".bin");
            return request.transferTo(destination) // <4>
                .thenApply(done -> HttpResponse.created(destination.getFileName().toString()));
        }).consumes(MediaType.APPLICATION_OCTET_STREAM_TYPE);

        routes.asyncPOST("/async/guarded", (request, pathVariables) -> {
            if (!request.getHeaders().contains("X-Token")) {
                return CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.UNAUTHORIZED)); // <5>
            }
            return request.bytes(64 * 1024)
                .thenApply(bytes -> HttpResponse.ok("accepted " + bytes.length + " bytes"));
        }).consumes(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }
}
// end::clazz[]
