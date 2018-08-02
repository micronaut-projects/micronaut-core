/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.context.router

import io.micronaut.context.DefaultApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.web.router.Router
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.http.HttpMethod.GET
import static io.micronaut.http.HttpMethod.POST

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationRouteBuilderSpec extends Specification {

    @Unroll
    void "Test annotation matches #route for arguments #arguments"() {
        given:
        Router router = new DefaultApplicationContext("test")
                .start()
                .getBean(Router)

        expect:
        router."$method"(route).isPresent()
        router."$method"(route).get().invoke(arguments as Object[]) == result

        where:
        method | route                   | arguments      | result
        GET    | '/person/name'          | ["Flintstone"] | "Fred Flintstone"
        POST   | '/person/message/World' | []             | "Hello World"
        GET    | '/'                     | []             | "welcome"
        GET    | ''                      | []             | "welcome"
        GET    | '/person/show/1'        | []             | "Person 1"
        GET    | '/person/1/friend/Joe'  | []             | "Person 1 Friend Joe"


    }

    @Controller('/')
    static class ApplicationController {
        @Get
        String index() {
            'welcome'
        }
    }

    @Controller('/person')
    static class PersonController {

        @Get('/name')
        String name(String name) {
            return "Fred $name"
        }

        @Get('/show{/id}')
        String byId(Long id) {
            return "Person $id"
        }

        @Get('{/id}/friend{/name}')
        String friend(Long id, String name) {
            return "Person $id Friend $name"
        }

        @Post('/message{/text}')
        String message(String text) {
            "Hello $text"
        }
    }
}
