/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/12201')
class UnixDomainSocketClientSpec extends Specification {

    @Shared
    Path tmpDir = Files.createTempDirectory('uds-client')

    def cleanupSpec() {
        tmpDir.toFile().deleteDir()
    }

    private static String encode(Path socketPath) {
        socketPath.toString().replace('/', '%2F')
    }

    private Path socketPath(String name) {
        tmpDir.resolve(name)
    }

    void 'the request key carries the decoded socket path'() {
        when:
        def key = new NettyHttpClient.RequestKey(null, URI.create('unix://%2Ftmp%2Fx.sock/v1/info'))

        then:
        key.socketPath == '/tmp/x.sock'
        key.port == -1
        !key.secure
    }

    void 'a plain http request key has no socket path'() {
        when:
        def key = new NettyHttpClient.RequestKey(null, URI.create('http://example.com/v1/info'))

        then:
        key.socketPath == null
        key.host == 'example.com'
        key.port == 80
    }

    void 'request keys pool per socket path and never collide with tcp'() {
        given:
        def a = new NettyHttpClient.RequestKey(null, URI.create('unix://%2Ftmp%2Fa.sock/x'))
        def alsoA = new NettyHttpClient.RequestKey(null, URI.create('unix://%2Ftmp%2Fa.sock/y'))
        def b = new NettyHttpClient.RequestKey(null, URI.create('unix://%2Ftmp%2Fb.sock/x'))
        def tcp = new NettyHttpClient.RequestKey(null, URI.create('http://example.com/x'))

        expect: 'the same socket shares a pool regardless of request path'
        a == alsoA
        a.hashCode() == alsoA.hashCode()

        and: 'a different socket gets its own pool'
        a != b

        and: 'a unix key never matches a tcp key'
        a != tcp
    }

    void 'a request over a unix domain socket gets a response'() {
        given:
        def path = socketPath('round-trip')
        def ctx = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                : 'UnixDomainSocketClientSpec',
                'micronaut.server.netty.listeners.a.family': 'UNIX',
                'micronaut.server.netty.listeners.a.path'  : path.toString(),
        ]).applicationContext
        def client = ctx.createBean(HttpClient, new URI("unix://${encode(path)}"))

        when:
        def body = client.toBlocking().retrieve('/uds/hello')

        then:
        body == 'hello'

        cleanup:
        client.close()
        ctx.close()
    }

    void 'the host header does not leak the socket path'() {
        given:
        def path = socketPath('host-header')
        def ctx = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                : 'UnixDomainSocketClientSpec',
                'micronaut.server.netty.listeners.a.family': 'UNIX',
                'micronaut.server.netty.listeners.a.path'  : path.toString(),
        ]).applicationContext
        def client = ctx.createBean(HttpClient, new URI("unix://${encode(path)}"))

        when:
        def host = client.toBlocking().retrieve('/uds/host')

        then:
        !host.contains('%2F')
        !host.contains(path.toString())

        cleanup:
        client.close()
        ctx.close()
    }

    void 'a second request reuses the pooled connection'() {
        given:
        def path = socketPath('reuse')
        def ctx = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                : 'UnixDomainSocketClientSpec',
                'micronaut.server.netty.listeners.a.family': 'UNIX',
                'micronaut.server.netty.listeners.a.path'  : path.toString(),
        ]).applicationContext
        def client = ctx.createBean(HttpClient, new URI("unix://${encode(path)}"))

        when:
        def first = client.toBlocking().retrieve('/uds/connection')
        def second = client.toBlocking().retrieve('/uds/connection')

        then: 'both requests arrive on the same server-side connection'
        first == second

        cleanup:
        client.close()
        ctx.close()
    }

    void 'connecting to a socket that does not exist fails'() {
        given:
        def missing = socketPath('not-there')
        def ctx = ApplicationContext.run()
        def client = ctx.createBean(HttpClient, new URI("unix://${encode(missing)}"))

        when:
        client.toBlocking().retrieve('/uds/hello')

        then:
        thrown(HttpClientException)

        cleanup:
        client.close()
        ctx.close()
    }

    void 'a unix client and a tcp client work side by side'() {
        given:
        def path = socketPath('coexist')
        def ctx = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                : 'UnixDomainSocketClientSpec',
                'micronaut.server.netty.listeners.a.family': 'UNIX',
                'micronaut.server.netty.listeners.a.path'  : path.toString(),
                'micronaut.server.netty.listeners.b.port'  : -1,
        ])
        def server = (EmbeddedServer) ctx
        def udsClient = server.applicationContext.createBean(HttpClient, new URI("unix://${encode(path)}"))
        def tcpClient = server.applicationContext.createBean(HttpClient, server.URI)

        expect:
        udsClient.toBlocking().retrieve('/uds/hello') == 'hello'
        tcpClient.toBlocking().retrieve('/uds/hello') == 'hello'

        cleanup:
        udsClient.close()
        tcpClient.close()
        server.close()
    }

    @Controller('/uds')
    @Requires(property = 'spec.name', value = 'UnixDomainSocketClientSpec')
    static class Ctrl {

        @Get('/hello')
        String hello() {
            'hello'
        }

        @Get('/host')
        String host(@Header('Host') String host) {
            host
        }

        @Get('/connection')
        String connection(io.micronaut.http.HttpRequest<?> request) {
            ((NettyHttpRequest) request).channelHandlerContext.channel().id().asLongText()
        }
    }
}
