package io.micronaut.http.client.convert;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

@Controller("/convert")
@Requires(property = "spec.name", value = "TypeConverterWithMethodReferenceSpec")
public class FooBarController {

    @Get("/foo/{foo}")
    public Foo foo(@PathVariable(name = "foo") Foo foo) {
        return foo;
    }

    @Get("/bar/{bar}")
    public Bar bar(@PathVariable(name = "bar") Bar bar) {
        return bar;
    }
}
