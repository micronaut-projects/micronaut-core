package io.micronaut.http.server.util;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

@Controller
public class TestController {
    @Get
    @Produces(MediaType.TEXT_PLAIN)
    public HttpResponse<?> simpleGet() {
        return HttpResponse.ok().body("foo");
    }

    @Get("/no-type")
    public HttpResponse<?> simpleGetNoContentType() {
        return HttpResponse.ok().body("foo");
    }

    @Get("/json")
    public HttpResponse<?> simpleJsonGet() {
        return HttpResponse.ok().body(new SimpleModel("foo"));
    }
}
