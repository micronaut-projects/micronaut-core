package io.micronaut.docs.http.client.bind;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;

@Controller
class ClientBindController {

    @Get("/client/bind")
    String test(@Header("X-Metadata-Version") String version) {
        return version;
    }
}
