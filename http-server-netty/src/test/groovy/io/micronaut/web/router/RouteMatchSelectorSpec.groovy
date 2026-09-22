package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
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
import spock.lang.Unroll

import java.net.http.HttpClient
import java.net.http.HttpResponse as JdkResponse

/**
 * The route selector of a route template engine selects among its routes by the media types, and
 * the media type it negotiates reaches the handler and the response.
 */
class RouteMatchSelectorSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'RouteMatchSelectorSpec'])

    @Shared
    HttpClient client = HttpClient.newHttpClient()

    @Unroll
    void "the selector picks the route by the accepted type #accept"() {
        when:
        JdkResponse<String> response = get('/neg/items/5', accept)

        then:
        response.statusCode() == 200
        response.headers().firstValue('Content-Type').orElse(null).startsWith(contentType)
        response.body().contains(body)

        where:
        accept                                  | contentType        | body
        'text/plain'                            | 'text/plain'       | 'text 5 text/plain'
        'application/json'                      | 'application/json' | '"selected":"application/json"'
        'text/plain;q=0.5, application/json'    | 'application/json' | '"selected":"application/json"'
        null                                    | 'text/plain'       | 'text 5 text/plain'
    }

    void "the negotiated type of a single route is the content type of the response"() {
        when:
        JdkResponse<String> selected = get('/neg/single/5', 'text/html;q=0.9, text/plain;q=0.5')
        JdkResponse<String> micronaut = get('/nat/single/5', 'text/html;q=0.9, text/plain;q=0.5')

        then: 'the selector negotiates the accepted type the route produces'
        selected.statusCode() == 200
        selected.headers().firstValue('Content-Type').get().startsWith('text/plain')
        selected.body() == 'single'

        and: 'a Micronaut route is unchanged: the first type it produces'
        micronaut.statusCode() == 200
        micronaut.headers().firstValue('Content-Type').get().startsWith('application/json')
    }

    void "a handler that sets the content type keeps it"() {
        when:
        JdkResponse<String> response = get('/neg/explicit/5', 'text/plain')

        then:
        response.statusCode() == 200
        response.headers().firstValue('Content-Type').get().startsWith('text/csv')
    }

    void "a type no route produces is not acceptable"() {
        expect:
        get('/neg/items/5', 'image/png').statusCode() == 406
    }

    private JdkResponse<String> get(String path, String accept) {
        def builder = java.net.http.HttpRequest.newBuilder(URI.create("${server.URL}${path}")).GET()
        if (accept != null) {
            builder.header('Accept', accept)
        }
        client.send(builder.build(), JdkResponse.BodyHandlers.ofString())
    }

    @Factory
    @Requires(property = 'spec.name', value = 'RouteMatchSelectorSpec')
    static class Routes {
        @Singleton
        HttpRoutes selectedRoutes() {
            return { HttpRouteBuilder routes ->
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestSelectingColonRouteTemplateEngine.template('/neg/items/:id')), { HttpRequest<?> request, PathVariables pathVariables ->
                    HttpResponse.ok("text ${pathVariables.getString('id')} ${pathVariables.selectedMediaType()}".toString())
                } as RequestHandler).produces(MediaType.TEXT_PLAIN_TYPE)
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestSelectingColonRouteTemplateEngine.template('/neg/items/:id')), { HttpRequest<?> request, PathVariables pathVariables ->
                    HttpResponse.ok([selected: pathVariables.selectedMediaType().toString()])
                } as RequestHandler).produces(MediaType.APPLICATION_JSON_TYPE)
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestSelectingColonRouteTemplateEngine.template('/neg/single/:id')), { HttpRequest<?> request, PathVariables pathVariables ->
                    HttpResponse.ok('single')
                } as RequestHandler).produces(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_PLAIN_TYPE)
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestSelectingColonRouteTemplateEngine.template('/neg/explicit/:id')), { HttpRequest<?> request, PathVariables pathVariables ->
                    HttpResponse.ok('a,b').contentType(MediaType.of('text/csv'))
                } as RequestHandler).produces(MediaType.TEXT_PLAIN_TYPE)
                routes.GET('/nat/single/{id}', { HttpRequest<?> request, PathVariables pathVariables ->
                    HttpResponse.ok('single')
                } as RequestHandler).produces(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_PLAIN_TYPE)
            } as HttpRoutes
        }
    }
}
