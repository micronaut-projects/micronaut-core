package io.micronaut.docs.basics

// tag::imports[]

import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.reactivex.Maybe

import io.micronaut.http.HttpRequest.GET

// end::imports[]

@Controller("/")
class HelloController(@param:Client("/endpoint") private val httpClient: RxHttpClient) {

    // tag::nonblocking[]
    @Get("/hello/{name}")
    internal fun hello(name: String): Maybe<String> { // <1>
        return httpClient.retrieve(GET<Any>("/hello/$name"))
                .firstElement() // <2>
    }
    // end::nonblocking[]

    @Get("/endpoint/hello/{name}")
    internal fun helloEndpoint(name: String): String {
        return "Hello $name"
    }

    // tag::json[]
    @Get("/greet/{name}")
    internal fun greet(name: String): Message {
        return Message("Hello $name")
    }
    // end::json[]

    @Post("/greet")
    @Status(HttpStatus.CREATED)
    internal fun echo(@Body message: Message): Message {
        return message
    }

    @Post(value = "/hello", consumes = [MediaType.TEXT_PLAIN], produces = [MediaType.TEXT_PLAIN])
    @Status(HttpStatus.CREATED)
    internal fun echoHello(@Body message: String): String {
        return message
    }

}
