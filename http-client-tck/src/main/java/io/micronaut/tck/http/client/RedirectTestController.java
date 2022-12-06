package io.micronaut.tck.http.client;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Produces;

import java.net.URI;

@Requires(property = "spec.name", value = "RedirectTest")
@Controller("/redirect")
public class RedirectTestController {

    @Get("/redirect")
    HttpResponse<?> redirect() {
        return HttpResponse.redirect(URI.create("/redirect/direct"));
    }

    @Get("/redirect-relative")
    HttpResponse<?> redirectRelative() {
        return HttpResponse.redirect(URI.create("./direct"));
    }

    @Get("/redirect-host")
    HttpResponse<?> redirectHost(@Header String redirect) {
        return HttpResponse.redirect(URI.create(redirect));
    }

    @Get("/direct")
    @Produces("text/plain")
    HttpResponse<?> direct() {
        return HttpResponse.ok("It works!");
    }
}
