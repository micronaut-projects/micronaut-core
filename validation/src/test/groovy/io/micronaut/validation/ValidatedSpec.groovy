/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.Specification

import javax.validation.ConstraintViolationException
import java.util.concurrent.ExecutorService

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ValidatedSpec extends Specification {

    def "test order"() {
        given:
        def list = [new CacheInterceptor(Mock(CacheManager),Mock(CacheErrorHandler),Mock(AsyncCacheErrorHandler), Mock(ExecutorService), Mock(BeanContext)), new ValidatingInterceptor(Optional.empty())]
        OrderUtil.sort(list)

        expect:
        list[0] instanceof ValidatingInterceptor
        list[1] instanceof CacheInterceptor
    }

    def "test validated annotation validates beans"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        Foo foo = beanContext.getBean(Foo)

        when:"invalid data is passed"

        foo.testMe("aaa")

        then:
        def e = thrown(ConstraintViolationException)
        e.message == 'testMe.number: numeric value out of bounds (<3 digits>.<2 digits> expected)'

        when:"valid data is passed"
        def result = foo.testMe("100.00")

        then:
        result == "\$100.00"

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
        result.message == 'pojo.email: Email should be valid'

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
}


