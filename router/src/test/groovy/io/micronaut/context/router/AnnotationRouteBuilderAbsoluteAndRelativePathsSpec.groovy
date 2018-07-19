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

import static io.micronaut.http.HttpMethod.GET

import io.micronaut.context.DefaultApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.web.router.Router
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Iván López
 * @since 1.0
 */
class AnnotationRouteBuilderAbsoluteAndRelativePathsSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/265')
    @Unroll
    void "Test annotation matches #route"() {
        given:
        Router router = new DefaultApplicationContext("test")
            .start()
            .getBean(Router)

        expect:
        router."$method"(route).isPresent()
        router."$method"(route).get().invoke() == result

        where:
        method | route                   | result
        GET    | '/city'                 | 'Hello city'
        GET    | '/city/Madrid'          | 'City Madrid'
        GET    | '/city/country/Spain'   | 'Country Spain'
        GET    | '/city/country2/Spain'  | 'Country Spain'
        GET    | '/city2'                | 'Hello city2'
        GET    | '/city2/Madrid'         | 'City Madrid'
        GET    | '/city2/country/Spain'  | 'Country Spain'
        GET    | '/city2/country2/Spain' | 'Country Spain'
    }

    @Controller('/city')
    static class CityController {

        @Get('/')
        String index() {
            "Hello city"
        }

        @Get('/{name}')
        String city(String name) {
            "City $name"
        }

        @Get('country/{name}')
        String country(String name) {
            "Country $name"
        }

        @Get('/country2/{name}')
        String country2(String name) {
            "Country $name"
        }
    }

    @Controller('/city2/')
    static class City2Controller {

        @Get('/')
        String index() {
            "Hello city2"
        }

        @Get('/{name}')
        String city(String name) {
            "City $name"
        }

        @Get('country/{name}')
        String country(String name) {
            "Country $name"
        }

        @Get('/country2/{name}')
        String country2(String name) {
            "Country $name"
        }
    }
}
