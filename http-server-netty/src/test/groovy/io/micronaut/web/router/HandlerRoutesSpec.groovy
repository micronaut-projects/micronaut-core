package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.util.concurrent.FastThreadLocalThread
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HandlerRoutesSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                          : 'HandlerRoutesSpec',
            'micronaut.server.context-path'      : '/ctx',
            'micronaut.server.thread-selection'  : 'AUTO',
    ])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "handler routes are under the context path"() {
        expect:
        get('/ctx/h/items/5') == 'item 5 java.lang.Long'

        when:
        get('/h/items/5')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    void "a path variable that does not convert is a bad request"() {
        when:
        get('/ctx/h/items/abc')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
    }

    void "an optional path variable"() {
        expect:
        get('/ctx/h/optional') == 'none'
        get('/ctx/h/optional/7') == '7'
    }

    void "typed accessors convert strings and primitives"() {
        expect:
        get('/ctx/h/typed/abc/42/9000000000/1.5/true/x/3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e') ==
                'abc,42,9000000000,1.5,true,x,3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e'
        get('/ctx/h/find/7') == '7,7.0,7,false,false'
    }

    void "a primitive accessor that does not convert is a bad request"() {
        when:
        get('/ctx/h/typed/abc/notanint/1/1.5/true/x/3f2b8c1e-8f0a-4a36-9d4f-2f6f1b3c4d5e')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
    }

    void "route table routes are under the context path"() {
        expect:
        get('/ctx/table/x') == 'table /ctx/table/x'
    }

    void "a route runs on its executor"() {
        expect: 'a handler that would run on the event loop runs on the named executor'
        get('/ctx/h/executor') == 'handler-test-thread'
    }

    void "a non-blocking route runs on the event loop"() {
        expect: 'a blocking handler runs off the event loop unless it is non-blocking'
        get('/ctx/h/blocking') == 'false'
        get('/ctx/h/non-blocking') == 'true'
    }

    private String get(String path) {
        client.toBlocking().retrieve(HttpRequest.GET(path))
    }

    private static HttpResponse<String> text(Object value) {
        HttpResponse.ok(String.valueOf(value)).contentType(MediaType.TEXT_PLAIN_TYPE)
    }

    @Factory
    @Requires(property = 'spec.name', value = 'HandlerRoutesSpec')
    static class Routes {
        @Singleton
        HttpRoutes handlerRoutes() {
            return { RouteBuilder routes ->
                routes.GET('/h/items/{id}', { HttpRequest<?> request ->
                    Long id = PathVariables.of(request).get('id', Long)
                    text("item $id ${id.class.name}")
                } as RequestHandler)
                routes.GET('/h/optional{/id}', { HttpRequest<?> request ->
                    OptionalInt id = PathVariables.of(request).findInt('id')
                    text(id.present ? String.valueOf(id.asInt) : 'none')
                } as RequestHandler)
                routes.GET('/h/typed/{s}/{i}/{l}/{d}/{b}/{c}/{u}', { HttpRequest<?> request ->
                    PathVariables vars = PathVariables.of(request)
                    text([vars.getString('s'), vars.getInt('i'), vars.getLong('l'), vars.getDouble('d'),
                          vars.getBoolean('b'), vars.getChar('c'), vars.get('u', UUID)].join(','))
                } as RequestHandler)
                routes.GET('/h/find/{l}', { HttpRequest<?> request ->
                    PathVariables vars = PathVariables.of(request)
                    text([vars.findLong('l').asLong, vars.findDouble('l').asDouble, vars.findString('l').get(),
                          vars.findInt('missing').present, vars.findBoolean('missing').present].join(','))
                } as RequestHandler)
                routes.handleAsync(io.micronaut.http.HttpMethod.GET, '/h/executor', { HttpRequest<?> request ->
                    CompletableFuture.completedFuture(text(Thread.currentThread().name))
                } as AsyncRequestHandler).executeOn('handler-test')
                routes.GET('/h/blocking', { HttpRequest<?> request ->
                    text(Thread.currentThread() instanceof FastThreadLocalThread)
                } as RequestHandler)
                routes.GET('/h/non-blocking', { HttpRequest<?> request ->
                    text(Thread.currentThread() instanceof FastThreadLocalThread)
                } as RequestHandler).nonBlocking()
            } as HttpRoutes
        }

        @Singleton
        @Named('handler-test')
        ExecutorService handlerTestExecutor() {
            return Executors.newSingleThreadExecutor { Runnable r -> new Thread(r, 'handler-test-thread') }
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'HandlerRoutesSpec')
    static class TableRoutes implements RouteSource {
        private final RouteTable table

        TableRoutes(RouteTableFactory tables) {
            table = tables.build { RouteBuilder routes ->
                routes.GET('/table/{+path}', { HttpRequest<?> request -> text("table ${request.path}") } as RequestHandler)
            }
        }

        @Override
        RouteTable snapshot() {
            return table
        }
    }
}
