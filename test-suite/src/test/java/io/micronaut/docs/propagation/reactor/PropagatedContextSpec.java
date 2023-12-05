package io.micronaut.docs.propagation.reactor;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "PropagatedContextSpec")
@MicronautTest
public class PropagatedContextSpec {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testMonoRequest() {
        String hello = client.toBlocking().retrieve(HttpRequest.GET("/hello"), Argument.of(String.class));
        assertEquals("Hello, World", hello);
    }

}

@Requires(property = "spec.name", value = "PropagatedContextSpec")
// tag::example[]
@Controller
class HelloController {

    @Get("/hello")
    Mono<String> hello() {
        PropagatedContext propagatedContext = PropagatedContext.get(); // <1>
        return Mono.just("Hello, World")
            .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext)); // <2>
    }
}
// end::example[]
