package io.micronaut.management.endpoint.health;

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import spock.lang.Specification
import spock.lang.Unroll;

class HealthEndpointCustomPathSpec extends Specification {

    @Unroll
    void "health path can be changed"(String path) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, [
                "endpoints.health.path": path
        ])
        expect:
        path.substring(1) == server.applicationContext.getBean(EndpointConfiguration.class, Qualifiers.byName("health")).getPath()

        when:
        HttpClient httpClient = server.applicationContext.createBean(HttpClient, server.getURL())
        BlockingHttpClient client = httpClient.toBlocking()
        client.exchange(HttpRequest.GET(path))
        then:
        noExceptionThrown()
        cleanup:
        client.close()
        httpClient.close()
        server.close()

        where:
        path << ["/up", "/health/check"]
    }
}
