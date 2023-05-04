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
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Unroll

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
        registry.findConsumer("consumer/string2").isPresent()
        registry.findSupplier("supplier/string").isPresent()
        registry.findSupplier("supplier/string2").isPresent()
        registry.findConsumer("consumer/pojo").isPresent()
        registry.findSupplier("supplier/pojo").isPresent()
        !registry.findConsumer("consumer/junk").isPresent()
        !registry.findSupplier("supplier/junk").isPresent()
        registry.findSupplier("supplier/custom-method").isEmpty() // FunctionBean registered with non supplier method
        registry.findSupplier("consumer/custom-method").isEmpty() // FunctionBean registered with non consumer method
    }

    @Unroll
    void "test string supplier"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(uri, String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'value'

        cleanup:
        embeddedServer.stop()

        where:
        uri << ['/supplier/string', '/supplier/string2', '/supplier/custom-method']
    }

    void "test string supplier with HEAD"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

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
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/supplier/pojo', String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == '{"title":"The Stand"}'
        response.header(HttpHeaders.CONTENT_TYPE) == "application/json"

        cleanup:
        embeddedServer.stop()
    }


    @Unroll
    void "test string consumer with JSON"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
        def data = '{"title":"The Stand"}'

        when:
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.POST(uri, data))

        then:
        response.code() == HttpStatus.OK.code
        StringConsumer.LAST_VALUE == "The Stand"

        cleanup:
        embeddedServer.stop()

        where:
        uri << ['/consumer/string', '/consumer/string2', '/consumer/custom-method']
    }

    void 'test camel cased function bean'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange('/helloWorld', String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'hello there world'

        cleanup:
        embeddedServer.stop()
    }

    @PendingFeature(reason = "the controller accepts application/json only now, there is no more dynamic reading based on request content type")
    void "test string consumer with text plain"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
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
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
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

    @FunctionBean(name = "supplier/string2", method = "get")
    static class StringSupplier2 implements Supplier<String> {
        String getValue() {
            return "value"
        }
        @Override
        String get() {
            return getValue()
        }
    }

    @FunctionBean(name = "supplier/custom-method", method = "getValue")
    static class NotStringSupplier implements Supplier<String> {
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

    @FunctionBean(name = "consumer/string2", method = "accept")
    static class StringConsumer2 implements Consumer<String> {

        static String LAST_VALUE
        @Override
        void accept(String title) {
            LAST_VALUE = title
        }
    }

    @FunctionBean(name = "consumer/custom-method", method = "myAccept")
    static class NonStringConsumer implements Consumer<String> {

        static String LAST_VALUE
        @Override
        void accept(String title) {
            myAccept(title)
        }

        void myAccept(String title) {
            LAST_VALUE = title
        }
    }

    @FunctionBean("consumer/pojo")
    static class PojoConsumer implements Consumer<Book> {

        static Book LAST_VALUE
        @Override
        void accept(@Body Book book) {
            LAST_VALUE = book
        }
    }

    @EqualsAndHashCode
    static class Book {
        String title
    }
}
