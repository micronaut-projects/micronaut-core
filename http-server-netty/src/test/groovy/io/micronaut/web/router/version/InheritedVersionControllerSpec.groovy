package io.micronaut.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InheritedVersionControllerSpec extends Specification {
    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                                 : 'AbstractVersionControllerSpec',
            "micronaut.router.versioning.enabled"       : "true",
            "micronaut.router.versioning.header.enabled": "true"
    ])

    @AutoCleanup
    @Shared
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "version defined on abstract controller works on child"() {
        given:
        HttpRequest request = HttpRequest.GET("/versioned/hello").header("X-API-VERSION", "1")

        expect:
        embeddedServer.applicationContext.containsBean(AbstractVersionedController)

        when:
        def r = client.exchange(request, String)

        then:
        noExceptionThrown()
        r.status() == HttpStatus.OK
        r.body() == "helloV1"
    }

    @Requires(property = "spec.name", value = "AbstractVersionControllerSpec")
    @Controller("/versioned")
    static class VersionedController extends AbstractVersionedController {

    }

    @Version("1")
    static abstract class AbstractVersionedController {
        @Produces(MediaType.TEXT_PLAIN)

        @Get("/hello")
        String helloV1() {
            "helloV1"
        }
    }
}