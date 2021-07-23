package io.micronaut.docs.http.client.bind

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import javax.annotation.Nullable

@Controller
class ClientBindController {

    @Get("/client/bind")
    fun test(@Header("X-Metadata-Version") version: String): String {
        return version
    }

    @Get("/client/authorized-resource{?name}")
    fun authorized(@QueryValue @Nullable name: String): String {
        return "Hello, $name"
    }
}
