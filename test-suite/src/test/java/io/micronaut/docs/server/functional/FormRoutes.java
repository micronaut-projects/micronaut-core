package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.form.FileUpload;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.UUID;
// end::imports[]

@Requires(property = "spec.name", value = "FormRoutesTest")
// tag::clazz[]
@Singleton
public class FormRoutes implements HttpRoutes {
    private final Path uploads;

    FormRoutes(@Value("${uploads.directory}") Path uploads) {
        this.uploads = uploads;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.POST("/forms/signup", (request, pathVariables, form) -> // <1>
            HttpResponse.ok("Welcome " + form.getString("name") + ", " + form.getInt("age", 18))
                .contentType(MediaType.TEXT_PLAIN_TYPE));

        routes.asyncPOST("/forms/profile", (request, pathVariables, body) ->
            body.form().thenCompose(form -> { // <2>
                FileUpload avatar = form.getFile("avatar"); // <3>
                return avatar.bytes(1024 * 1024)
                    .thenApply(bytes -> HttpResponse.ok(form.getString("name") + " sent " + avatar.fileName() + ", " + bytes.length + " bytes")
                        .contentType(MediaType.TEXT_PLAIN_TYPE));
            })
        ).consumes(MediaType.MULTIPART_FORM_DATA_TYPE);

        routes.asyncPOST("/forms/upload", (request, pathVariables, body) -> {
            Path destination = uploads.resolve(UUID.randomUUID() + ".upload");
            return body.parts() // <4>
                .part("file", part -> part.file().transferTo(destination)) // <5>
                .thenApply(found -> found
                    ? HttpResponse.created(destination.getFileName().toString()).contentType(MediaType.TEXT_PLAIN_TYPE)
                    : HttpResponse.badRequest("no file"));
        }).consumes(MediaType.MULTIPART_FORM_DATA_TYPE);
    }
}
// end::clazz[]
