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

class NoVersionOnVersionedRouteControllerSpec extends Specification {
    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                          : 'NoVersionOnVersionedRouteControllerSpec',
            "micronaut.router.versioning.enabled": "true"
    ])


    @AutoCleanup
    @Shared
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "if I do not supply a version, and there is one versioned route"() {
        given:
        HttpRequest request = HttpRequest.GET("/versioned/hello")

        expect:
        embeddedServer.applicationContext.containsBean(VersionedController)

        when:
        client.exchange(request, String)

        then:
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.BAD_REQUEST
    }

    @Requires(property = "spec.name", value = "NoVersionOnVersionedRouteControllerSpec")
    @Controller("/versioned")
    static class VersionedController {

        @Produces(MediaType.TEXT_PLAIN)
        @Version("1")
        @Get("/hello")
        String helloV1() {
            "helloV1"
        }
    }

}
