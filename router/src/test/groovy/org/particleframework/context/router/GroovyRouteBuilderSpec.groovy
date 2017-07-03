/*
 * Copyright 2017 original authors
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
package org.particleframework.context.router

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.particleframework.context.BeanContext
import org.particleframework.context.router.RouteBuilderTests.AuthorController
import org.particleframework.context.router.RouteBuilderTests.BookController
import org.particleframework.http.HttpMethod
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Inject

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyRouteBuilderSpec extends Specification {

    @Unroll
    void "Test uri #method #uri matches route"() {
        given:
        MyRoutes routes = new MyRoutes(Mock(BeanContext))
        routes."$routesMethod"(new BookController(), new AuthorController())
        def result = routes.builtRoutes.find() { it.match(uri).isPresent() && it.httpMethod == method }
        Optional route = result != null ? Optional.of(result) : Optional.empty()

        expect:
        route.isPresent() == isPresent

        where:
        uri                | method            | isPresent | routesMethod
        '/book'            | HttpMethod.GET    | true      | "books"
        '/book/hello'      | HttpMethod.POST   | true      | "books"
        '/bo'              | HttpMethod.GET    | false     | "books"
        '/book'            | HttpMethod.POST   | false     | "books"
        '/book/1'          | HttpMethod.GET    | true      | "books"
        '/book/1/author'   | HttpMethod.GET    | true      | "books"
        '/book/1/author/1' | HttpMethod.GET    | false     | "books"
        '/book/1'          | HttpMethod.GET    | true      | "bookResources"
        '/book'            | HttpMethod.GET    | true      | "bookResources"
        '/book/1'          | HttpMethod.PUT    | true      | "bookResources"
        '/book/1'          | HttpMethod.DELETE | true      | "bookResources"
        '/book/1'          | HttpMethod.PATCH  | true      | "bookResources"
        '/book/1/author'   | HttpMethod.GET    | true      | "bookResources"
    }

    @InheritConstructors
    static class MyRoutes extends GroovyRouteBuilder {

        @Inject
        void books(BookController bookController, AuthorController authorController) {
            GET(bookController) {
                POST("/hello", bookController.&hello)
            }
            GET(bookController, ID) {
                GET(authorController)
            }
        }

        @Inject
        void bookResources(BookController bookController, AuthorController authorController) {
            resources(bookController) {
                single(authorController)
            }
        }
    }
}
