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
package io.micronaut.docs.server.response

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ProducesControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name':'producesspec'
    ], Environment.TEST)

    @AutoCleanup
    @Shared
    HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    def "@Produces allow you to change the Media Type of the response"() {

        when:
        HttpResponse rsp = rxClient.toBlocking().exchange(HttpRequest.GET('/produces'))

        then:
        rsp.status() == HttpStatus.OK
        rsp.getContentType().isPresent() && rsp.getContentType().get() == MediaType.APPLICATION_JSON_TYPE

        when:
        rsp = rxClient.toBlocking().exchange(HttpRequest.GET('/produces/html'))

        then:
        rsp.status() == HttpStatus.OK
        rsp.getContentType().isPresent() && rsp.getContentType().get() == MediaType.TEXT_HTML_TYPE

        when:
        rsp = rxClient.toBlocking().exchange(HttpRequest.GET('/produces/xml'))

        then:
        rsp.status() == HttpStatus.OK
        rsp.getContentType().isPresent() && rsp.getContentType().get() == MediaType.TEXT_XML_TYPE
    }
}
