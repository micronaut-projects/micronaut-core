package io.micronaut.http.client

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import kotlinx.coroutines.delay

class Bar {
    var name: String = ""
    constructor()
    constructor(name: String) { this.name = name }
}

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

    @Get("/bars")
    suspend fun getBars(): List<Bar> {
        delay(1)
        return listOf(Bar("hello"))
    }
}
