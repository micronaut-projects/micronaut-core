package io.micronaut.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Unroll

class VersioningMatchSpec extends VersioningSpec {

    @Unroll("#description")
    void "versioning matching scenarios"(boolean versioning,
                                         String defaultVersion,
                                         RouteVersioning routeVersioning,
                                         String requestVersion,
                                         String expected,
                                         String description) {
        given:
        Map<String, Object> configuration = getConfiguration('VersioningMatchSpec', versioning, defaultVersion)
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, configuration)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        HttpRequest<?> request = createRequest(routeVersioning, requestVersion)
        String rsp = client.retrieve(request, String)

        then:
        expected == rsp

        cleanup:
        client.close()
        httpClient.close()
        embeddedServer.close()

        where:
        versioning | defaultVersion | routeVersioning        | requestVersion | expected
        false      | null           | RouteVersioning.SINGLE | null           | 'Hello World'
        false      | null           | RouteVersioning.SINGLE | '1.0'          | 'Hello World'
        false      | null           | RouteVersioning.NONE   | null           | 'Hello World'
        false      | null           | RouteVersioning.NONE   | '1.0'          | 'Hello World'
        true       | null           | RouteVersioning.SINGLE | null           | 'Hello World'
        true       | '1.0'          | RouteVersioning.SINGLE | null           | 'Hello World'
        true       | null           | RouteVersioning.SINGLE | '1.0'          | 'Hello World'
        true       | null           | RouteVersioning.NONE   | null           | 'Hello World'
        true       | '2.0'          | RouteVersioning.MULTI  | null           | 'Hello World'
        true       | null           | RouteVersioning.MULTI  | '1.0'          | 'Hello Moon'
        true       | null           | RouteVersioning.MULTI  | '2.0'          | 'Hello World'
        description = createDescription(true, versioning, defaultVersion, routeVersioning, requestVersion)
    }

    @Requires(property = 'spec.name', value = 'VersioningMatchSpec')
    @Controller("/multi")
    static class CustomMultiController extends VersioningSpec.MultiController {}

    @Requires(property = 'spec.name', value = 'VersioningMatchSpec')
    @Controller("/single")
    static class CustomSingleController extends VersioningSpec.SingleController {}

    @Requires(property = 'spec.name', value = 'VersioningMatchSpec')
    @Controller("/none")
    static class CustomNoneController extends VersioningSpec.NoneController {}
}