/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.docs.server.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PointControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(
            EmbeddedServer, ['spec.name': 'PointControllerSpec'])
    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test JSON with no @Body endpoint"() {
        given:
        HttpRequest<String> httpRequest = HttpRequest
                .POST("/point/no-body-json", '{"x":10,"y":20}')
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)

        when:
        HttpResponse<Point> response = client.toBlocking().exchange(httpRequest, Point)

        then:
        assertResult(response.body.orElse(null))
    }

    void "test Form data with no @Body endpoint"() {
        given:
        HttpRequest<String> httpRequest = HttpRequest
                .POST("/point/no-body-form", 'x=10&y=20')
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)

        when:
        HttpResponse<Point> response = client.toBlocking().exchange(httpRequest, Point)

        then:
        assertResult(response.body.orElse(null))
    }

    void assertResult(Point p) {
        p
        p.x == 10
        p.y == 20
    }
}
