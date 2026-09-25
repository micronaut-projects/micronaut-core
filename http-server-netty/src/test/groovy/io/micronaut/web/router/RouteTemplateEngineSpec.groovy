package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.BasicHttpAttributes
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.RouteTemplate
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.PathVariables
import io.micronaut.web.router.builder.RequestHandler
import io.micronaut.web.router.builder.RouteDeclaration
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Handler routes of a second route template engine, registered for the tests only, served next to
 * Micronaut routes under a context path.
 */
class RouteTemplateEngineSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                    : 'RouteTemplateEngineSpec',
            'micronaut.server.context-path': '/ctx',
    ])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a route of another engine is served under the context path"() {
        expect:
        get('/ctx/c/items/5') == 'colon 5 /ctx/c/items/:id'
        get('/ctx/c/items/5/') == 'colon 5 /ctx/c/items/:id'

        when:
        get('/c/items/5')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    void "a route of another engine has an implicit HEAD route"() {
        expect:
        client.toBlocking().exchange(HttpRequest.HEAD('/ctx/c/items/5')).status() == HttpStatus.OK
    }

    void "the more specific route wins across engines"() {
        expect:
        get('/ctx/c/items/special') == 'micronaut special'
        get('/ctx/c/items/7') == 'colon 7 /ctx/c/items/:id'
    }

    void "a Micronaut route keeps its meaning of the modifier"() {
        expect:
        get('/ctx/m/items/abc') == 'micronaut abc'

        when: 'the Micronaut modifier limits the length'
        get('/ctx/m/items/abcd')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    void "the router lists the routes of both engines by their templates"() {
        given:
        Router router = server.applicationContext.getBean(Router)

        expect:
        router.uriRoutes().anyMatch { it.routeTemplate == RouteTemplate.of(TestColonRouteTemplateEngine.ID, '/ctx/c/items/:id') }
        router.uriRoutes().anyMatch { it.routeTemplate == RouteTemplate.micronaut('/ctx/m/items/{id:3}') }
    }

    private String get(String path) {
        client.toBlocking().retrieve(HttpRequest.GET(path))
    }

    private static HttpResponse<String> text(Object value) {
        HttpResponse.ok(String.valueOf(value)).contentType(MediaType.TEXT_PLAIN_TYPE)
    }

    @Factory
    @Requires(property = 'spec.name', value = 'RouteTemplateEngineSpec')
    static class Routes {
        @Singleton
        HttpRoutes engineRoutes() {
            return { HttpRouteBuilder routes ->
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestColonRouteTemplateEngine.template('/c/items/:id')), { HttpRequest<?> request, PathVariables pathVariables ->
                    text("colon ${pathVariables.getString('id')} ${BasicHttpAttributes.getUriTemplate(request).orElse('none')}")
                } as RequestHandler)
                routes.GET('/c/items/special', { HttpRequest<?> request, PathVariables pathVariables ->
                    text('micronaut special')
                } as RequestHandler)
                routes.GET('/m/items/{id:3}', { HttpRequest<?> request, PathVariables pathVariables ->
                    text("micronaut ${pathVariables.getString('id')}")
                } as RequestHandler)
            } as HttpRoutes
        }
    }
}
