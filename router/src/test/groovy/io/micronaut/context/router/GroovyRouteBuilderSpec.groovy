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
package io.micronaut.context.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Error
import io.micronaut.web.router.GroovyRouteBuilder
import io.micronaut.web.router.RouteMatch
import io.micronaut.web.router.Router
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyRouteBuilderSpec extends Specification {

    @Unroll
    void "Test uri #method #uri matches route for routes #routeBean.name"() {

        when:
        def context = new DefaultApplicationContext("test").start()
        Router router = context.getBean(Router)

        Optional<RouteMatch> route = router."${method}"(uri)

        then:
        route.isPresent() == isPresent
        route.isPresent() ? route.get().invoke() : null == result

        cleanup:
        context.stop()

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

    void "test the correct local error route is returned"() {
        given:
        def context = new DefaultApplicationContext("test").start()
        Router router = context.getBean(Router)

        expect:
        router.route(ErrorHandlingController, new A()).get().execute() == "c"
        router.route(ErrorHandlingController, new B()).get().execute() == "c"
        router.route(ErrorHandlingController, new C()).get().execute() == "c"
        router.route(ErrorHandlingController, new D()).get().execute() == "e"
        router.route(ErrorHandlingController, new E()).get().execute() == "e"

        cleanup:
        context.stop()
    }

    // tag::routes[]
    @Singleton
    static class MyRoutes extends GroovyRouteBuilder {

        MyRoutes(ApplicationContext beanContext) {
            super(beanContext)
        }

        @Inject
        void bookResources(BookController bookController, AuthorController authorController) {
            GET(bookController) {
                POST("/hello{/message}", bookController.&hello) // <1>
            }
            GET(bookController, ID) { // <2>
                GET(authorController)
            }
        }
    }
    // end::routes[]

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

    @Controller('/book')
    @Executable
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

    @Controller('/author')
    @Executable
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

    @Controller('/error-handling')
    @Executable
    static class ErrorHandlingController {

        @Get('/throws-a')
        String throwsA() { throw new A() }
        @Get('/throws-b')
        String throwsB() { throw new B() }
        @Get('/throws-c')
        String throwsC() { throw new C() }
        @Get('/throws-d')
        String throwsD() { throw new D() }
        @Get('/throws-e')
        String throwsE() { throw new E() }

        @Error
        String handleC(C c) {
            "c"
        }

        @Error
        String handleE(E e) {
            "e"
        }
    }

    static class A extends B {}
    static class B extends C {}
    static class C extends D {}
    static class D extends E {}
    static class E extends RuntimeException {}

    def 'content negotiation on accept header'() {
        given:
        def context = new DefaultApplicationContext("test").start()
        Router router = context.getBean(Router)

        def request = HttpRequest.GET('/accept').header('Accept', accept)

        expect:
        router.findAllClosest(request).collectMany { it.produces }.collect { it.toString() }.toSet() == produced.toSet()

        cleanup:
        context.stop()

        where:
        accept                     | produced
        'application/json'         | ['application/json']
        'application/*'            | ['application/json']
        'text/*'                   | ['text/plain', 'text/html']
        // "If more than one media range applies to a given type, the most specific reference has precedence."
        'text/*, text/plain'       | ['text/plain']
        'text/*, text/plain;q=0.5' | ['text/html']
        'text/*;q=0.5, */*'        | ['image/svg', 'application/json']
    }

    def 'content negotiation on accept header: wildcard'() {
        given:
        def context = new DefaultApplicationContext("test").start()
        Router router = context.getBean(Router)

        def request = HttpRequest.GET('/accept/wildcard').header('Accept', accept)

        expect:
        router.findAllClosest(request).collectMany { it.produces }.collect { it.toString() }.toSet() == produced.toSet()

        cleanup:
        context.stop()

        where:
        accept             | produced
        'application/json' | ['application/json', '*/*']
        'application/xml'  | ['application/xml']
        'application/*'    | ['application/json', '*/*', 'application/xml']
        '*/*'              | ['application/json', '*/*']
    }

    @Controller('/accept')
    @Executable
    static class AcceptController {
        @Get(produces = 'application/json')
        def json() {}

        @Get(produces = 'text/html')
        def html() {}

        @Get(produces = 'text/plain')
        def plain() {}

        @Get(produces = 'image/svg')
        def svg() {}

        @Get(value = '/wildcard', produces = ['application/json', '*/*'])
        def wildcardProducesJson() {}

        @Get(value = '/wildcard', produces = ['application/xml'])
        def wildcardProducesXml() {}
    }

}
