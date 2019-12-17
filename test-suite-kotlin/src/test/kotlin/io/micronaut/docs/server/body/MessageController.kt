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

import javax.validation.constraints.Size

// end::imports[]

// tag::echo[]
@Controller("/receive")
open class MessageController {

    @Post(value = "/echo", consumes = [MediaType.TEXT_PLAIN]) // <1>
    open fun echo(@Size(max = 1024) @Body text: String): String { // <2>
        return text // <3>
    }
    // end::echo[]

    // tag::echoReactive[]
    @Post(value = "/echo-flow", consumes = [MediaType.TEXT_PLAIN]) // <1>
    open fun echoFlow(@Body text: Flowable<String>): Single<MutableHttpResponse<String>> { //<2>
        return text.collect({ StringBuffer() }, { obj, str -> obj.append(str) }) // <3>
                .map { buffer -> HttpResponse.ok(buffer.toString()) }
    }
    // end::echoReactive[]
// tag::echo[]
}
// end::echo[]
