package io.micronaut.annotation.processing

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.Status
import kotlinx.coroutines.delay

@Controller("/demo-error")
class DemoErrorController {

    @Get("/async/unit/throw")
    @Status(HttpStatus.NO_CONTENT)
    suspend fun asyncUnit(@QueryValue("id") message: String) {
        delay(1)
        throw JumpException(message)
    }

    class JumpException(message: String) : RuntimeException(message)

    @Error(exception = JumpException::class)
    fun handleJump(e: JumpException): HttpResponse<String> {
        return HttpResponse.ok(e.message)
    }

}
