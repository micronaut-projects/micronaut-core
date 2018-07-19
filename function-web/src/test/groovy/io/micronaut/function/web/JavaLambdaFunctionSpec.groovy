package io.micronaut.function.web

import groovy.transform.NotYetImplemented
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class JavaLambdaFunctionSpec extends Specification {

    @NotYetImplemented
    void "test string supplier"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/java/supplier/string', String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'value'

        cleanup:
        embeddedServer.stop()
    }
}
