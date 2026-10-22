package io.micronaut.docs.server.filters.bodybuffer

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

@Requires(property = "spec.name", value = "ChecksumFilterSpec")
// tag::clazz[]
@Controller("/checksum")
class ChecksumController {

    @Post(consumes = [MediaType.TEXT_PLAIN], produces = [MediaType.TEXT_PLAIN])
    fun upload(@Body body: String): String { // <1>
        return "Received ${body.length} characters"
    }
}
// end::clazz[]
