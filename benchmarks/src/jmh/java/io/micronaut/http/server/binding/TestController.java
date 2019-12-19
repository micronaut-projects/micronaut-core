package io.micronaut.http.server.binding;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/arguments")
public class TestController {

    @Get("/foo/{name}/{age}")
    String show(String name, int age) {
        return name + " is " + age;
    }
}
