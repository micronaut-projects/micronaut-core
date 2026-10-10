package io.micronaut.http.server

import groovy.transform.CompileStatic
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.web.router.AssembledRoutes
import io.micronaut.web.router.DefaultRouter
import io.micronaut.web.router.RouteAssembly
import io.micronaut.web.router.Router
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder
import io.micronaut.web.router.builder.HttpRouteBuilder
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.UnaryOperator

/**
 * The route builder without micronaut-websocket, which the router has as an optional dependency
 * for the WebSocket routes: Groovy reflects on the generic signatures of the route specs, at
 * runtime and when it compiles statically, so they must not have the types of micronaut-websocket.
 */
class HttpRoutesWithoutWebSocketSpec extends Specification {

    void "micronaut-websocket is not on the classpath of this module"() {
        when:
        Class.forName('io.micronaut.websocket.context.WebSocketBean')

        then:
        thrown(ClassNotFoundException)
    }

    void "dynamic Groovy declares routes"() {
        given:
        RouteAssembly assembly = assembly()
        def routes = new DefaultHttpRouteBuilder(assembly)

        when:
        routes.GET('/dynamic').respond(HttpResponse.ok('dynamic'))
        Router router = router(assembly)

        then:
        router.findClosest(HttpRequest.GET('/dynamic')) != null
    }

    void "statically compiled Groovy declares routes"() {
        given:
        RouteAssembly assembly = assembly()

        when:
        StaticRoutes.declare(new DefaultHttpRouteBuilder(assembly))
        Router router = router(assembly)

        then:
        router.findClosest(HttpRequest.GET('/static')) != null
    }

    void "a WebSocket route requires micronaut-websocket"() {
        given:
        RouteAssembly assembly = assembly()
        def routes = new DefaultHttpRouteBuilder(assembly)

        when:
        routes.GET('/ws').webSocket { }

        then:
        IllegalStateException e = thrown()
        e.message == 'The WebSocket route GET /ws requires micronaut-websocket'

        when: 'the route was dropped'
        routes.GET('/plain').respond(HttpResponse.ok())
        Router router = router(assembly)

        then:
        router.findClosest(HttpRequest.GET('/ws')) == null
        router.findClosest(HttpRequest.GET('/plain')) != null
    }

    private static RouteAssembly assembly() {
        new RouteAssembly(null, ConversionService.SHARED, { String uri -> uri } as UnaryOperator<String>, { route -> } as Consumer)
    }

    private static Router router(RouteAssembly assembly) {
        new DefaultRouter([], [{ -> assembly } as AssembledRoutes])
    }

    @CompileStatic
    static class StaticRoutes {
        static void declare(HttpRouteBuilder routes) {
            routes.GET('/static').respond(HttpResponse.ok('static'))
        }
    }
}
