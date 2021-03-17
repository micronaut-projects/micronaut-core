package io.micronaut.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.UriRouteMatch
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton
import java.util.function.Predicate

class RouteVersionFilterReplacementSpec extends Specification {
    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                                 : 'RouteVersionFilterReplacementSpec',
            "micronaut.router.versioning.enabled"       : "true",
            "micronaut.router.versioning.header.enabled": "true"
    ])


    @AutoCleanup
    @Shared
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "if I supply a wrong version, then response status should be NOT_FOUND"() {
        given:
        HttpRequest request = HttpRequest.GET("/versioned/hello").header("X-API-VERSION", "4")

        when:
        client.exchange(request, String)

        then:
        noExceptionThrown()
    }

    @Requires(property = "spec.name", value = "RouteVersionFilterReplacementSpec")
    @Controller("/versioned")
    static class VersionedController {

        @Produces(MediaType.TEXT_PLAIN)
        @Version("1")
        @Get("/hello")
        String helloV1() {
            "helloV1"
        }
    }

    @Requires(property = "spec.name", value = "RouteVersionFilterReplacementSpec")
    @Singleton
    @Requires(beans = RoutesVersioningConfiguration.class)
    @Replaces(bean = RouteVersionFilter.class)
    static class RouteVersionFilterReplacement implements VersionRouteMatchFilter {

        @Override
        <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request) {
            return { match -> true }
        }
    }

}
