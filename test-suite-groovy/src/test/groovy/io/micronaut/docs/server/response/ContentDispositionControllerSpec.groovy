/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ContentDispositionControllerSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ["spec.name": "contentdisposition"],
            Environment.TEST)

    @AutoCleanup
    @Shared
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test attachment with filename"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/content-disposition/report"), String)

        then:
        response.header(HttpHeaders.CONTENT_DISPOSITION) == 'attachment; filename="report.csv"; filename*=utf-8\'\'report.csv'
    }

    void "test inline"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/content-disposition/preview"), String)

        then:
        response.header(HttpHeaders.CONTENT_DISPOSITION) == 'inline'
    }
}
