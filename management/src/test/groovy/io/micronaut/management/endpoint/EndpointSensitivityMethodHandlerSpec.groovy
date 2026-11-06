package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.PathVariables
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.inject.ExecutableMethod
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.RequestHandler
import io.micronaut.web.router.builder.RouteMethodProvider
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A handler route that implements the method of an endpoint, see {@link RouteMethodProvider}, is
 * as sensitive as the endpoint method: the route binds the arguments of the handler, so the
 * filter finds the endpoint method through the element of the route.
 */
class EndpointSensitivityMethodHandlerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'EndpointSensitivityMethodHandlerSpec'], Environment.TEST)

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "a handler that implements a sensitive endpoint method is unauthorized like the endpoint"() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        client.exchange("/handled/sensitive", String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNAUTHORIZED

        when:
        client.exchange("/sensitivehandled", String)

        then:
        HttpClientResponseException endpoint = thrown()
        endpoint.status == HttpStatus.UNAUTHORIZED
    }

    void "a handler that implements an endpoint method that is not sensitive answers"() {
        expect:
        httpClient.toBlocking().retrieve("/handled/open") == "handled"
        httpClient.toBlocking().retrieve("/openhandled") == "open"
    }

    void "a handler without a method answers"() {
        expect:
        httpClient.toBlocking().retrieve("/handled/lambda") == "lambda"
    }

    @Endpoint(id = "sensitivehandled", defaultSensitive = true)
    @Requires(property = 'spec.name', value = 'EndpointSensitivityMethodHandlerSpec')
    static class SensitiveEndpoint {
        @Read
        String status() {
            return "sensitive"
        }
    }

    @Endpoint(id = "openhandled", defaultSensitive = false)
    @Requires(property = 'spec.name', value = 'EndpointSensitivityMethodHandlerSpec')
    static class OpenEndpoint {
        @Read
        String status() {
            return "open"
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'EndpointSensitivityMethodHandlerSpec')
    static class Routes implements HttpRoutes {
        private final BeanContext beanContext

        Routes(BeanContext beanContext) {
            this.beanContext = beanContext
        }

        @Override
        void routes(HttpRouteBuilder routes) {
            routes.GET("/handled/sensitive", new MethodHandler(beanContext.getBeanDefinition(SensitiveEndpoint).getRequiredMethod("status")))
            routes.GET("/handled/open", new MethodHandler(beanContext.getBeanDefinition(OpenEndpoint).getRequiredMethod("status")))
            routes.GET("/handled/lambda", { HttpRequest<?> request, PathVariables pathVariables -> HttpResponse.ok("lambda") } as RequestHandler)
        }
    }

    static class MethodHandler implements RequestHandler, RouteMethodProvider {
        private final ExecutableMethod<?, ?> method

        MethodHandler(ExecutableMethod<?, ?> method) {
            this.method = method
        }

        @Override
        HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return HttpResponse.ok("handled")
        }

        @Override
        ExecutableMethod<?, ?> routeMethod() {
            return method
        }
    }
}
