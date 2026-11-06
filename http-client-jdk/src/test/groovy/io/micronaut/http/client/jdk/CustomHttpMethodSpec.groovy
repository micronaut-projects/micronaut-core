package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CustomHttpMethod
import io.micronaut.http.client.HttpClient
import io.micronaut.http.simple.SimpleHttpRequestFactory
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class CustomHttpMethodSpec extends Specification {

    void "a custom method is sent with its name"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'CustomHttpMethodSpec'])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
        HttpRequest<?> request = new SimpleHttpRequestFactory().create(HttpMethod.CUSTOM, '/custom-method', 'PROPFIND')
        String body = client.toBlocking().retrieve(request)

        then:
        body == 'PROPFIND'

        cleanup:
        client.close()
        server.close()
    }

    @Requires(property = 'spec.name', value = 'CustomHttpMethodSpec')
    @Controller('/custom-method')
    static class CustomMethodController {

        @CustomHttpMethod(method = 'PROPFIND')
        String propfind(HttpRequest<?> request) {
            request.methodName
        }
    }
}
