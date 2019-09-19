package io.micronaut.docs.server.response

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

@Requires(property = 'spec.name', value = 'producesspec')
//tag::clazz[]
@Controller("/test")
class TestController {

    @Get
    HttpResponse index() {
        HttpResponse.ok().body("{\"msg\":\"This is JSON\"}")
    }

    @Produces(MediaType.TEXT_HTML) // <1>
    @Get("/html")
    String html() {
        "<html><title><h1>HTML</h1></title><body></body></html>"
    }
}
//end::clazz[]