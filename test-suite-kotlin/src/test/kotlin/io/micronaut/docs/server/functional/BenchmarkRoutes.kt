package io.micronaut.docs.server.functional

// tag::imports[]
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.DirectContext
import io.micronaut.web.router.builder.DirectRouteBuilder
import io.micronaut.web.router.builder.HttpDirectRoutes
import jakarta.inject.Singleton
import java.util.function.Function
// end::imports[]

@Requires(property = "spec.name", value = "BenchmarkRoutesTest")
// tag::clazz[]
@Singleton
class BenchmarkRoutes : HttpDirectRoutes {

    companion object {
        private val HELLO = "Hello, World!".toByteArray(Charsets.US_ASCII) // <1>
    }

    @Introspected
    data class Message(@JsonProperty("message") val message: String)

    override fun routes(routes: DirectRouteBuilder) {
        routes.GET("/plaintext").respond(Function { direct: DirectContext -> // <2>
            direct.responses().ok(HELLO)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.SERVER, "Micronaut") // <3>
        })
        routes.GET("/json").respond(Function { direct: DirectContext -> // <4>
            direct.responses().ok(Message("Hello, World!"))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.SERVER, "Micronaut")
        })
    }
}
// end::clazz[]
