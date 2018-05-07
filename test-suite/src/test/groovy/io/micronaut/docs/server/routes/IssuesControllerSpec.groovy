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
package io.micronaut.docs.server.routes

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IssuesControllerSpec extends Specification{
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    // tag::badrequest[]
    void "/issues/show/{number} with an invalid Integer number responds 400"() {
        when:
        client.toBlocking().exchange("/issues/hello")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 400
    }
    // end::badrequest[]

    // tag::notfound[]
    void "/issues/show/{number} without number responds 404"() {
        when:
        client.toBlocking().exchange("/issues/")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 404
    }
    // end::notfound[]
}
