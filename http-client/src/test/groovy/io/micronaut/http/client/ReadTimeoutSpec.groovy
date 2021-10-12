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
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.http.client.netty.DefaultNettyHttpClientRegistry
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.pool.AbstractChannelPoolMap
import io.netty.channel.pool.FixedChannelPool
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.lang.reflect.Field
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ReadTimeoutSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            "micronaut.http.client.readTimeout":'3s',
            'spec.name': 'ReadTimeoutSpec'
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
                'spec.name': 'ReadTimeoutSpec',
                'micronaut.http.client.read-timeout':'10s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        HttpClient client = clientContext.createBean(HttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def result = client.toBlocking().retrieve(HttpRequest.GET('/timeout/success'), String)
        def result2 = client.toBlocking().retrieve(HttpRequest.GET('/timeout/success'), String)

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        AtomicInteger integer = new AtomicInteger(0)
        def results = (1..50).collect() { // larger than available connections
            CompletableFuture.supplyAsync({->
                client.toBlocking().retrieve(HttpRequest.GET('/timeout/success/' + integer.incrementAndGet()), String)
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
                'spec.name': 'ReadTimeoutSpec',
                'micronaut.http.client.read-timeout':'10s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        HttpClient client = clientContext.createBean(HttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def result = client.toBlocking().retrieve(HttpRequest.GET('/timeout/success'), String)
        def result2 = client.toBlocking().retrieve(HttpRequest.GET('/timeout/success'), String)

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        def integer = new AtomicInteger()
        def results = Flux.concat((1..500).collect() {

            client.retrieve(HttpRequest.GET('/timeout/success/' + integer.incrementAndGet()), String)
        }).collectList().block()

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
                'spec.name': 'ReadTimeoutSpec',
                'micronaut.http.client.read-timeout':'10s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        HttpClient client = clientContext.createBean(HttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def result = client.toBlocking().retrieve(HttpRequest.GET('/timeout/success'), String)
        def result2 = client.toBlocking().retrieve(HttpRequest.GET('/timeout/success'), String)

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        def results = Flux.concat((1..500).collect() {
            client.retrieve(HttpRequest.GET('/timeout/success'), String)
        }).collectList().block()

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
                'spec.name': 'ReadTimeoutSpec',
                'micronaut.http.client.read-timeout':'3s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        HttpClient client = clientContext.createBean(HttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def integer = new AtomicInteger()
        def result = client.toBlocking().retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String)
        def result2 = client.toBlocking().retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String)

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        def results = Flux.concat((1..50).collect() {
            client.retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String)
        }).collectList().block()

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
                'spec.name': 'ReadTimeoutSpec',
                'micronaut.http.client.read-timeout':'3s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        HttpClient client = clientContext.createBean(HttpClient, embeddedServer.getURL())

        when:"Another request is made"
        def integer = new AtomicInteger()
        def result = client.toBlocking().retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String)
        def result2 = client.toBlocking().retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String)

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        when:"issue a whole bunch of requests"
        def results = (1..25).collect() { // larger than available connections
            CompletableFuture.supplyAsync({->
                client.toBlocking().retrieve(HttpRequest.GET('/timeout/no-keep-alive/' + integer.incrementAndGet()), String)
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
                'spec.name': 'ReadTimeoutSpec',
                'micronaut.http.client.read-timeout':'1s',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':1
        )
        HttpClient client = clientContext.createBean(HttpClient, embeddedServer.getURL())
        when:
        client.toBlocking().retrieve(HttpRequest.GET('/timeout/client'), String)

        then:
        def e = thrown(ReadTimeoutException)
        e.message == 'Read Timeout'

        when:"Another request is made"
        def result = client.toBlocking().retrieve(HttpRequest.GET('/timeout/success'), String)
        def result2 = client.toBlocking().retrieve(HttpRequest.GET('/timeout/success'), String)

        then:"Ensure the read timeout was reset in the connection in the pool"
        result == result2

        cleanup:
        client.close()
        clientContext.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1796')
    void "test read timeout setting with connection pool doesn't leak connections"() {
        given:
        ApplicationContext clientContext = ApplicationContext.run(
                'spec.name': 'ReadTimeoutSpec',
                'my.port':embeddedServer.getPort(),
                'micronaut.http.client.read-timeout':'1ms',
                'micronaut.http.client.pool.enabled':true,
                'micronaut.http.client.pool.max-connections':10
        )
        PollingConditions conditions = new PollingConditions(timeout: 3)

        when:"Lots of read timeouts occur"
        TimeoutClient client = clientContext.getBean(TimeoutClient)
        (1..20).collect {
            client.getFuture()
        }.each {
            try {
                it.join()
            } catch (Throwable e){ }
        }

        def clients = clientContext.getBean(DefaultNettyHttpClientRegistry).clients
        def clientKey = clients.keySet().stream()
                .filter { it.clientId == "http://localhost:${embeddedServer.getPort()}" }
                .findFirst()
                .get()
        def pool = getPool(clients.get(clientKey))

        then:"Connections are not leaked"
        conditions.eventually {
            pool.acquiredChannelCount() == 0
        }

        cleanup:
        clientContext.close()
    }

    FixedChannelPool getPool(HttpClient client) {
        AbstractChannelPoolMap poolMap = client.poolMap
        Field mapField = AbstractChannelPoolMap.getDeclaredField("map")
        mapField.setAccessible(true)
        Map innerMap = mapField.get(poolMap)
        return innerMap.values().first()
    }

    void "test disable read timeout"() {
        given:

        ApplicationContext clientContext = ApplicationContext.run([
                'spec.name': 'ReadTimeoutSpec',
                'micronaut.http.client.read-timeout':'-1s'])
        def server = clientContext.getBean(EmbeddedServer).start()
        HttpClient client = clientContext.createBean(HttpClient, server.getURL())
        when:
        def result = client.toBlocking().retrieve(HttpRequest.GET('/timeout/client'), String)

        then:
        result == 'success'

        cleanup:
        client.close()
        clientContext.close()
    }

    @Requires(property = 'spec.name', value = 'ReadTimeoutSpec')
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

    @Requires(property = 'spec.name', value = 'ReadTimeoutSpec')
    @Client('http://localhost:${my.port}')
    static interface TimeoutClient {

        @Get("/")
        CompletableFuture<String> getFuture()
    }

    @Requires(property = 'spec.name', value = 'ReadTimeoutSpec')
    @Client("/timeout")
    @Consumes(MediaType.TEXT_PLAIN)
    static interface TestClient {
        @Get
        String get()

        @Get("/")
        CompletableFuture<String> getFuture()
    }
}
