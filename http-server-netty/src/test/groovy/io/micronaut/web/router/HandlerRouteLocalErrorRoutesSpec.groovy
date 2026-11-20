package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.PathVariables
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.RequestHandler
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The local error routes of a controller handle the errors of its routes only: not the errors of
 * a route to a handler function, which are handled by the global error routes.
 */
class HandlerRouteLocalErrorRoutesSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'HandlerRouteLocalErrorRoutesSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the local error route of a controller handles the errors of its routes"() {
        expect:
        client.toBlocking().retrieve('/local-errors/local') == 'local'
        client.toBlocking().retrieve('/local-errors/global') == 'global'
    }

    void "the global error route handles the errors of a handler route"() {
        expect:
        client.toBlocking().retrieve('/handler-errors/global') == 'global'
    }

    void "the local error route of a controller does not handle the errors of a handler route"() {
        when:
        client.toBlocking().retrieve('/handler-errors/local')

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
    }

    static class LocalException extends RuntimeException {
    }

    static class GlobalException extends RuntimeException {
    }

    @Requires(property = 'spec.name', value = 'HandlerRouteLocalErrorRoutesSpec')
    @Controller('/local-errors')
    static class LocalErrorsController {

        @Get('/local')
        String local() {
            throw new LocalException()
        }

        @Get('/global')
        String global() {
            throw new GlobalException()
        }

        @Error(LocalException)
        HttpResponse<String> localError() {
            HttpResponse.ok('local')
        }

        @Error(exception = GlobalException, global = true)
        HttpResponse<String> globalError() {
            HttpResponse.ok('global')
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'HandlerRouteLocalErrorRoutesSpec')
    static class Routes {

        @Singleton
        HttpRoutes handlerErrorRoutes() {
            { HttpRouteBuilder routes ->
                routes.GET('/handler-errors/local', { HttpRequest<?> request, PathVariables pathVariables ->
                    throw new LocalException()
                } as RequestHandler)
                routes.GET('/handler-errors/global', { HttpRequest<?> request, PathVariables pathVariables ->
                    throw new GlobalException()
                } as RequestHandler)
            } as HttpRoutes
        }
    }
}
