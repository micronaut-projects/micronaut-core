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
import io.micronaut.aop.InterceptPhase
import io.micronaut.aop.InvocationContext
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.order.OrderUtil
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.Specification

import javax.validation.ConstraintViolationException
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ValidatedSpec extends Specification {

    def "test order"() {
        given:
        def list = [new MethodInterceptor<Object, Object>() {

            @Override
            int getOrder() {
                return InterceptPhase.CACHE.getPosition()
            }

            @Override
            Object intercept(MethodInvocationContext context) {
                return null
            }

            @Override
            Object intercept(InvocationContext context) {
                return null
            }
        }, new ValidatingInterceptor(null, null)]
        OrderUtil.sort(list)

        expect:
        list[0] instanceof ValidatingInterceptor
        list[1] instanceof MethodInterceptor
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

        cleanup:
        beanContext.close()
    }

    def "test validated return value"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        Foo foo = beanContext.getBean(Foo)

        when:
        foo.notNull()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "String: must not be null"

        cleanup:
        beanContext.close()
    }

    def "test validated return value without cascade"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        Foo foo = beanContext.getBean(Foo)

        when:
        foo.notNullBar()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "Bar: must not be null"

        cleanup:
        beanContext.close()
    }

    def "test validate return value with cascading"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        Foo foo = beanContext.getBean(Foo)

        when:
        foo.cascadeValidateReturnValue()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "Bar.prop: must not be null"

        cleanup:
        beanContext.close()
    }

    def "test validate list return value with cascading"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        Foo foo = beanContext.getBean(Foo)

        when:
        foo.validateReturnList()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "List[0].prop: must not be null"

        cleanup:
        beanContext.close()
    }

    def "test validate map return value with cascading"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        Foo foo = beanContext.getBean(Foo)

        when:
        foo.validateMap()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "Map[barObj].prop: must not be null"

        cleanup:
        beanContext.close()
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
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
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

    def "test validated controller validates @Valid classes with standard embedded errors"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName,
                'jackson.always-serialize-errors-as-list': true
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.POST("/validated/pojo", '{"email":"abc","name":"Micronaut"}')
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        def result = new JsonSlurper().parseText((String) e.response.getBody().get())

        then:
        result.message == 'Bad Request'
        result._embedded.errors.size == 1
        result._embedded.errors.find { it.message == 'pojo.email: Email should be valid' }

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
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
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
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
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

    def "test validated controller args with standard embedded errors"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName,
                'jackson.always-serialize-errors-as-list': true
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/validated/args", '{"amount":"xxx"}')
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                String
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        def result = new JsonSlurper().parseText((String) e.response.getBody().get())

        then:
        result.message == 'Bad Request'
        result._embedded.errors.size == 1
        result._embedded.errors.find { it.message == 'amount: numeric value out of bounds (<3 digits>.<2 digits> expected)' }

        cleanup:
        server.close()
    }

    void "test validated controller optional query param"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/validated/optional?limit=0"),
                Argument.STRING,
                Argument.of(Map, String, Object)
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        Map result = e.response.getBody(Argument.of(Map, String, Object)).get()

        then:
        result.message == 'limit: must be greater than or equal to 1'

        cleanup:
        server.close()
    }

    void "test validated controller optional query param with standard embedded errors"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName,
                'jackson.always-serialize-errors-as-list': true
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/validated/optional?limit=0"),
                Argument.STRING,
                Argument.of(Map, String, Object)
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        Map result = e.response.getBody(Argument.of(Map, String, Object)).get()

        then:
        result.message == 'Bad Request'
        result._embedded.errors.size == 1
        result._embedded.errors.find { it.message == 'limit: must be greater than or equal to 1' }

        cleanup:
        server.close()
    }

    void "test validated controller empty optional query param"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/validated/optional"),
                Argument.STRING,
                Argument.of(Map, String, Object)
        ))
        def resp = flowable.blockingFirst()

        then:
        noExceptionThrown()
        resp.body() == "true"

        cleanup:
        client.close()
        server.close()
    }

    void "test validated controller empty optional query param with not null"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/validated/optional/notNull"),
                Argument.STRING,
                Argument.of(Map, String, Object)
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        Map result = e.response.getBody(Argument.of(Map, String, Object)).get()

        then:
        result.message == 'limit: must not be null'

        cleanup:
        server.close()
    }

    void "test validated controller empty optional query param with not null with standard embedded errors"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'jackson.always-serialize-errors-as-list': true
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/validated/optional/notNull"),
                Argument.STRING,
                Argument.of(Map, String, Object)
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        Map result = e.response.getBody(Argument.of(Map, String, Object)).get()

        then:
        result.message == 'Bad Request'
        result._embedded.errors.size == 1
        result._embedded.errors.find { it.message == 'limit: must not be null' }

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

    void "test validated response with annotation with standard embedded errors"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'jackson.always-serialize-errors-as-list': true
        ])
        TestClient client = server.applicationContext.getBean(TestClient)

        when:
        client.test1("x")

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def result = new JsonSlurper().parseText((String) e.response.getBody().get())

        then:
        result.message == 'Bad Request'
        result._embedded.errors.size == 1
        result._embedded.errors.find { it.message == 'value: size must be between 2 and 2147483647' }

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

    void "test a client can be validated"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        TestClient client = server.applicationContext.getBean(TestClient)

        when:
        client.test5("")

        then:
        thrown(ConstraintViolationException)

        cleanup:
        server.close()
    }

    void "test a java client can be validated"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        JavaClient client = server.applicationContext.getBean(JavaClient)

        when:
        client.test5("")

        then:
        thrown(ConstraintViolationException)
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
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
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

    void "test validated controller with non introspected pojo with standard embedded errors"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName,
                'jackson.always-serialize-errors-as-list': true
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.POST("/validated/no-introspection", '{"email":"a@a.com","name":"Micronaut"}')
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                String
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code

        when:
        def result = new JsonSlurper().parseText((String) e.response.getBody().get())

        then:
        result.message == 'Bad Request'
        result._embedded.errors.size == 1
        result._embedded.errors.find { it.message == 'pojo: Cannot validate io.micronaut.validation.PojoNoIntrospection. No bean introspection present. Please add @Introspected to the class and ensure Micronaut annotation processing is enabled' }

        cleanup:
        server.close()
    }

    void "test validated config props with annotations in abstract class"() {
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])

        when:
        context.getBean(Config)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.message.contains("count - must be greater than or equal to 1")
    }

    @Client("/validated/tests")
    @Consumes(MediaType.TEXT_PLAIN)
    static interface TestClient {

        @Get(value = "/test1/{value}", produces = MediaType.TEXT_PLAIN)
        String test1(String value)

        @Get(value = "/test2/{value}", produces = MediaType.TEXT_PLAIN)
        String test2(String value)

        @Get(value = "/test3", produces = MediaType.TEXT_PLAIN)
        String test3()

        @Get(value = "/test4", produces = MediaType.TEXT_PLAIN)
        String test4()

        @Get(value = "/validated")
        String test5(@NotBlank @Header String header)
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

    @ConfigurationProperties("my.config")
    static class Config {

        @NotNull
        @Min(1L)
        @Max(10L)
        Integer count = 0

    }

    abstract static class AbstractConfig {
        @NotNull
        @Min(1L)
        @Max(10L)
        Integer count = 0
    }
}


