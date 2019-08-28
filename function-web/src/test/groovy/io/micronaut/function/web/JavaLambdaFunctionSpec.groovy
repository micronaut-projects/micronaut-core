/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    void "test string supplier with produces"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/java/supplier/xml', String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == '<hello></hello>'
        response.contentType.get() == MediaType.TEXT_XML_TYPE

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

    void "test func xml"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.POST('/java/function/xml', '<hello></hello>').contentType(MediaType.TEXT_XML_TYPE), String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "<hello></hello>"

        cleanup:
        embeddedServer.stop()
    }

    void "test func json"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.POST('/java/function/json', '{"hi": "there"}').contentType(MediaType.APPLICATION_JSON), String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == '{"hi": "there"}'

        cleanup:
        embeddedServer.stop()
    }
}
