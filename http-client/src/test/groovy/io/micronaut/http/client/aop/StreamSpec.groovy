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
package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.io.buffer.ReferenceCounted
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.Nullable
import java.nio.charset.StandardCharsets

/**
 * @author Jesper Steen Møller
 * @since 1.0
 */
class StreamSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

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
        Flowable<ByteBuffer> responseFlowable = myClient.echoAsByteBuffers(n, "Hello, World!")
        int sum = 0
        // using blockingForEach for variations sake
        responseFlowable.blockingForEach { ByteBuffer bytes ->
            sum += bytes.toByteArray().count('!')
            ((ReferenceCounted)bytes).release()
        }
        then:
        sum == n
    }

    void "test receive client using byte[]"() {
        given:
        int n = 90659
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        when:
        Flux<byte[]> responseFlowable = myClient.echoAsByteArrays(n, "Hello, World!")
        // reduce to the total count of !'s
        long sum = responseFlowable.reduce(0L, { long acc, byte[] bytes -> acc + bytes.count('!') }).block()
        then:
        sum == n
    }

    void "test that the client is unable to convert bytes to elephants"() {
        given:
        StreamEchoClient myClient = context.getBean(StreamEchoClient)
        when:
        Flowable<Elephant> _ = myClient.echoAsElephant(42, "Hello, big grey animal!")
        then:
        def ex = thrown(ConfigurationException)
        ex.message == 'Cannot create the generated HTTP client\'s required return type, since no TypeConverter from ' +
                'ByteBuffer to class io.micronaut.http.client.aop.StreamSpec$Elephant is registered'
    }

    @Client('/stream')
    static interface StreamEchoClient {
        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        String echoAsString(@QueryValue @Nullable int n, @QueryValue @Nullable String data);

        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        Flowable<ByteBuffer<?>> echoAsByteBuffers(@QueryValue @Nullable int n, @QueryValue @Nullable String data);

        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        Flux<byte[]> echoAsByteArrays(@QueryValue @Nullable int n, @QueryValue @Nullable String data);

        @Get(value = "/echo{?n,data}", consumes = MediaType.TEXT_PLAIN)
        Flowable<Elephant> echoAsElephant(@QueryValue @Nullable int n, @QueryValue @Nullable String data);
    }

    static class Elephant {
        int trunkLength
        int earSize
    }

    @Controller('/stream')
    static class StreamEchoController {

        @Get(value = "/echo{?n,data}", produces = MediaType.TEXT_PLAIN)
        Flowable<byte[]> postStream(@QueryValue @Nullable int n,  @QueryValue @Nullable String data) {
            return Flowable.just(data.getBytes(StandardCharsets.UTF_8)).repeat(n)
        }
    }

}
