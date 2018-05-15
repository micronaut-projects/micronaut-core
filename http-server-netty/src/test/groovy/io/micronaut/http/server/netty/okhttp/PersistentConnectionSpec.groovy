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
package io.micronaut.http.server.netty.okhttp

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class PersistentConnectionSpec extends Specification {

    void "test okhttp persistent connections work"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        OkHttpClient client = new OkHttpClient()

        when:
        def builder = new Request.Builder().url(
                new URL(embeddedServer.getURL(), "/persistent-connection")
        ).header(HttpHeaders.CONNECTION, HttpHeaderValues.KEEP_ALIVE.toString())
        Response response = client.newCall(builder.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == '{"name":"Fred"}'
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        !response.header(HttpHeaderNames.CONNECTION.toString())

        when:
        response = client.newCall(builder.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == '{"name":"Fred"}'
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        !response.header(HttpHeaderNames.CONNECTION.toString())

        cleanup:
        embeddedServer

    }

    @Controller("/persistent-connection")
    static class TestController {
        @Get("/")
        Test index() {
            return new Test(name: "Fred")
        }
    }

    static class Test {
        String name
    }
}
