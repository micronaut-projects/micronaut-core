/*
 * Copyright 2017 original authors
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
package org.particleframework.function.web

import groovy.transform.EqualsAndHashCode
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.particleframework.context.ApplicationContext
import org.particleframework.function.FunctionBean
import org.particleframework.function.LocalFunctionRegistry
import org.particleframework.http.HttpHeaders
import org.particleframework.http.HttpStatus
import org.particleframework.runtime.server.EmbeddedServer
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
        LocalFunctionRegistry registry = ApplicationContext.run(LocalFunctionRegistry)

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
        String server = "http://localhost:$embeddedServer.port"
        OkHttpClient client = new OkHttpClient()
        def request = new Request.Builder()
                .url("$server/supplier/string")

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'value'

        cleanup:
        embeddedServer.stop()
    }

    void "test pojo supplier"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        String server = "http://localhost:$embeddedServer.port"
        OkHttpClient client = new OkHttpClient()
        def request = new Request.Builder()
                .url("$server/supplier/pojo")

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == '{"title":"The Stand"}'
        response.header(HttpHeaders.CONTENT_TYPE) == "application/json"


        cleanup:
        embeddedServer.stop()
    }


    void "test string consumer with JSON"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        String server = "http://localhost:$embeddedServer.port"
        OkHttpClient client = new OkHttpClient()
        def data = '{"title":"The Stand"}'
        def request = new Request.Builder()
                .url("$server/consumer/string")
                .post(RequestBody.create( MediaType.parse(org.particleframework.http.MediaType.APPLICATION_JSON), data))

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        StringConsumer.LAST_VALUE == "The Stand"

        cleanup:
        embeddedServer.stop()
    }

    void "test string consumer with text plain"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        String server = "http://localhost:$embeddedServer.port"
        OkHttpClient client = new OkHttpClient()
        def data = 'The Stand'
        def request = new Request.Builder()
                .url("$server/consumer/string")
                .post(RequestBody.create( MediaType.parse("text/plain"), data))

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        StringConsumer.LAST_VALUE == "The Stand"

        cleanup:
        embeddedServer.stop()
    }

    void "test pojo consumer"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        String server = "http://localhost:$embeddedServer.port"
        OkHttpClient client = new OkHttpClient()
        def request = new Request.Builder()
                .url("$server/consumer/pojo")
                .post(RequestBody.create( MediaType.parse(org.particleframework.http.MediaType.APPLICATION_JSON), '{"title":"The Stand"}'))
        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        PojoConsumer.LAST_VALUE == new Book(title: "The Stand")

        cleanup:
        embeddedServer.stop()
    }

    @FunctionBean("supplier/string")
    static class StringSupplier implements Supplier<String> {

        @Override
        String get() {
            return "value"
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
