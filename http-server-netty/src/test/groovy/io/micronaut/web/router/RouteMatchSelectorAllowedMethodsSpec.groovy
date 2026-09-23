package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
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
import spock.lang.Unroll

import java.net.http.HttpClient
import java.net.http.HttpResponse as JdkResponse

/**
 * A route the route selector of its engine does not select is not found, and its method is not
 * allowed: the {@code 405} status and the {@code Allow} header count only the selected routes.
 */
class RouteMatchSelectorAllowedMethodsSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'RouteMatchSelectorAllowedMethodsSpec'])

    @Shared
    HttpClient client = HttpClient.newHttpClient()

    @Unroll
    void "#method #path answers #status with Allow #allow"() {
        when:
        JdkResponse<String> response = send(method, path)

        then:
        response.statusCode() == status
        allowed(response) == allow as Set

        where:
        method   | path                | status | allow
        'GET'    | '/allow/rejected/1' | 404    | []
        'HEAD'   | '/allow/rejected/1' | 404    | []
        'POST'   | '/allow/rejected/1' | 404    | []
        'GET'    | '/allow/kept/1'     | 405    | ['POST']
        'POST'   | '/allow/kept/1'     | 200    | []
        'GET'    | '/allow/mixed/1'    | 405    | ['POST', 'DELETE']
        'PUT'    | '/allow/mixed/1'    | 405    | ['POST', 'DELETE']
        'DELETE' | '/allow/mixed/1'    | 200    | []
        'GET'    | '/allow/native/1'   | 405    | ['POST']
    }

    private static Set<String> allowed(JdkResponse<String> response) {
        response.headers().allValues('Allow').collectMany { it.split(',').collect { it.trim() } }.findAll { it } as Set
    }

    private JdkResponse<String> send(String method, String path) {
        def body = method in ['POST', 'PUT'] ? java.net.http.HttpRequest.BodyPublishers.ofString('x') : java.net.http.HttpRequest.BodyPublishers.noBody()
        def request = java.net.http.HttpRequest.newBuilder(URI.create("${server.URL}${path}"))
            .method(method, body)
            .build()
        client.send(request, JdkResponse.BodyHandlers.ofString())
    }

    @Factory
    @Requires(property = 'spec.name', value = 'RouteMatchSelectorAllowedMethodsSpec')
    static class Routes {
        @Singleton
        HttpRoutes allowedRoutes() {
            RequestHandler ok = { HttpRequest<?> request, PathVariables pathVariables -> HttpResponse.ok('ok') } as RequestHandler
            return { HttpRouteBuilder routes ->
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestSelectingColonRouteTemplateEngine.template('/allow/rejected/:rejected')), ok)
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestSelectingColonRouteTemplateEngine.template('/allow/kept/:rejected')), ok)
                routes.handle(RouteDeclaration.of(HttpMethod.POST, TestSelectingColonRouteTemplateEngine.template('/allow/kept/:id')), ok)
                routes.handle(RouteDeclaration.of(HttpMethod.GET, TestSelectingColonRouteTemplateEngine.template('/allow/mixed/:rejected')), ok)
                routes.handle(RouteDeclaration.of(HttpMethod.POST, TestSelectingColonRouteTemplateEngine.template('/allow/mixed/:id')), ok)
                routes.handle(RouteDeclaration.of(HttpMethod.PUT, TestSelectingColonRouteTemplateEngine.template('/allow/mixed/:rejected')), ok)
                routes.DELETE('/allow/mixed/{id}', ok)
                routes.POST('/allow/native/{id}', ok)
            } as HttpRoutes
        }
    }
}
