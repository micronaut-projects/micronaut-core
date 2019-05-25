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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpHeaderValues
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.http.annotation.Get
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ReadTimeoutSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            "micronaut.http.client.readTimeout":'3s'
    )

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())


    void "test read timeout setting"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET('/timeout'), String)

        then:
        def e = thrown(ReadTimeoutException)
        e.message == 'Read Timeout'
    }

    void "test connection pool under load 2"() {
        given:
        ApplicationContext clientContext = ApplicationContext.run(
                'micronaut.http.client.read-timeout':'10s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        RxHttpClient client = clientContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def result = client.retrieve(HttpRequest.GET('/timeout/success'), String).blockingFirst()
        def result2 = client.retrieve(HttpRequest.GET('/timeout/success'), String).blockingFirst()

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        AtomicInteger integer = new AtomicInteger(0)
        def results = (1..50).collect() { // larger than available connections
            CompletableFuture.supplyAsync({->
                client.retrieve(HttpRequest.GET('/timeout/success/' + integer.incrementAndGet()), String).blockingFirst()
            })

        }.collect({ it.get()})

        then:"Every result is correct"
        results.size() == 50
        results.every() { it == result }


        cleanup:
        client.close()
        clientContext.close()
    }

    void "test connection pool under load 3"() {
        given:
        ApplicationContext clientContext = ApplicationContext.run(
                'micronaut.http.client.read-timeout':'10s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        RxHttpClient client = clientContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def result = client.retrieve(HttpRequest.GET('/timeout/success'), String).blockingFirst()
        def result2 = client.retrieve(HttpRequest.GET('/timeout/success'), String).blockingFirst()

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        def integer = new AtomicInteger()
        def results = Flowable.concat((1..500).collect() {

            client.retrieve(HttpRequest.GET('/timeout/success/' + integer.incrementAndGet()), String)
        }).toList().blockingGet()

        then:"Every result is correct"
        results.size() == 500
        results.every() { it == result }


        cleanup:
        client.close()
        clientContext.close()
    }

    void "test connection pool under load"() {
        given:
        ApplicationContext clientContext = ApplicationContext.run(
                'micronaut.http.client.read-timeout':'10s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        RxHttpClient client = clientContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def result = client.retrieve(HttpRequest.GET('/timeout/success'), String).blockingFirst()
        def result2 = client.retrieve(HttpRequest.GET('/timeout/success'), String).blockingFirst()

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        def results = Flowable.concat((1..500).collect() {
            client.retrieve(HttpRequest.GET('/timeout/success'), String)
        }).toList().blockingGet()

        then:"Every result is correct"
        results.size() == 500
        results.every() { it == result }


        cleanup:
        client.close()
        clientContext.close()
    }

    void "test connection pool under load - no keep alive"() {
        given:
        ApplicationContext clientContext = ApplicationContext.run(
                'micronaut.http.client.read-timeout':'3s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        RxHttpClient client = clientContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def integer = new AtomicInteger()
        def result = client.retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String).blockingFirst()
        def result2 = client.retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String).blockingFirst()

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        def results = Flowable.concat((1..50).collect() {
            client.retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String)
        }).toList().blockingGet()

        then:"Every result is correct"
        results.size() == 50
        results.every() { it == result }


        cleanup:
        client.close()
        clientContext.close()
    }

    void "test connection pool under load - no keep alive 2"() {
        given:
        ApplicationContext clientContext = ApplicationContext.run(
                'micronaut.http.client.read-timeout':'3s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        RxHttpClient client = clientContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def integer = new AtomicInteger()
        def result = client.retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String).blockingFirst()
        def result2 = client.retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String).blockingFirst()

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        def results = (1..25).collect() { // larger than available connections
            CompletableFuture.supplyAsync({->
                client.retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String).blockingFirst()
            })

        }.collect({ it.get()})

        then:"Every result is correct"
        results.size() == 25
        results.every() { it == result }


        cleanup:
        client.close()
        clientContext.close()
    }

    void "test read timeout setting with connection pool"() {
        given:
        ApplicationContext clientContext = ApplicationContext.run(
                'micronaut.http.client.read-timeout':'1s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':1
        )
        RxHttpClient client = clientContext.createBean(RxHttpClient, embeddedServer.getURL())
        when:
        client.retrieve(HttpRequest.GET('/timeout/client'), String).blockingFirst()

        then:
        def e = thrown(ReadTimeoutException)
        e.message == 'Read Timeout'

        when:"Another request is made"
        def result = client.retrieve(HttpRequest.GET('/timeout/success'), String).blockingFirst()
        def result2 = client.retrieve(HttpRequest.GET('/timeout/success'), String).blockingFirst()

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        cleanup:
        client.close()
        clientContext.close()
    }

    void "test disable read timeout"() {
        given:

        ApplicationContext clientContext = ApplicationContext.run(
                'micronaut.http.client.read-timeout':'-1s')
        def server = clientContext.getBean(EmbeddedServer).start()
        RxHttpClient client = clientContext.createBean(RxHttpClient, server.getURL())
        when:
        def result = client.retrieve(HttpRequest.GET('/timeout/client'), String).blockingFirst()

        then:
        result == 'success'

        cleanup:
        client.close()
        clientContext.close()
    }

    @Controller("/timeout")
    static class GetController {

        @Inject
        TestClient testClient

        @Get(value = "/", produces = MediaType.TEXT_PLAIN)
        String index() {
            sleep 5000
            return "success"
        }

        @Get(value = "/client", produces = MediaType.TEXT_PLAIN)
        String test() {
            return testClient.get()
        }

        @Get(value = "/success", produces = MediaType.TEXT_PLAIN)
        String success() {
            return "ok"
        }

        @Get(value = "/success/{num}", produces = MediaType.TEXT_PLAIN)
        String success(Integer num) {
            return "ok"
        }

        @Get(value = "/no-keep-alive/{num}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> noKeepAlive(Integer num) {
            return HttpResponse.ok("ok").header(HttpHeaders.CONNECTION, "close")
        }
    }

    @Client("/timeout")
    static interface TestClient {
        @Get
        String get()
    }
}
