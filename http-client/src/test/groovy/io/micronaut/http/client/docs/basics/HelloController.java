package io.micronaut.http.client.docs.basics;

// tag::imports[]

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.reactivex.Maybe;

import static io.micronaut.http.HttpRequest.GET;
// end::imports[]

@Controller("/")
public class HelloController {

    private final RxHttpClient httpClient;

    public HelloController(@Client("/endpoint") RxHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // tag::nonblocking[]
    @Get("/hello/{name}")
    Maybe<String> hello(String name) { // <1>
        return httpClient.retrieve( GET("/hello/" + name) )
                         .firstElement(); // <2>
    }
    // end::nonblocking[]

    @Get("/endpoint/hello/{name}")
    String helloEndpoint(String name) {
        return "Hello " + name;
    }

    // tag::json[]
    @Get("/greet/{name}")
    Message greet(String name) {
        return new Message("Hello " + name);
    }
    // end::json[]

    @Post("/greet")
    @Status(HttpStatus.CREATED)
    Message echo(@Body Message message) {
        return message;
    }

    @Post(value = "/hello", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
    @Status(HttpStatus.CREATED)
    String echoHello(@Body String message) {
        return message;
    }

}
