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
import io.micronaut.http.HttpRequest.GET
import io.micronaut.http.HttpStatus.CREATED
import io.micronaut.http.MediaType.TEXT_PLAIN
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import io.micronaut.core.async.annotation.SingleResult
// end::imports[]

@Requires(property = "spec.name", value = "HelloControllerSpec")
@Controller("/")
class HelloController(@param:Client("/endpoint") private val httpClient: HttpClient) {

    // tag::nonblocking[]
    @Get("/hello/{name}")
    @SingleResult
    internal fun hello(name: String): Publisher<String> { // <1>
        return Flux.from(httpClient.retrieve(GET<Any>("/hello/$name")))
                         .next() // <2>
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
    @Status(CREATED)
    internal fun echo(@Body message: Message): Message {
        return message
    }

    @Post(value = "/hello", consumes = [TEXT_PLAIN], produces = [TEXT_PLAIN])
    @Status(CREATED)
    internal fun echoHello(@Body message: String): String {
        return message
    }
}
