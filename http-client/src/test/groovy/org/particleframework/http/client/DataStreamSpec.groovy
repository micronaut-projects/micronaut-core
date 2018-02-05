/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpRequest
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.client.rxjava2.RxHttpClient
import org.particleframework.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton
import java.nio.charset.StandardCharsets

/**
 * @author graemerocher
 * @since 1.0
 */
class DataStreamSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test read bytebuffer stream"() {
        given:
        RxHttpClient client = context.createBean(RxHttpClient,embeddedServer.getURL())

        when:
        List<byte[]> arrays = client.dataStream(HttpRequest.GET(
                '/datastream/books'
        )).map({buf -> buf.toByteArray()}).toList().blockingGet()

        then:
        arrays.size() == 2
        new String(arrays[0]) == 'The Stand'
        new String(arrays[1]) == 'The Shining'

        cleanup:
        client.stop()

    }

    void "test read response bytebuffer stream"() {
        given:
        RxHttpClient client = context.createBean(RxHttpClient,embeddedServer.getURL())

        when:
        List<byte[]> arrays = client.exchangeStream(HttpRequest.GET(
                '/datastream/books'
        )).map({res -> res.body.get().toByteArray() }).toList().blockingGet()

        then:
        arrays.size() == 2
        new String(arrays[0]) == 'The Stand'
        new String(arrays[1]) == 'The Shining'

        cleanup:
        client.stop()

    }

    @Controller("/datastream/books")
    @Singleton
    static class BookController {

        @Get(uri = '/', produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<byte[]> list() {
            return Flowable.just("The Stand".getBytes(StandardCharsets.UTF_8), "The Shining".getBytes(StandardCharsets.UTF_8))
        }
    }
}
