package io.micronaut.docs.http.client.bind

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header

@Controller
class ClientBindController {

    @Get("/client/bind")
    fun test(@Header("X-Metadata-Version") version: String): String {
        return version
    }
}
