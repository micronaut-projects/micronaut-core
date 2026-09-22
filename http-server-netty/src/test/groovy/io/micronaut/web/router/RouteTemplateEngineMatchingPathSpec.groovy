package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.http.PathVariables
import io.micronaut.web.router.builder.RequestHandler
import io.micronaut.web.router.builder.RouteDeclaration
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * An engine whose routes ignore matrix parameters: the routes match the path without them, and
 * the handler still sees the raw URI of the request.
 */
class RouteTemplateEngineMatchingPathSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'RouteTemplateEngineMatchingPathSpec',
    ])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "matrix parameters do not affect matching and the handler sees the raw URI"() {
        expect:
        get('/cars;color=red/7;trim=gt/details') == '7 /cars;color=red/7;trim=gt/details'
        get('/cars/7/details') == '7 /cars/7/details'
    }

    void "a Micronaut route of the same server is matched with the raw path"() {
        expect:
        get('/m/items/5') == 'micronaut 5'

        when:
        get('/m/items;v=1/5')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    void "the allowed methods of a path with matrix parameters are found"() {
        when:
        client.toBlocking().exchange(HttpRequest.DELETE('/cars;color=red/7/details'))

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.METHOD_NOT_ALLOWED
    }

    private String get(String path) {
        client.toBlocking().retrieve(HttpRequest.GET(path))
    }

    private static HttpResponse<String> text(Object value) {
        HttpResponse.ok(String.valueOf(value)).contentType(MediaType.TEXT_PLAIN_TYPE)
    }

    @Factory
    @Requires(property = 'spec.name', value = 'RouteTemplateEngineMatchingPathSpec')
    static class Routes {
        @Singleton
        HttpRoutes matrixRoutes() {
            return { HttpRouteBuilder routes ->
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestMatrixColonRouteTemplateEngine.template('/cars/:id/details')), { HttpRequest<?> request, PathVariables pathVariables ->
                    text("${pathVariables.getString('id')} ${request.uri}")
                } as RequestHandler)
                routes.GET('/m/items/{id}', { HttpRequest<?> request, PathVariables pathVariables ->
                    text("micronaut ${pathVariables.getString('id')}")
                } as RequestHandler)
            } as HttpRoutes
        }
    }
}
