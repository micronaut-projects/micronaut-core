/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.router;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import org.junit.Test;
import io.micronaut.web.router.*;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.List;

import static org.junit.Assert.*;
/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class RouteBuilderTests {

    @Test
    public void testRouterBuilder() {
        ApplicationContext beanContext = new DefaultApplicationContext("test").start();
        beanContext.registerSingleton(new BookController())
                    .registerSingleton(new AuthorController());
        MyRouteBuilder routeBuilder = new MyRouteBuilder(beanContext);
        routeBuilder.someRoutes(new BookController(), new AuthorController());
        List<UriRoute> builtRoutes = routeBuilder.getUriRoutes();
        Router router = new DefaultRouter(routeBuilder);

        // test invoking routes
        assertTrue(router.GET("/books/1").isPresent());

        assertEquals("Hello World", router.GET("/message/World").get().invoke());
        assertEquals("Book 1", router.GET("/books/1").get().invoke());
        assertEquals("not found", ((MethodBasedRouteMatch) router.route(HttpStatus.NOT_FOUND).get()).invoke());
        assertEquals("class not found: error", ((MethodBasedRouteMatch) router.route(new ClassNotFoundException("error")).get()).invoke());

        // test route state

        assertTrue(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/books/1/authors").isPresent() && route.getHttpMethod() == HttpMethod.GET)
        );
        assertTrue(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/books").isPresent() && route.getHttpMethod() == HttpMethod.POST)
        );
        assertTrue(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/book").isPresent() && route.getHttpMethod() == HttpMethod.POST)
        );
        assertFalse(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/boo").isPresent() && route.getHttpMethod() == HttpMethod.POST)
        );
        assertTrue(builtRoutes
                .stream()
                .anyMatch(route ->
                        route.match("/book/1").isPresent() && route.getHttpMethod() == HttpMethod.GET)
        );
    }

    static class MyRouteBuilder extends DefaultRouteBuilder {
        public MyRouteBuilder(ApplicationContext beanContext) {
            super(beanContext);
        }

        @Inject
        void someRoutes(BookController controller, AuthorController authorController) {
            GET("/conditional{/message}", controller, "hello", String.class)
                    .where((request)->
                            request.getContentType().map(type->type.equals(MediaType.APPLICATION_JSON_TYPE)).orElse(false)
                    );

            GET("/message{/message}", controller, "hello", String.class).consumes(MediaType.APPLICATION_JSON_TYPE);
            GET("/books{/id}", controller, "show", Long.class).nest(() ->
                    GET("/authors", controller)
            );
            GET(controller);
            POST(controller);
            PUT(controller, ID);
            GET(controller, ID);
            GET(BookController.class, ID);
//            GET(Book.class, ID);
            GET(controller, ID).nest(() ->
                    GET(authorController)
            );

            GET("/books", controller);
            POST("/books", controller, "save").consumes(MediaType.APPLICATION_JSON_TYPE);

            // handle errors TODO
            error(ClassNotFoundException.class, controller, "classNotFound");
            error(ReflectiveOperationException.class, controller);
            // handle status codes
            status(HttpStatus.NOT_FOUND, controller, "notFound");

            // REST resources
            resources(controller);
//            resources(Book.class);
//            single(Book.class).nest(()->
//                resources(Author.class)
//            );
        }
    }

    @Singleton
    @Executable
    static class BookController {
        String hello(String message) {
            return "Hello " + message;
        }

        String show(Long id) { return "Book " + id; }
        String index() { return "dummy"; }
        String save() { return "dummy"; }
        String delete(Long id) { return "dummy"; }
        String update(Long id) { return "dummy"; }

        String notFound() { return "not found";}

        String classNotFound(ClassNotFoundException e) {
            return "class not found: " + e.getMessage();
        }

        String reflectiveOperation(ReflectiveOperationException e) {
            return "reflect exception";
        }
    }

    @Singleton
    @Executable
    static class AuthorController {
        String hello(String message) {
            return "Hello " + message;
        }

        String show() { return "dummy"; }
        String index() { return "dummy"; }
        String save() { return "dummy"; }
        String delete() { return "dummy"; }
        String update() { return "dummy"; }

    }

    static class Author {}
    static class Book {}
}
