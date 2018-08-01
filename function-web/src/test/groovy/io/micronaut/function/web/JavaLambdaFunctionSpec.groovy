package io.micronaut.function.web

import groovy.transform.NotYetImplemented
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class JavaLambdaFunctionSpec extends Specification {

    void "test string supplier"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/java/supplier/string', String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'myvalue'

        cleanup:
        embeddedServer.stop()
    }


    void "test func primitive"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<Long> response = client.toBlocking().exchange(HttpRequest.POST('/java/function/round', '10.2')
                                                                              .contentType(MediaType.TEXT_PLAIN_TYPE), Long)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 10

        cleanup:
        embeddedServer.stop()
    }

    void "test func pojo"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<TestFunctionFactory.Name> response = client.toBlocking().exchange(HttpRequest.POST('/java/function/upper', new TestFunctionFactory.Name(name: "fred")), TestFunctionFactory.Name)

        then:
        response.code() == HttpStatus.OK.code
        response.body().name == "FRED"

        cleanup:
        embeddedServer.stop()
    }

    @NotYetImplemented // Not sure if support for BiFunctions makes sense for Java
    void "test bi func pojo"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<TestFunctionFactory.Name> response = client.toBlocking().exchange(HttpRequest.POST('/java/function/fullname','{"arg0":"Fred", "arg1":"Flintstone"}'), TestFunctionFactory.Name)

        then:
        response.code() == HttpStatus.OK.code
        response.body().name == "Fred Flinstone"

        cleanup:
        embeddedServer.stop()
    }
}
