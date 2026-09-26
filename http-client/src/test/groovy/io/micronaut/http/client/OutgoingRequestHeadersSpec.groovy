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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.json.JsonMapper
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The headers the client generates (Host, Content-Type, Content-Length, Transfer-Encoding,
 * Connection) are sent, but never written into the caller's request.
 */
class OutgoingRequestHeadersSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'OutgoingRequestHeadersSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient)

    @Shared
    RawHttpClient rawClient = server.applicationContext.getBean(RawHttpClient)

    void "generated headers of a plain request are sent but not written into the request"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.GET(url('localhost', '/outgoing-headers/echo'))
        Map<String, List<String>> before = snapshot(request)

        when:
        Map<String, String> wire = send(request)

        then:
        wire.host == "localhost:${server.port}".toString()
        wire.connection == 'keep-alive'
        !wire.containsKey('content-type')
        !wire.containsKey('transfer-encoding')
        snapshot(request) == before
        before.keySet().intersect(GENERATED).isEmpty()
    }

    void "generated headers of a request with a body are sent but not written into the request"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.POST(url('localhost', '/outgoing-headers/echo'), [foo: 'bar'])
        Map<String, List<String>> before = snapshot(request)

        when:
        Map<String, String> wire = send(request)

        then:
        wire.host == "localhost:${server.port}".toString()
        wire.'content-type' == MediaType.APPLICATION_JSON
        wire.'content-length' == '{"foo":"bar"}'.length().toString()
        !wire.containsKey('transfer-encoding')
        wire.connection == 'keep-alive'
        snapshot(request) == before
        before.keySet().intersect(GENERATED).isEmpty()
    }

    void "a streamed body is sent chunked without writing the transfer encoding into the request"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.POST(url('localhost', '/outgoing-headers/echo'), Flux.just('a', 'b'))
                .contentType(MediaType.TEXT_PLAIN_TYPE)
        Map<String, List<String>> before = snapshot(request)

        when:
        Map<String, String> wire = send(request)

        then:
        wire.host == "localhost:${server.port}".toString()
        wire.'content-type' == MediaType.TEXT_PLAIN
        wire.'transfer-encoding' == 'chunked'
        !wire.containsKey('content-length')
        wire.connection == 'keep-alive'
        snapshot(request) == before
    }

    void "a form request gets its encoded content type on the wire only"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.POST(url('localhost', '/outgoing-headers/echo'), [foo: 'bar'])
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        Map<String, List<String>> before = snapshot(request)

        when:
        Map<String, String> wire = send(request)

        then:
        wire.host == "localhost:${server.port}".toString()
        wire.'content-type'.startsWith(MediaType.APPLICATION_FORM_URLENCODED)
        wire.'content-length' == 'foo=bar'.length().toString()
        wire.connection == 'keep-alive'
        snapshot(request) == before
        request.headers.getAll('Content-Type') == [MediaType.APPLICATION_FORM_URLENCODED]
    }

    void "a multipart request gets its boundary on the wire only"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.POST(url('localhost', '/outgoing-headers/echo'),
                MultipartBody.builder().addPart('foo', 'bar').build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        Map<String, List<String>> before = snapshot(request)

        when:
        Map<String, String> wire = send(request)

        then:
        wire.host == "localhost:${server.port}".toString()
        wire.'content-type'.startsWith(MediaType.MULTIPART_FORM_DATA + '; boundary=')
        wire.containsKey('content-length') || wire.'transfer-encoding' == 'chunked'
        snapshot(request) == before
        request.headers.getAll('Content-Type') == [MediaType.MULTIPART_FORM_DATA]
    }

    void "a raw request gets the generated headers on the wire only"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.POST(url('localhost', '/outgoing-headers/echo'), null)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
        Map<String, List<String>> before = snapshot(request)

        when:
        Map<String, String> first = sendRaw(request, 'abc')

        then:
        first.host == "localhost:${server.port}".toString()
        first.'content-type' == MediaType.TEXT_PLAIN
        first.'content-length' == '3'
        first.connection == 'keep-alive'
        snapshot(request) == before

        when:
        request.uri(URI.create(url('127.0.0.1', '/outgoing-headers/echo')))
        Map<String, String> second = sendRaw(request, 'abcdef')

        then:
        second.host == "127.0.0.1:${server.port}".toString()
        second.'content-length' == '6'
        snapshot(request) == before
    }

    void "a request reused for different targets and bodies gets fresh generated headers"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.POST(url('localhost', '/outgoing-headers/echo'), 'a')
                .contentType(MediaType.TEXT_PLAIN_TYPE)
        Map<String, List<String>> before = snapshot(request)

        when:
        Map<String, String> first = send(request)

        then:
        first.host == "localhost:${server.port}".toString()
        first.'content-length' == '1'
        snapshot(request) == before

        when:
        request.uri(URI.create(url('127.0.0.1', '/outgoing-headers/echo'))).body('abcd')
        Map<String, String> second = send(request)

        then:
        second.host == "127.0.0.1:${server.port}".toString()
        second.'content-type' == MediaType.TEXT_PLAIN
        second.'content-length' == '4'
        second.connection == 'keep-alive'
        snapshot(request) == before
    }

    void "an explicit host header of the caller is sent unchanged"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.GET(url('localhost', '/outgoing-headers/echo'))
                .header('Host', 'example.com')
        Map<String, List<String>> before = snapshot(request)

        when:
        Map<String, String> wire = send(request)

        then:
        wire.host == 'example.com'
        snapshot(request) == before
    }

    private static final Set<String> GENERATED = ['host', 'content-length', 'transfer-encoding', 'connection'] as Set

    private String url(String host, String path) {
        "http://${host}:${server.port}${path}"
    }

    private Map<String, String> send(MutableHttpRequest<?> request) {
        client.toBlocking().retrieve(request, Map) as Map<String, String>
    }

    private Map<String, String> sendRaw(MutableHttpRequest<?> request, String body) {
        def byteBody = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(body.getBytes(StandardCharsets.UTF_8))
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(rawClient.exchange(request, byteBody, null)).block()) {
            return server.applicationContext.getBean(JsonMapper).readValue(response.byteBody().buffer().get().toByteArray(), Map) as Map<String, String>
        }
    }

    private static Map<String, List<String>> snapshot(MutableHttpRequest<?> request) {
        Map<String, List<String>> map = new TreeMap<>()
        for (String name : request.headers.names()) {
            map.put(name.toLowerCase(Locale.ROOT), List.copyOf(request.headers.getAll(name)))
        }
        map
    }

    @Requires(property = 'spec.name', value = 'OutgoingRequestHeadersSpec')
    @Controller('/outgoing-headers')
    static class EchoController {

        @Get(value = '/echo')
        Map<String, String> get(HttpRequest<?> request) {
            headers(request)
        }

        @Post(value = '/echo', consumes = MediaType.ALL)
        Map<String, String> post(HttpRequest<?> request) {
            headers(request)
        }

        private static Map<String, String> headers(HttpRequest<?> request) {
            Map<String, String> map = new TreeMap<>()
            for (String name : request.headers.names()) {
                map.put(name.toLowerCase(Locale.ROOT), String.join(',', request.headers.getAll(name)))
            }
            map
        }
    }
}
