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
package io.micronaut.docs.basics

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.reactivex.Maybe

import io.micronaut.http.HttpRequest.GET
// end::imports[]

@Requires(property = "spec.name", value = "HelloControllerSpec")
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
