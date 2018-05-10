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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.annotation.Controller
import io.micronaut.web.router.GroovyRouteBuilder
import io.micronaut.web.router.RouteMatch
import io.micronaut.web.router.Router
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyRouteBuilderSpec extends Specification {

    @Unroll
    void "Test uri #method #uri matches route for routes #routeBean.name"() {

        given:
        def context = new DefaultApplicationContext("test").start()
        Router router = context.getBean(Router)

        Optional<RouteMatch> route = router."${method}"(uri)

        expect:
        route.isPresent() == isPresent
        route.isPresent() ? route.get().invoke() : null == result

        where:
        uri                 | method            | isPresent | routeBean      | result
        '/bo'               | HttpMethod.GET    | false     | MyRoutes       | null
        '/book'             | HttpMethod.GET    | true      | MyRoutes       | ['book1']
        '/book/hello/World' | HttpMethod.POST   | true      | MyRoutes       | "Hello World"
        '/book'             | HttpMethod.POST   | true      | MyRoutes       | "saved"
        '/book/1'           | HttpMethod.GET    | true      | MyRoutes       | "book 1"
        '/book/1/author'    | HttpMethod.GET    | true      | MyRoutes       | ['author']
        '/book/1/author/1'  | HttpMethod.GET    | false     | MyRoutes       | null
        '/book/1'           | HttpMethod.GET    | true      | ResourceRoutes | "book 1"
        '/book'             | HttpMethod.GET    | true      | ResourceRoutes | ['book1']
        '/book/1'           | HttpMethod.PUT    | true      | ResourceRoutes | "updated 1"
        '/book/1'           | HttpMethod.DELETE | true      | ResourceRoutes | "deleted 1"
        '/book/1'           | HttpMethod.PATCH  | true      | ResourceRoutes | "updated 1"
        '/book/1/author'    | HttpMethod.GET    | true      | ResourceRoutes | ['author']
    }

    @Singleton
    static class MyRoutes extends GroovyRouteBuilder {

        MyRoutes(ApplicationContext beanContext) {
            super(beanContext)
        }

        @Inject
        void bookResources(BookController bookController, AuthorController authorController) {
            GET(bookController) {
                POST("/hello{/message}", bookController.&hello)
            }
            GET(bookController, ID) {
                GET(authorController)
            }
        }
    }

    @Singleton
    static class ResourceRoutes extends GroovyRouteBuilder {

        ResourceRoutes(ApplicationContext beanContext) {
            super(beanContext)
        }

        @Inject
        void books(BookController bookController, AuthorController authorController) {
            resources(bookController) {
                single(authorController)
            }
        }
    }

    @Controller
    static class BookController {

        String hello(String message) {
            "Hello $message"
        }

        List index() { ['book1'] }

        String show(String id) {
            "book $id"
        }

        String save() {
            "saved"
        }

        String delete(Long id) {
            "deleted $id"
        }

        String update(Integer id) {
            "updated $id"
        }
    }

    @Controller
    static class AuthorController {
        List index() {
            ["author"]
        }

        String save() {
            "author saved"
        }

        String delete(Long id) {
            "author $id deleted"
        }

        String update(Integer id) {
            "author $id updated"
        }

    }
}
