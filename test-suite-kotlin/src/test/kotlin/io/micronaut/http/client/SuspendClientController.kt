package io.micronaut.http.client

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import kotlinx.coroutines.delay

@Requires(property = "spec.name", value = "SuspendClientSpec")
@Controller
class SuspendClientController {

    @Put
    fun echo(@Body body: String): String {
        return body
    }

    @Get
    suspend fun notFound(): String? {
        delay(1)
        return null
    }
}
