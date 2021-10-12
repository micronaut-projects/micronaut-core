/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.codec.http.HttpHeaderValues
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.zip.GZIPOutputStream

class CompressedRequest extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, [
                    'spec.name': 'CompressedRequest'
            ])

    void "test gzipped body in post request"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())
        byte[] body = gzip("[0, 1, 2, 3, 4]")

        when:
        int i = 0
        HttpResponse<List> result = client.toBlocking().exchange(
                HttpRequest.POST('/gzip/request/numbers', body)
                        .contentEncoding(HttpHeaderValues.GZIP)
                        .contentType(MediaType.APPLICATION_JSON_TYPE), List)

        then:
        result.body().size() == 5
        result.body() == [0, 1, 2, 3, 4]

        cleanup:
        client.stop()
        client.close()
    }

    @Requires(property = 'spec.name', value = 'CompressedRequest')
    @Controller('/gzip/request')
    static class StreamController {

        @Post("/numbers")
        @SingleResult
        Publisher<List<Long>> numbers(@Header MediaType contentType, @Body Publisher<List<Long>> numbers) {
            assert contentType == MediaType.APPLICATION_JSON_TYPE
            numbers
        }
    }

    byte[] gzip(String s) {
        def targetStream = new ByteArrayOutputStream()
        def zipStream = new GZIPOutputStream(targetStream)
        zipStream.write(s.getBytes('UTF-8'))
        zipStream.close()
        byte[] zippedBytes = targetStream.toByteArray()
        targetStream.close()
        zippedBytes
    }
}
