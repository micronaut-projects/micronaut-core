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
package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.exceptions.ServerStartupException
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import jakarta.inject.Singleton
import spock.lang.Retry
import spock.lang.Specification
import spock.lang.Stepwise

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Stepwise
@Retry // sometimes fails to bind port on Travis
class NettyHttpServerSpec extends Specification {

    void "test Micronaut server running"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyHttpServerSpec'])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        HttpResponse response = client.toBlocking().exchange('/person/Fred', String)
        then:
        response.body() == "Person Named Fred"

        cleanup:
        client.stop()
        embeddedServer.stop()
    }

    void "test run Micronaut server on same port as another server"() {
        when:
        PropertySource propertySource = PropertySource.of('micronaut.server.port':-1, 'spec.name': 'NettyHttpServerSpec')
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, propertySource, Environment.TEST)

        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
        HttpResponse response = client.toBlocking().exchange('/person/Fred', String)

        then:
        response.body() == "Person Named Fred"

        when: "Run another server with same port"
        sleep(1_000) // wait for port to be not available
        ApplicationContext.run(EmbeddedServer, PropertySource.of('micronaut.server.port':embeddedServer.getPort(), 'spec.name': 'NettyHttpServerSpec'), Environment.TEST)

        then:"An error is thrown"
        def e = thrown(ServerStartupException)
        e.cause instanceof BindException

        cleanup:
        client.stop()
        embeddedServer.applicationContext.stop()
    }

    void "test Micronaut server running again"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyHttpServerSpec'])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        HttpResponse response = client.toBlocking().exchange('/person/Fred', String)
        then:
        response.body() == "Person Named Fred"

        cleanup:
        client.stop()
        embeddedServer.stop()
    }

    void "test Micronaut server on different port"() {
        when:
        int newPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyHttpServerSpec', 'micronaut.server.port':newPort.toString()])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        HttpResponse response = client.toBlocking().exchange('/person/Fred', String)

        then:
        response.body() == "Person Named Fred"

        cleanup:
        client.stop()
        embeddedServer.stop()
    }

    void "test bind method argument from request parameter"() {
        when:
        int newPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyHttpServerSpec', 'micronaut.server.port':newPort.toString()])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        HttpResponse response = client.toBlocking().exchange('/person/another/job?id=10', String)

        then:
        response.body() == "JOB ID 10"

        cleanup:
        client.stop()
        embeddedServer.stop()
    }

    void "test bind method argument from request parameter when parameter missing"() {
        when:"A required request parameter is missing"
        int newPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyHttpServerSpec', 'micronaut.server.port':newPort.toString()])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        client.toBlocking().exchange('/person/another/job', String)

        then:"A 400 is returned"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        cleanup:
        client.stop()
            embeddedServer.stop()
    }

    void "test allowed methods handling"() {
        when:"A request is sent to the server for the wrong HTTP method"
        int newPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyHttpServerSpec', 'micronaut.server.port':newPort.toString()])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        client.toBlocking().exchange(HttpRequest.POST('/person/job/test', '{}'), String)

        then:
        HttpClientResponseException e = thrown()
        e.response.code() == HttpStatus.METHOD_NOT_ALLOWED.code
        e.response.header(HttpHeaders.ALLOW) == 'PUT'

        cleanup:
        client.stop()
        embeddedServer.stop()
    }

    void "test expected connection persistence"() {
        when:
        DefaultHttpClientConfiguration config = new DefaultHttpClientConfiguration()
        // The client will explicitly request "Connection: close" unless using a connection pool, so set it up
        config.connectionPoolConfiguration.enabled = true
        config.connectionPoolConfiguration.acquireTimeout = Duration.of(3, ChronoUnit.SECONDS);

        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyHttpServerSpec'])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL(), config)

        HttpRequest request = HttpRequest.create(HttpMethod.GET, '/person/Fred')
        HttpResponse response = client.toBlocking().exchange(request, String)
        then:
        response.body() == "Person Named Fred"
        response.header(HttpHeaders.CONNECTION) == null // HTTP/1.1 is keep-alive by default

        cleanup:
        client.stop()
            embeddedServer.stop()
    }

    void "test run Micronaut server when enabling both http and https"() {
        when:
        PropertySource propertySource = PropertySource.of(
                'spec.name': 'NettyHttpServerSpec',
                'micronaut.server.port':httpPort,
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.port': -1,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.server.dualProtocol':true
        )
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, propertySource, Environment.TEST)
        int httpPort = (embeddedServer.boundPorts - embeddedServer.port).first()

        URL secureUrl = embeddedServer.getURL()
        HttpClient httpsClient = embeddedServer.applicationContext.createBean(HttpClient, secureUrl)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, new URL("http://localhost:$httpPort"))
        HttpResponse httpsResponse = httpsClient.toBlocking().exchange('/person/Fred', String)
        HttpResponse httpResponse = httpClient.toBlocking().exchange('/person/Fred', String)

        then:
        httpsResponse.body() == "Person Named Fred"
        httpResponse.body() == "Person Named Fred"

        cleanup:
        httpsClient.stop()
        embeddedServer.applicationContext.stop()
    }

    void "test dual protocol is using https by default when grabbing values from server"() {
        def securePort = SocketUtils.findAvailableTcpPort()
        def unsecurePort = SocketUtils.findAvailableTcpPort()
        when:
        PropertySource propertySource = PropertySource.of(
                'spec.name': 'NettyHttpServerSpec',
                'micronaut.server.port': unsecurePort,
                'micronaut.server.ssl.port': securePort,
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.dualProtocol':true
        )
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, propertySource, Environment.TEST)

        then:
        embeddedServer.getPort() == securePort
        embeddedServer.getScheme() == "https"
        embeddedServer.getURL().toString() == "https://localhost:$securePort"

        cleanup:
        embeddedServer.applicationContext.stop()
    }

    void "test non dual protocol Micronaut server only fires startup event once"() {
        when:
        PropertySource propertySource = PropertySource.of(
                'spec.name': 'NettyHttpServerSpec',
                'micronaut.server.port': SocketUtils.findAvailableTcpPort(),
                'micronaut.server.dualProtocol':false
        )
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, propertySource)

        then:
        EventCounter eventCounter = embeddedServer.applicationContext.getBean(EventCounter)
        eventCounter.count as Integer == 1

        cleanup:
        embeddedServer.applicationContext.stop()
    }

    void "test dual protocol only fires startup event once"() {
        when:
        PropertySource propertySource = PropertySource.of(
                'spec.name': 'NettyHttpServerSpec',
                'micronaut.server.port': SocketUtils.findAvailableTcpPort(),
                'micronaut.server.ssl.port': SocketUtils.findAvailableTcpPort(),
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.dualProtocol':true
        )
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, propertySource)

        then:
        EventCounter eventCounter = embeddedServer.applicationContext.getBean(EventCounter)
        eventCounter.count as Integer == 1

        cleanup:
        embeddedServer.applicationContext.stop()
    }

    def "toCompletionStage after event loop shutdown"() {
        given:
        def group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        def loop = group.next()

        when:
        def promise = loop.newPromise()
        promise.setSuccess("foo")
        loop.shutdownGracefully().syncUninterruptibly()
        then:
        NettyHttpServer.toCompletionStage(promise).toCompletableFuture().get() == "foo"

        cleanup:
        group.shutdownGracefully()
    }

    @Requires(property = "spec.name", value = "NettyHttpServerSpec")
    @Singleton
    static class EventCounter {
        AtomicInteger count = new AtomicInteger(0)

        @EventListener
        void receive(StartupEvent event) {
            count.incrementAndGet()
        }
    }

    @Requires(property = "spec.name", value = "NettyHttpServerSpec")
    @Controller("/person")
    static class PersonController {

        @Get('/{name}')
        String name(String name) {
            "Person Named $name"
        }

        @Put('/job/{name}')
        void doWork(String name) {
            println 'doing work'
        }

        @Get('/another/job')
        String doMoreWork(int id) {
            "JOB ID $id"
        }
    }
}
