package io.micronaut.management.endpoint.info.source

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Produces
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class VersionInfoSourceSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'VersionInfoSourceSpec',
            'endpoints.info.enabled': true,
            'endpoints.info.sensitive': false,
    ])

    @AutoCleanup
    @Shared
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "versions are shown in info endpoint"() {
        expect:
        embeddedServer.applicationContext.containsBean(ControllerWithMultipleVersions)
        embeddedServer.applicationContext.containsBean(ExtraController)
        embeddedServer.applicationContext.containsBean(AnotherController)
        !embeddedServer.applicationContext.getBeansOfType(VersionCollector).isEmpty()

        when:
        HttpResponse<Map> resp = client.exchange(HttpRequest.GET('/info'), Map)

        then:
        resp.status() == HttpStatus.OK

        resp.body().containsKey('api-versions')
        resp.body()['api-versions'] == ['1.0', '2.0', '3.0']
    }

    @Requires(property = "spec.name", value = 'VersionInfoSourceSpec')
    @Controller
    static class ControllerWithMultipleVersions {

        @Produces(MediaType.TEXT_PLAIN)
        @Version("1.0")
        String v1() {
            "1.0"
        }

        @Produces(MediaType.TEXT_PLAIN)
        @Version("2.0")
        String v2() {
            "2.0"
        }
    }

    @Requires(property = "spec.name", value = 'VersionInfoSourceSpec')
    @Controller
    static class ExtraController {

        @Produces(MediaType.TEXT_PLAIN)
        @Version("1.0")
        String foo() {
            "bar"
        }
    }

    @Requires(property = "spec.name", value = 'VersionInfoSourceSpec')
    @Controller
    static class AnotherController {

        @Produces(MediaType.TEXT_PLAIN)
        @Version("3.0")
        String foo() {
            "bar"
        }
    }
}
