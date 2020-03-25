/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
