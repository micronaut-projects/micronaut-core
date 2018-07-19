package io.micronaut.docs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.reactivex.Single;

import javax.validation.constraints.NotBlank;

@Controller("/")
public class IndexController {

    private MeterRegistry meterRegistry;

    public IndexController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Get("/hello/{name}")
    public Single hello(@NotBlank String name) {
        meterRegistry
                .counter("web.access", "controller", "index", "action", "hello")
                .increment();
        return Single.just("Hello " + name);
    }

}