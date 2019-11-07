package io.micronaut.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.http.server.netty.converters.DuplicateRouteHandler
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.UriRouteMatch
import io.micronaut.web.router.exceptions.DuplicateRouteException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton

class VersionControllerSpec extends Specification {
    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                                 : 'VersionControllerSpec',
            "micronaut.router.versioning.enabled"       : "true",
            "micronaut.router.versioning.header.enabled": "true"
    ])


    @AutoCleanup
    @Shared
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "if I supply a wrong version, then response status should be BAD_REQUEST"() {
        given:
        HttpRequest request = HttpRequest.GET("/versioned/hello").header("X-API-VERSION", "4")

        expect:
        embeddedServer.applicationContext.containsBean(VersionedController)
        embeddedServer.applicationContext.containsBean(DuplicateRouteWithVersionExceptionHandler)

        when:
        client.exchange(request, String)

        then:
        HttpClientResponseException e = thrown()
        e.response.status() == HttpStatus.BAD_REQUEST
    }

    void "if I do not supply a version, and there are multiple versioned routes a DuplicateRouteException is thrown"() {
        given:
        HttpRequest request = HttpRequest.GET("/versioned/hello")

        expect:
        embeddedServer.applicationContext.containsBean(VersionedController)
        embeddedServer.applicationContext.containsBean(DuplicateRouteWithVersionExceptionHandler)

        when:
        client.exchange(request, String)

        then: 'Handled by DuplicateRouteWithVersionExceptionHandler'
        HttpClientResponseException e = thrown()
        e.response.status() == HttpStatus.NOT_ACCEPTABLE
    }

    @Requires(property = "spec.name", value = "VersionControllerSpec")
    @Controller("/versioned")
    static class VersionedController {

        @Produces(MediaType.TEXT_PLAIN)
        @Version("1")
        @Get("/hello")
        String helloV1() {
            "helloV1"
        }

        @Produces(MediaType.TEXT_PLAIN)
        @Version("2")
        @Get("/hello")
        String helloV2() {
            "helloV2"
        }
    }

    @Requires(property = "spec.name", value = "VersionControllerSpec")
    @Produces
    @Singleton
    @Replaces(DuplicateRouteHandler)
    @Requires(classes = [DuplicateRouteException, ExceptionHandler])
    static class DuplicateRouteWithVersionExceptionHandler implements ExceptionHandler<DuplicateRouteException, HttpResponse> {

        @Override
        HttpResponse handle(HttpRequest request, DuplicateRouteException exception) {
            if (areAllUriRoutesAnnotatedWith(exception.getUriRoutes(), Version, String)) {
                return HttpResponse.status(HttpStatus.NOT_ACCEPTABLE)
            }
            JsonError error = new JsonError(exception.getMessage())
            error.link(Link.SELF, Link.of(request.getUri()))
            return HttpResponse.badRequest(error)
        }

        private boolean areAllUriRoutesAnnotatedWith(List<UriRouteMatch<Object, Object>> uriRoutes,
                                                     Class annotationClass,
                                                     Class annotationValue) {
            uriRoutes.every { uriRoute ->
                AnnotationValue versionAnnotationValue = uriRoute.getAnnotation(annotationClass)
                if (versionAnnotationValue == null) {
                    return false
                }
                versionAnnotationValue.getValue(annotationValue).isPresent()
            }
        }
    }

}
