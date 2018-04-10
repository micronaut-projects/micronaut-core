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
package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.exceptions.ServerStartupException
import io.micronaut.runtime.Micronaut
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Stepwise
class NettyHttpServerSpec extends Specification {


    void "test Micronaut server running"() {
        when:
        ApplicationContext applicationContext = Micronaut.run()
        def embeddedServer = applicationContext.getBean(EmbeddedServer)
        RxHttpClient client = applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        def response = client.exchange('/person/Fred', String).blockingFirst()
        then:
        response.body() == "Person Named Fred"

        cleanup:
        applicationContext?.stop()
    }

    void "test run Micronaut server on same port as another server"() {
        when:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(
                EmbeddedServer,
                PropertySource.of('micronaut.server.port':port)
        )
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        def response = client.exchange('/person/Fred', String).blockingFirst()
        then:
        response.body() == "Person Named Fred"

        when:"Run another server with same port"
        ApplicationContext.run(
                EmbeddedServer,
                PropertySource.of('micronaut.server.port':port)
        )

        then:"An error is thrown"
        def e = thrown(ServerStartupException)
        e.cause instanceof BindException

        cleanup:
        embeddedServer?.stop()
    }

    void "test Micronaut server running again"() {
        when:
        ApplicationContext applicationContext = Micronaut.run()
        def embeddedServer = applicationContext.getBean(EmbeddedServer)
        RxHttpClient client = applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        def response = client.exchange('/person/Fred', String).blockingFirst()
        then:
        response.body() == "Person Named Fred"

        cleanup:
        applicationContext?.stop()
    }

    void "test Micronaut server on different port"() {
        when:
        int newPort = SocketUtils.findAvailableTcpPort()
        ApplicationContext applicationContext = Micronaut.run('-port',newPort.toString())
        def embeddedServer = applicationContext.getBean(EmbeddedServer)
        RxHttpClient client = applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        def response = client.exchange('/person/Fred', String).blockingFirst()
        then:
        response.body() == "Person Named Fred"

        cleanup:
        applicationContext?.stop()
    }

    void "test bind method argument from request parameter"() {
        when:
        int newPort = SocketUtils.findAvailableTcpPort()
        ApplicationContext applicationContext = Micronaut.run('-port',newPort.toString())
        def embeddedServer = applicationContext.getBean(EmbeddedServer)
        RxHttpClient client = applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        def response = client.exchange('/person/another/job?id=10', String).blockingFirst()

        then:
        response.body() == "JOB ID 10"

        cleanup:
        applicationContext?.stop()
    }

    void "test bind method argument from request parameter when parameter missing"() {
        when:"A required request parameter is missing"
        int newPort = SocketUtils.findAvailableTcpPort()
        ApplicationContext applicationContext = Micronaut.run('-port',newPort.toString())
        def embeddedServer = applicationContext.getBean(EmbeddedServer)
        RxHttpClient client = applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        client.exchange('/person/another/job', String).blockingFirst()

        then:"A 400 is returned"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        cleanup:
        applicationContext?.stop()
    }

    void "test allowed methods handling"() {
        when:"A request is sent to the server for the wrong HTTP method"
        int newPort = SocketUtils.findAvailableTcpPort()
        ApplicationContext applicationContext = Micronaut.run('-port',newPort.toString())
        def embeddedServer = applicationContext.getBean(EmbeddedServer)
        RxHttpClient client = applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        client.exchange(HttpRequest.POST('/person/job/test', '{}'), String).blockingFirst()


        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.METHOD_NOT_ALLOWED.code
        e.response.header(HttpHeaders.ALLOW) == 'PUT'
    }


    @Controller
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
