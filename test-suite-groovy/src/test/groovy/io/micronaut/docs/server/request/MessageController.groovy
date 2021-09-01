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
package io.micronaut.docs.server.request

// tag::imports[]
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.context.ServerRequestContext
import reactor.core.publisher.Mono

// end::imports[]

// tag::class[]
@Controller("/request")
class MessageController {
// end::class[]

    // tag::request[]
    @Get("/hello") // <1>
    HttpResponse<String> hello(HttpRequest<?> request) {
        String name = request.parameters
                             .getFirst("name")
                             .orElse("Nobody") // <2>

        HttpResponse.ok("Hello " + name + "!!")
                 .header("X-My-Header", "Foo") // <3>
    }
    // end::request[]

    // tag::static-request[]
    @Get("/hello-static") // <1>
    HttpResponse<String> helloStatic() {
        HttpRequest<?> request = ServerRequestContext.currentRequest() // <1>
                .orElseThrow(() -> new RuntimeException("No request present"))
        String name = request.parameters
                .getFirst("name")
                .orElse("Nobody")

        HttpResponse.ok("Hello " + name + "!!")
                .header("X-My-Header", "Foo")
    }
    // end::static-request[]

    // tag::request-context[]
    @Get("/hello-reactor")
    Mono<HttpResponse<String>> helloReactor() {
        Mono.deferContextual(ctx -> { // <1>
            HttpRequest<?> request = ctx.get(ServerRequestContext.KEY) // <2>
            String name = request.parameters
                    .getFirst("name")
                    .orElse("Nobody")

            Mono.just(HttpResponse.ok("Hello " + name + "!!")
                    .header("X-My-Header", "Foo"))
        })
    }
    // end::request-context[]
// tag::endclass[]
}
// end::endclass[]
