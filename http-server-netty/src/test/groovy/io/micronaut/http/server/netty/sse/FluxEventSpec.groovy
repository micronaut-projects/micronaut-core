/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.netty.sse

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux

import javax.validation.constraints.NotBlank

class FluxEventSpec extends AbstractMicronautSpec {

    void "test reactor flux events"() {
        given:
        RxStreamingHttpClient client = RxStreamingHttpClient.create(embeddedServer.getURL())

        when:
        Hello hello = client.jsonStream(HttpRequest.GET('/flux/hello/fred'), Hello).blockingFirst()

        then:
        hello.name == "test1"
        hello.number == 1
    }

    @Controller("/")
    static class HelloController {

        @Produces(MediaType.APPLICATION_JSON_STREAM) // add 'application/stream+json'
        @Get("/flux/hello/{name}")
        Flux<Hello> hello(@NotBlank String name) {

            List<Hello> list = new ArrayList<>()

            list.add(new Hello("test1", 1))
            list.add(new Hello("test2", 2))

            return Flux.fromIterable(list).doOnComplete( {->
                System.out.println("response should be closed here!")
            })
        }
    }

    static class Hello {
        String name
        int number

        Hello(String name, int number) {
            this.name = name
            this.number = number
        }

        Hello() {
        }
    }
}
