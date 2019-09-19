package io.micronaut.docs.server.request;

// tag::imports[]

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import static io.micronaut.http.HttpResponse.ok;
// end::imports[]

// tag::class[]
@Controller("/request")
class MessageController {

    @Get("/hello") // <2>
    HttpResponse<String> hello(HttpRequest<?> request) {
        String name = request.getParameters()
                             .getFirst("name")
                             .orElse("Nobody") // <3>

        ok("Hello " + name + "!!")
                 .header("X-My-Header", "Foo") // <4>
    }
}
// end::class[]
