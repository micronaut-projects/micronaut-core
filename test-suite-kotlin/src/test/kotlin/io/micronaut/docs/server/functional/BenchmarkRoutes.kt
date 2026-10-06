package io.micronaut.docs.server.functional

// tag::imports[]
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.MediaType
import io.micronaut.web.router.direct.DirectContext
import io.micronaut.web.router.direct.DirectRouteBuilder
import io.micronaut.web.router.direct.HttpDirectRoutes
import jakarta.inject.Singleton
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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

    private class HttpDate(val second: Long, val text: String)

    @Volatile
    private var date = HttpDate(-1, "") // <2>

    /**
     * The Date header value, formatted at most once per second.
     */
    fun date(): String {
        val second = System.currentTimeMillis() / 1000
        var current = date
        if (current.second != second) {
            current = HttpDate(second, DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(second).atZone(ZoneOffset.UTC)))
            date = current
        }
        return current.text
    }

    override fun routes(routes: DirectRouteBuilder) {
        routes.GET("/plaintext").respond(Function { direct: DirectContext -> // <3>
            direct.responses().ok(HELLO)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.SERVER, "Micronaut")
                .header(HttpHeaders.DATE, date())
        })
        routes.GET("/json").respond(Function { direct: DirectContext -> // <4>
            direct.responses().ok(Message("Hello, World!"))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.SERVER, "Micronaut")
                .header(HttpHeaders.DATE, date())
        })
    }
}
// end::clazz[]
