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
import io.micronaut.http.annotation.Head
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ContentTypeHeaderInHeadRequestSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared HttpClient client = embeddedServer.applicationContext.createBean(
            HttpClient,
            embeddedServer.getURL()
    )

    void "test that content type header is present in head request"() {
        when:"A head request is sent"
        HttpResponse<String> resp = client.toBlocking().exchange(
                HttpRequest.HEAD("/test/content-type-head/example"), String
        )

        then:"The request succeeds, the content-type header is present"
        resp.status() == HttpStatus.OK
        resp.body() == null
        resp.contentType.present
        resp.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        resp.contentLength == 10
    }

    @Controller("/test/content-type-head")
    static class ContentTypeController {

        @Head(value = "/example")
        HttpResponse<String> get() {
            return HttpResponse.ok("ok")
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .contentLength(10)
        }

    }

}
