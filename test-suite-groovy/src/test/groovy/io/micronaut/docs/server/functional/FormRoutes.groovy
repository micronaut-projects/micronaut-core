package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.form.FileUpload
import io.micronaut.http.form.FormData
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton

import java.nio.file.Path
// end::imports[]

@Requires(property = "spec.name", value = "FormRoutesSpec")
// tag::clazz[]
@Singleton
class FormRoutes implements HttpRoutes {

    private final Path uploads

    FormRoutes(@Value('${uploads.directory}') Path uploads) {
        this.uploads = uploads
    }

    @Override
    void routes(HttpRouteBuilder routes) {
        routes.POST("/forms/signup").form().handle { request, pathVariables, FormData form -> // <1>
            HttpResponse.ok("Welcome " + form.getString("name") + ", " + form.getInt("age", 18))
                .contentType(MediaType.TEXT_PLAIN_TYPE)
        }
        routes.POST("/forms/profile").consumes(MediaType.MULTIPART_FORM_DATA_TYPE).body().handleAsync { request, pathVariables, body ->
            body.form().thenCompose { FormData form -> // <2>
                FileUpload avatar = form.getFile("avatar") // <3>
                avatar.bytes(1024 * 1024)
                    .thenApply { byte[] bytes ->
                        HttpResponse.ok(form.getString("name") + " sent " + avatar.fileName() + ", " + bytes.length + " bytes")
                            .contentType(MediaType.TEXT_PLAIN_TYPE)
                    }
            }
        }
        routes.POST("/forms/upload").consumes(MediaType.MULTIPART_FORM_DATA_TYPE).body().handleAsync { request, pathVariables, body ->
            Path destination = uploads.resolve(UUID.randomUUID().toString() + ".upload")
            body.parts() // <4>
                .part("file") { part -> part.file().transferTo(destination) } // <5>
                .thenApply { boolean found ->
                    found
                        ? HttpResponse.created(destination.fileName.toString()).contentType(MediaType.TEXT_PLAIN_TYPE)
                        : HttpResponse.badRequest("no file")
                }
        }
    }
}
// end::clazz[]
