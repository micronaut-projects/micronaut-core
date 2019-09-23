package io.micronaut.docs.server.body

// tag::imports[]

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.functions.BiConsumer
import java.util.concurrent.Callable

import javax.validation.constraints.Size

// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Controller("/receive")
class MessageController {
    // end::class[]

    // tag::echo[]
    @Post(value = "/echo", consumes = [MediaType.TEXT_PLAIN]) // <1>
    open fun echo(@Size(max = 1024) @Body text: String): String { // <2>
        return text // <3>
    }
    // end::echo[]

    // tag::echoReactive[]
    @Post(value = "/echo-flow", consumes = [MediaType.TEXT_PLAIN]) // <1>
    internal fun echoFlow(@Body text: Flowable<String>): Single<MutableHttpResponse<String>> { //<2>
        return text.collect<StringBuffer>(Callable<StringBuffer> { StringBuffer() }, BiConsumer<StringBuffer, String> { obj, str -> obj.append(str) }) // <3>
                .map { buffer -> HttpResponse.ok(buffer.toString()) }
    }
    // end::echoReactive[]
}

