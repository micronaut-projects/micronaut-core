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
package io.micronaut.http.client.aop

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.Nullable
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

/**
 * @author Jesper Steen Møller
 * @since 1.0
 */
class StreamSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'StreamSpec'
    ])

    @Shared
    @AutoCleanup
    ApplicationContext context = embeddedServer.applicationContext

    void "test that the server can return a header value to us"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)

        when:
        HttpResponse<String> response = myClient.echoWithHeaders(2, "Hello!")

        then:
        response.status == HttpStatus.OK
        response.header('X-MyHeader') == "42"
        response.body() == "Hello!Hello!"
    }

    void "test that the server can return a header value to us with a single"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)

        when:
        HttpResponse<String> response = myClient.echoWithHeadersSingle( "Hello!")

        then:
        response.status == HttpStatus.OK
        response.header('X-MyHeader') == "42"
        response.body() == "Hello!"
    }

    void "test send and receive parameter"() {
        given:
        int n = 800// This can be as high as 806596 - if any higher, it will overflow the default aggregator buffer size
        StreamEchoClient myClient = context.getBean(StreamEchoClient)

        when:
        String result = myClient.echoAsString(n, "Hello, World!")

        then:
        result.length() == n * 13 // Should be less than 10485760
    }

    void "test receive client using ByteBuffer"() {
        given:
        int n = 42376 // This may be higher than 806596, but the test takes forever, then.
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        when:
        Flux<ByteBuffer> reactiveSequence = myClient.echoAsByteBuffers(n, "Hello, World!")
        int sum = 0
        CountDownLatch latch = new CountDownLatch(1)
        reactiveSequence.doOnTerminate { latch.countDown() }.subscribe(bytes -> {
            sum += bytes.toByteArray().count('!')
        })
        latch.await()

        then:
        sum == n
    }

    void "test that the client is unable to convert bytes to elephants"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)

        when:
        Flux.from(myClient.echoAsElephant(42, "Hello, big grey animal!")).blockFirst()

        then:
        ConfigurationException ex = thrown()
        ex.message == 'Cannot create the generated HTTP client\'s required return type, since no TypeConverter from ' +
                'ByteBuffer to class io.micronaut.http.client.aop.StreamSpec$Elephant is registered'
    }

    @Unroll
    void "JSON is still just text (variation #n)"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        expect:
        myClient.someJson(n) == '{"key":"value"}'
        where:
        n << [1, 2, 3]
    }

    void "JSON can still be streamed using reactive sequence as container"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        expect:
        myClient.someJsonCollection() == '[{"x":1},{"x":2}]'
    }

    void "JSON stream can still be streamed using reactive sequence as container"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)

        expect:
        myClient.someJsonStreamCollection() == '{"x":1}{"x":2}'
    }

    void "JSON error can still be streamed using reactive sequence as container"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)

        when:
        myClient.someJsonErrorCollection()

        then:
        HttpClientResponseException e = thrown()
        e.response.body.orElseThrow(() -> new RuntimeException()) != '{}'
    }

    void "JSON stream error can still be streamed using reactive sequence as container"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)

        when:
        myClient.someJsonStreamErrorCollection()

        then:
        HttpClientResponseException e = thrown()
        e.response.body.orElseThrow(() -> new RuntimeException()) != '{}'
    }

    @Requires(property = 'spec.name', value = 'StreamSpec')
    @Client('/stream')
    static interface StreamEchoClient {
        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        String echoAsString(@QueryValue @Nullable Integer n, @QueryValue @Nullable String data);

        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        Publisher<ByteBuffer<?>> echoAsByteBuffers(@QueryValue @Nullable Integer n, @QueryValue @Nullable String data);

        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        Publisher<Elephant> echoAsElephant(@QueryValue @Nullable Integer n, @QueryValue @Nullable String data);

        @Get(value = "/echoWithHeaders{?n,data}", consumes = MediaType.TEXT_PLAIN)
        HttpResponse<String> echoWithHeaders(@QueryValue @Nullable Integer n, @QueryValue @Nullable String data);

        @Get(value = "/echoWithHeadersSingle{?data}", consumes = MediaType.TEXT_PLAIN)
        HttpResponse<String> echoWithHeadersSingle(@QueryValue @Nullable String data);

        @Get(value = "/someJson{n}", consumes = MediaType.APPLICATION_JSON)
        String someJson(int n);

        @Get(value = "/someJsonCollection", consumes = MediaType.APPLICATION_JSON)
        String someJsonCollection();

        @Get(value = "/someJsonStreamCollection", consumes = MediaType.APPLICATION_JSON_STREAM)
        String someJsonStreamCollection();

        @Get(value = "/someJsonErrorCollection", consumes = MediaType.APPLICATION_JSON)
        String someJsonErrorCollection();

        @Get(value = "/someJsonStreamErrorCollection", consumes = MediaType.APPLICATION_JSON_STREAM)
        String someJsonStreamErrorCollection();
    }

    static class Elephant {
        int trunkLength
        int earSize
    }

    @Requires(property = 'spec.name', value = 'StreamSpec')
    @Controller('/stream')
    static class StreamEchoController {

        @Get(value = "/echo{?n,data}", produces = MediaType.TEXT_PLAIN)
        Publisher<byte[]> postStream(@QueryValue @Nullable Integer n, @QueryValue @Nullable String data) {
            return Flux.just(data.getBytes(StandardCharsets.UTF_8)).repeat(n - 1)
        }

        @Get(value = "/echoWithHeaders{?n,data}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<Publisher<byte[]>> echoWithHeaders(@QueryValue @Nullable Integer n, @QueryValue @Nullable String data) {
            return HttpResponse.ok(Flux.just(data.getBytes(StandardCharsets.UTF_8)).repeat(n - 1)).header("X-MyHeader", "42")
        }

        @Get(value = "/echoWithHeadersSingle{?data}", produces = MediaType.TEXT_PLAIN)
        @SingleResult
        Publisher<HttpResponse<byte[]>> echoWithHeadersSingle(@QueryValue @Nullable String data) {
            return Mono.just(HttpResponse.ok(data.getBytes(StandardCharsets.UTF_8)).header("X-MyHeader", "42"))
        }

        @Get(value = "/someJson1", produces = MediaType.APPLICATION_JSON)
        Publisher<byte[]> someJson1() {
            return Flux.just('{"key":"value"}'.getBytes(StandardCharsets.UTF_8))
        }

        @Get(value = "/someJson2", produces = MediaType.APPLICATION_JSON)
        HttpResponse<Publisher<byte[]>> someJson2() {
            return HttpResponse.ok(Flux.just('{"key":"value"}'.getBytes(StandardCharsets.UTF_8)))
        }

        @Get(value = "/someJson3", produces = MediaType.APPLICATION_JSON)
        Publisher<ByteBuf> someJson3() {
            return Flux.just(byteBuf('{"key":'), byteBuf('"value"}'))
        }

        @Get(value = "/someJsonCollection", produces = MediaType.APPLICATION_JSON)
        HttpResponse<Publisher<String>> someJsonCollection() {
            return HttpResponse.ok(Flux.just('{"x":1}','{"x":2}'))
        }

        @Get(value = "/someJsonStreamCollection", produces = MediaType.APPLICATION_JSON_STREAM)
        HttpResponse<Publisher<String>> someJsonStreamCollection() {
            return HttpResponse.ok(Flux.just('{"x":1}','{"x":2}'))
        }

        @Get(value = "/someJsonErrorCollection", produces = MediaType.APPLICATION_JSON)
        HttpResponse<Publisher<String>> someJsonErrorCollection() {
            return HttpResponse.badRequest(Flux.just('{"x":1}','{"x":2}'))
        }

        @Get(value = "/someJsonStreamErrorCollection", produces = MediaType.APPLICATION_JSON_STREAM)
        HttpResponse<Publisher<String>> someJsonStreamErrorCollection() {
            return HttpResponse.badRequest(Flux.just('{"x":1}','{"x":2}'))
        }

        private static ByteBuf byteBuf(String s) {
            Unpooled.wrappedBuffer(s.getBytes(StandardCharsets.UTF_8))
        }

    }

}
