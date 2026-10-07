package io.micronaut.docs.server.asyncbody

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
// end::imports[]

@Requires(property = "spec.name", value = "PeopleControllerSpec")
// tag::class[]
@Controller("/messages")
class MessageController {

    @Post(consumes = MediaType.TEXT_PLAIN)
    String receive(@Body String message) { // <1>
        return "Received " + message
    }
}
// end::class[]
