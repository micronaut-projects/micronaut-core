package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import java.nio.file.Path
import java.util.UUID
// end::imports[]

@Requires(property = "spec.name", value = "FormRoutesTest")
// tag::clazz[]
@Singleton
class FormRoutes(@Value("\${uploads.directory}") private val uploads: Path) : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        routes.POST("/forms/signup").form().handle { request, pathVariables, form -> // <1>
            HttpResponse.ok("Welcome " + form.getString("name") + ", " + form.getInt("age", 18))
                .contentType(MediaType.TEXT_PLAIN_TYPE)
        }
        routes.POST("/forms/profile").consumes(MediaType.MULTIPART_FORM_DATA_TYPE).body().handleAsync { request, pathVariables, body ->
            body.form().thenCompose { form -> // <2>
                val avatar = form.getFile("avatar") // <3>
                avatar.bytes(1024 * 1024)
                    .thenApply { bytes ->
                        HttpResponse.ok(form.getString("name") + " sent " + avatar.fileName() + ", " + bytes.size + " bytes")
                            .contentType(MediaType.TEXT_PLAIN_TYPE)
                    }
            }
        }
        routes.POST("/forms/upload").consumes(MediaType.MULTIPART_FORM_DATA_TYPE).body().handleAsync { request, pathVariables, body ->
            val destination = uploads.resolve(UUID.randomUUID().toString() + ".upload")
            body.parts() // <4>
                .part("file") { part -> part.file().transferTo(destination) } // <5>
                .thenApply { found ->
                    if (found) HttpResponse.created(destination.fileName.toString()).contentType(MediaType.TEXT_PLAIN_TYPE)
                    else HttpResponse.badRequest("no file")
                }
        }
    }
}
// end::clazz[]
