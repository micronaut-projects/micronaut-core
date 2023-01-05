package io.micronaut.docs.server.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.docs.server.intro.HelloControllerSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class TraceFilterSpec extends Specification {

    void "test trace filter"() {
        given:
        EmbeddedServer embeddedServer =
                ApplicationContext.run(EmbeddedServer,['spec.name': HelloControllerSpec.simpleName,
                                                       'spec.filter': 'TraceFilter',
                                                       'spec.lang': 'java'], Environment.TEST)
        HttpClient httpClient =
                embeddedServer.getApplicationContext()
                        .createBean(HttpClient, embeddedServer.getURL())
        HttpResponse response = httpClient.toBlocking()
                                      .exchange(HttpRequest.GET('/hello'))


        expect:
        response.headers.get('X-Trace-Enabled') == 'true'

        cleanup:
        embeddedServer.close()
        httpClient.close()
    }

    void "test trace filter 2"() {
        given:
        EmbeddedServer embeddedServer =
                ApplicationContext.run(EmbeddedServer,['spec.name': HelloControllerSpec.simpleName,
                                                       'spec.filter': 'TraceFilter2',
                                                       'spec.lang': 'java'], Environment.TEST)
        HttpClient httpClient =
                embeddedServer.getApplicationContext()
                        .createBean(HttpClient, embeddedServer.getURL())
        HttpResponse response = httpClient.toBlocking()
                                      .exchange(HttpRequest.GET('/hello'))


        expect:
        response.headers.get('X-Trace-Enabled') == 'true'

        cleanup:
        embeddedServer.close()
        httpClient.close()
    }
}

