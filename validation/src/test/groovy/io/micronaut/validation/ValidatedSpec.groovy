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
package io.micronaut.validation

import groovy.json.JsonSlurper
import io.micronaut.cache.AsyncCacheErrorHandler
import io.micronaut.cache.CacheErrorHandler
import io.micronaut.cache.CacheManager
import io.micronaut.cache.interceptor.CacheInterceptor
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.core.order.OrderUtil
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.Specification

import javax.validation.ConstraintViolationException
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size
import java.util.concurrent.ExecutorService

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ValidatedSpec extends Specification {

    def "test order"() {
        given:
        def list = [new CacheInterceptor(Mock(CacheManager), Mock(CacheErrorHandler), Mock(AsyncCacheErrorHandler), Mock(ExecutorService), Mock(BeanContext)), new ValidatingInterceptor(Optional.empty())]
        OrderUtil.sort(list)

        expect:
        list[0] instanceof ValidatingInterceptor
        list[1] instanceof CacheInterceptor
    }

    def "test validated annotation validates beans"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        Foo foo = beanContext.getBean(Foo)

        when: "invalid data is passed"

        foo.testMe("aaa")

        then:
        def e = thrown(ConstraintViolationException)
        e.message == 'testMe.number: numeric value out of bounds (<3 digits>.<2 digits> expected)'

        when: "valid data is passed"
        def result = foo.testMe("100.00")

        then:
        result == "\$100.00"

    }

    def "test validated return value"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        Foo foo = beanContext.getBean(Foo)

        when:
        foo.notNull()

        then:
        thrown(ConstraintViolationException)
    }


    def "test validated controller validates @Valid classes"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.POST("/validated/pojo", '{"email":"abc","name":"Micronaut"}')
                        .contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE),
                String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        def result = new JsonSlurper().parseText((String) e.response.getBody().get())

        then:
        result.message == 'pojo.email: Email should be valid'

        cleanup:
        server.close()
    }

    def "test validated controller with multiple violations"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.POST("/validated/pojo", '{"email":"abc"}')
                        .contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE),
                String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        def result = new JsonSlurper().parseText((String) e.response.getBody().get())

        then:
        result.message == 'Bad Request'
        result._embedded.errors.size == 2
        result._embedded.errors.find { it.message == 'pojo.email: Email should be valid' }
        result._embedded.errors.find { it.message == 'pojo.name: must not be blank' }

        cleanup:
        server.close()
    }

    def "test validated controller args"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/validated/args", '{"amount":"xxx"}')
                        .contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE),
                String
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        def result = new JsonSlurper().parseText((String) e.response.getBody().get())

        then:
        result.message == 'amount: numeric value out of bounds (<3 digits>.<2 digits> expected)'

        cleanup:
        server.close()
    }

    void "test validated response with annotation"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        TestClient client = server.applicationContext.getBean(TestClient)

        when:
        client.test1("x")

        then:
        def e = thrown(HttpClientResponseException)
        e.message == 'value: size must be between 2 and 2147483647'


        cleanup:
        server.close()
    }

    void "test validated response raw exception creation and null"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        TestClient client = server.applicationContext.getBean(TestClient)

        when:
        client.test3()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == 'something is invalid'


        cleanup:
        server.close()
    }

    void "test validated response raw exception creation and empty"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        TestClient client = server.applicationContext.getBean(TestClient)

        when:
        client.test4()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == 'another thing is invalid'


        cleanup:
        server.close()
    }

    void "test validated controller with non introspected pojo"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.POST("/validated/no-introspection", '{"email":"a@a.com","name":"Micronaut"}')
                        .contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE),
                String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        def result = new JsonSlurper().parseText((String) e.response.getBody().get())

        then:
        result.message == 'pojo: Cannot validate io.micronaut.validation.PojoNoIntrospection. No bean introspection present. Please add @Introspected to the class and ensure Micronaut annotation processing is enabled'

        cleanup:
        server.close()
    }

    @Client("/validated/tests")
    static interface TestClient {
        @Get(value = "/test1/{value}", produces = MediaType.TEXT_PLAIN)
        String test1(String value)

        @Get(value = "/test2/{value}", produces = MediaType.TEXT_PLAIN)
        String test2(String value)

        @Get(value = "/test3", produces = MediaType.TEXT_PLAIN)
        String test3()

        @Get(value = "/test4", produces = MediaType.TEXT_PLAIN)
        String test4()
    }

    @Controller("/validated/tests")
    @Validated
    static class TestController {

        @Get(value = "/test1/{value}", produces = MediaType.TEXT_PLAIN)
        String test1(@Size(min = 2) String value) {
            return "got some " + value
        }

        @Get(value = "/test3", produces = MediaType.TEXT_PLAIN)
        String test3() {
            throw new ConstraintViolationException("something is invalid", null)
        }

        @Get(value = "/test4", produces = MediaType.TEXT_PLAIN)
        String test4() {
            throw new ConstraintViolationException("another thing is invalid", Collections.emptySet())
        }


        static class Some {

            private String thing

            @NotNull
            String getThing() {
                return thing
            }

            void setThing(String thing) {
                this.thing = thing
            }
        }
    }
}


