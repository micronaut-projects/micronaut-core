package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.MediaType
import io.micronaut.http.server.routes.StaticResources
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "StaticResourceRoutesTest")
// tag::clazz[]
@Singleton
class StaticResourceRoutes(@Value("\${site.directory}") private val siteDirectory: String) : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        // tag::resources[]
        routes.GET("/assets") // <1>
            .after { _, response -> response.header("X-Assets", "true") }.and() // <2>
            .resources(StaticResources.classpath("functional-static/assets") // <3>
                .cacheControl("public, max-age=31536000, immutable")) // <4>

        routes.GET("/site").resources(StaticResources.fileSystem(siteDirectory)) // <5>
        // end::resources[]

        // tag::group[]
        routes.path("/manual") { manual ->
            manual.produces(MediaType.APPLICATION_JSON_TYPE) // <1>
            manual.executeOn(TaskExecutors.BLOCKING) // <2>
            manual.after { _, response -> response.header("X-Manual", "true") } // <3>
            manual.GET("").resources(StaticResources.classpath("functional-static/manual")) // <4>
        }
        // end::group[]
    }
}
// end::clazz[]
