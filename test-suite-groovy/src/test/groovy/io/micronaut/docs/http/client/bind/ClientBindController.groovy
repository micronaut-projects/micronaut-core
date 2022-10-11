package io.micronaut.docs.http.client.bind

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue

import javax.annotation.Nullable

@Controller
class ClientBindController {

    @Get("/client/bind")
    String test(@Header("X-Metadata-Version") String version) {
        return version
    }

    @Get("/client/authorized-resource{?name}")
    String authorized(@QueryValue @Nullable String name) {
        return "Hello, " + name
    }
}
