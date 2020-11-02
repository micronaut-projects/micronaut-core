package io.micronaut.http.client

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Put

@Requires(property = "spec.name", value = "SuspendClientSpec")
@Controller
class SuspendClientController {

    @Put
    fun echo(@Body body: String): String {
        return body
    }
}
