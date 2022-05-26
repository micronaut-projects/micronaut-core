package io.micronaut.docs.context.annotation.primary

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PrimarySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'primaryspec'
    ], Environment.TEST)

    @AutoCleanup
    @Shared
    HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    def "@Primary annotated beans gets injected in case of a collection"() {
        expect:
        embeddedServer.applicationContext.getBeansOfType(ColorPicker.class).size() == 2

        when:
        HttpResponse<String> rsp = rxClient.toBlocking().exchange(HttpRequest.GET('/test'), String)

        then:
        rsp.status() == HttpStatus.OK
        rsp.body() == 'green'
    }
}