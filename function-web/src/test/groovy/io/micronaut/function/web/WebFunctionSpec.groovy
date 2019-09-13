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

import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.function.FunctionBean
import io.micronaut.function.LocalFunctionRegistry
import io.micronaut.http.*
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class WebFunctionSpec extends Specification {

    void "test the function registry"() {
        given:
        LocalFunctionRegistry registry = ApplicationContext.run().getBean(LocalFunctionRegistry)

        expect:
        registry.findConsumer("consumer/string").isPresent()
        registry.findSupplier("supplier/string").isPresent()
        registry.findConsumer("consumer/pojo").isPresent()
        registry.findSupplier("supplier/pojo").isPresent()
        !registry.findConsumer("consumer/junk").isPresent()
        !registry.findSupplier("supplier/junk").isPresent()
    }

    void "test string supplier"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/supplier/string', String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'value'

        cleanup:
        embeddedServer.stop()
    }

    void "test string supplier with HEAD"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.HEAD('/supplier/string'), String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == null

        cleanup:
        embeddedServer.stop()
    }

    void "test pojo supplier"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/supplier/pojo', String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == '{"title":"The Stand"}'
        response.header(HttpHeaders.CONTENT_TYPE) == "application/json"


        cleanup:
        embeddedServer.stop()
    }


    void "test string consumer with JSON"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())
        def data = '{"title":"The Stand"}'

        when:
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.POST('/consumer/string', data))

        then:
        response.code() == HttpStatus.OK.code
        StringConsumer.LAST_VALUE == "The Stand"

        cleanup:
        embeddedServer.stop()
    }

    void "test string consumer with XML"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())
        def data = '<book><title>The Stand</title></book>'

        when:
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.POST('/consumer/string', data)
                                                                        .contentType(MediaType.APPLICATION_XML_TYPE))

        then:
        response.code() == HttpStatus.OK.code
        StringConsumer.LAST_VALUE == '<book><title>The Stand</title></book>'

        cleanup:
        embeddedServer.stop()
    }

    void 'test camel cased function bean'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/helloWorld', String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'hello there world'

        cleanup:
        embeddedServer.stop()
    }

    void "test string consumer with text plain"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())
        def data = 'The Stand'

        when:
        HttpResponse<?> response = client.toBlocking().exchange(
                HttpRequest.POST('/consumer/string', data)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
        )

        then:
        response.code() == HttpStatus.OK.code
        StringConsumer.LAST_VALUE == "The Stand"

        cleanup:
        embeddedServer.stop()
    }

    void "test pojo consumer"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())
        def data = '{"title":"The Stand"}'

        when:
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.POST('/consumer/pojo', data))


        then:
        response.code() == HttpStatus.OK.code
        PojoConsumer.LAST_VALUE == new Book(title: "The Stand")

        cleanup:
        embeddedServer.stop()
    }

    @FunctionBean("supplier/string")
    static class StringSupplier implements Supplier<String> {
        String getValue() {
            return "value"
        }
        @Override
        String get() {
            return getValue()
        }
    }

    @FunctionBean("helloWorld")
    static class CamelCaseSupplier implements Supplier<String> {
        String getValue() {
            return "hello there world"
        }
        @Override
        String get() {
            return getValue()
        }
    }

    @FunctionBean("supplier/pojo")
    static class PojoSupplier implements Supplier<Book> {

        @Override
        Book get() {
            return new Book(title: "The Stand")
        }
    }


    @FunctionBean("consumer/string")
    static class StringConsumer implements Consumer<String> {

        static String LAST_VALUE
        @Override
        void accept(String title) {
            LAST_VALUE = title
        }
    }


    @FunctionBean("consumer/pojo")
    static class PojoConsumer implements Consumer<Book> {

        static Book LAST_VALUE
        @Override
        void accept(Book book) {
            LAST_VALUE = book
        }
    }

    @EqualsAndHashCode
    static class Book {
        String title
    }
}
