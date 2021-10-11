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
package io.micronaut.http.server.netty.consumes

import io.micronaut.context.ApplicationContext
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ContentTypeHeaderSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared HttpClient client = embeddedServer.applicationContext.createBean(
            HttpClient,
            embeddedServer.getURL()
    )

    void "test that content type header is ignored for methods that don't support a body"() {
        when:"A request is sent with a content type header"
        HttpResponse<String> resp = client.toBlocking().exchange(
                HttpRequest.GET("/test/content-type/get")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML), String
        )

        then:"The request succeeds, the content-type header is ignored"
        resp.status() == HttpStatus.OK
        resp.body() == 'ok'
    }

    void "test that content type header is ignored when it contains an empty string"() {
        when:"A request is sent with a content type header"
        HttpResponse<String> resp = client.toBlocking().exchange(
                HttpRequest.GET("/test/content-type/get")
                        .header(HttpHeaders.CONTENT_TYPE, ""), String
        )

        then:"The request succeeds, the content-type header is ignored"
        resp.status() == HttpStatus.OK
        resp.body() == 'ok'
    }


    @Controller("/test/content-type")
    static class ContentTypeController {

        @Get("/get")
        String get() {
            return "ok"
        }

    }

}
