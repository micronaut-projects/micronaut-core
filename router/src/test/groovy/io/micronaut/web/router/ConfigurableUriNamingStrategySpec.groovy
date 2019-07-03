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
package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.web.router.naming.ConfigurableUriNamingStrategy
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.http.HttpMethod.GET

/**
 * @author Andrey Tsarenko
 * @since 1.2.0
 */
class ConfigurableUriNamingStrategySpec extends Specification {

    void "By default ConfigurableUriNamingStrategy bean is not loaded"() {
        given:
        def applicationContext = new DefaultApplicationContext('test').start()

        expect:
        !applicationContext.containsBean(ConfigurableUriNamingStrategy)

        cleanup:
        applicationContext.close()
    }

    void "ConfigurableUriNamingStrategy bean is loaded if 'micronaut.server.context-path' specified"() {
        given:
        def applicationContext = ApplicationContext.run(
                PropertySource.of(
                        'test',
                        ['micronaut.server.context-path': '/test']
                )
        )

        expect:
        applicationContext.containsBean(ConfigurableUriNamingStrategy)

        cleanup:
        applicationContext.close()
    }

    @Unroll
    void "Test 'micronaut.server.context-path' matches with #route"() {
        given:
        def applicationContext = ApplicationContext.run(
                PropertySource.of(
                        'test',
                        ['micronaut.server.context-path': '/test']
                )
        )
                .start()
        def router = applicationContext.getBean(Router)

        expect:
        router."$method"(route).isPresent()
        router."$method"(route).get().invoke() == result

        cleanup:
        applicationContext.close()

        where:
        method | route                      | result
        GET    | '/test/city'               | 'Hello city'
        GET    | '/test/city/Madrid'        | 'City Madrid'
        GET    | '/test/city/country/Spain' | 'Country Spain'
    }

    @Unroll
    void "Test 'micronaut.server.context-path' not started with '/' and matches with #route"() {
        given:
        def applicationContext = ApplicationContext.run(
                PropertySource.of(
                        'test',
                        ['micronaut.server.context-path': 'test']
                )
        )
                .start()
        def router = applicationContext.getBean(Router)

        expect:
        router."$method"(route).isPresent()
        router."$method"(route).get().invoke() == result

        cleanup:
        applicationContext.close()

        where:
        method | route                      | result
        GET    | '/test/city'               | 'Hello city'
        GET    | '/test/city/Madrid'        | 'City Madrid'
        GET    | '/test/city/country/Spain' | 'Country Spain'
    }

    @Unroll
    void "Test 'micronaut.server.context-path' ending with '/' and matches with #route"() {
        given:
        def applicationContext = ApplicationContext.run(
                PropertySource.of(
                        'test',
                        ['micronaut.server.context-path': 'test/']
                )
        )
                .start()
        def router = applicationContext.getBean(Router)

        expect:
        router."$method"(route).isPresent()
        router."$method"(route).get().invoke() == result

        cleanup:
        applicationContext.close()

        where:
        method | route                      | result
        GET    | '/test/city'               | 'Hello city'
        GET    | '/test/city/Madrid'        | 'City Madrid'
        GET    | '/test/city/country/Spain' | 'Country Spain'
    }

    @Unroll
    void "Test 'micronaut.server.context-path' started and ending with '/' and matches with #route"() {
        given:
        def applicationContext = ApplicationContext.run(
                PropertySource.of(
                        'test',
                        ['micronaut.server.context-path': '/test/']
                )
        )
                .start()
        def router = applicationContext.getBean(Router)

        expect:
        router."$method"(route).isPresent()
        router."$method"(route).get().invoke() == result

        cleanup:
        applicationContext.close()

        where:
        method | route                      | result
        GET    | '/test/city'               | 'Hello city'
        GET    | '/test/city/Madrid'        | 'City Madrid'
        GET    | '/test/city/country/Spain' | 'Country Spain'
    }

    @Controller('/city')
    static class CityController {

        @Get
        String index() {
            'Hello city'
        }

        @Get('/{name}')
        String city(String name) {
            "City $name"
        }

        @Get('country/{name}')
        String country(String name) {
            "Country $name"
        }
    }
}
