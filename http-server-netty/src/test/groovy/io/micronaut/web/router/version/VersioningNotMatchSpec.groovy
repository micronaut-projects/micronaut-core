package io.micronaut.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Unroll

class VersioningNotMatchSpec extends VersioningSpec {

    @Unroll("#description")
    void "versioning no match scenarios"(boolean versioning,
                                         String defaultVersion,
                                         VersioningSpec.RouteVersioning routeVersioning,
                                         String jsonErrorMessage,
                                         String requestVersion,
                                         HttpStatus status,
                                         String description) {
        given:
        Map<String, Object> configuration = getConfiguration('VersioningNotMatchSpec', versioning, defaultVersion)
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, configuration)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        Argument<String> ok = Argument.of(String)
        Argument<JsonError> err = Argument.of(JsonError)
        HttpRequest<?> request = createRequest(routeVersioning, requestVersion)
        client.retrieve(request, ok, err)

        then:
        HttpClientResponseException e = thrown()
        e.status == status

        when:
        Optional<JsonError> jsonErrorOptional = e.response.getBody(JsonError)

        then:
        jsonErrorOptional.isPresent()

        when:
        JsonError jsonError = jsonErrorOptional.get()
        then:
        jsonError
        jsonError.message.contains(jsonErrorMessage)

        cleanup:
        client.close()
        httpClient.close()
        embeddedServer.close()

        where:
        versioning | defaultVersion | routeVersioning       | requestVersion | status                 |  jsonErrorMessage
        false      | null           | RouteVersioning.MULTI | null           | HttpStatus.BAD_REQUEST | 'More than 1 route matched the incoming request'
        false      | null           | RouteVersioning.MULTI | '1.0'          | HttpStatus.BAD_REQUEST | 'More than 1 route matched the incoming request'
        true       | null           | RouteVersioning.MULTI | null           | HttpStatus.BAD_REQUEST | 'More than 1 route matched the incoming request'
        true       | null           | RouteVersioning.NONE  | '1.0'          | HttpStatus.NOT_FOUND   | 'Page Not Found'
        description = createDescription(false, versioning, defaultVersion, routeVersioning, requestVersion)
    }

    @Requires(property = 'spec.name', value = 'VersioningNotMatchSpec')
    @Controller("/multi")
    static class CustomMultiController extends VersioningSpec.MultiController {}

    @Requires(property = 'spec.name', value = 'VersioningNotMatchSpec')
    @Controller("/single")
    static class CustomSingleController extends VersioningSpec.SingleController {}

    @Requires(property = 'spec.name', value = 'VersioningNotMatchSpec')
    @Controller("/none")
    static class CustomNoneController extends VersioningSpec.NoneController {}
}