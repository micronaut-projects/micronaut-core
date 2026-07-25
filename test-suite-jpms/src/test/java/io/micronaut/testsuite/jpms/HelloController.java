package io.micronaut.testsuite.jpms;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/hello")
class HelloController {

    @Get("/world")
    String world() {
        return "Hello World";
    }
}
