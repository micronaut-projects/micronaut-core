package io.micronaut.docs.server.defaults

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import jakarta.validation.constraints.Size
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

@Requires(property = "spec.name", value = "defaults")
// tag::class[]
@Controller("/defaults")
open class DefaultsController {
// end::class[]

    // tag::echo[]
    @Post(value = "/echo", consumes = [MediaType.TEXT_PLAIN]) // <1>
    open fun echo(@Size(max = 1024) @Body text: String, @Header("MYHEADER") someHeader : String = "THEDEFAULT"): String { // <2>
        return someHeader // <3>
    }
    // end::echo[]

    // tag::echoReactive[]
    @Post(value = "/echo-publisher", consumes = [MediaType.TEXT_PLAIN]) // <1>
    @SingleResult
    open fun echoFlow(@Body text: Publisher<String>, @Header("MYHEADER") someHeader : String = "THEDEFAULT"): Publisher<HttpResponse<String>> { //<2>
        return Flux.from(text)
            .collect({ StringBuffer() }, { obj, str -> obj.append(str) }) // <3>
            .map { HttpResponse.ok(someHeader) }
    }
    // end::echoReactive[]

// tag::endclass[]
}
