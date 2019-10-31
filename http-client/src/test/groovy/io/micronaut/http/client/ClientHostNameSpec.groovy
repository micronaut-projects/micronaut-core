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
package io.micronaut.http.client

import io.micronaut.http.client.exceptions.HttpClientException
import spock.lang.Specification

class ClientHostNameSpec extends Specification {

    void "test host name with underscores"() {
        when:
        def client = HttpClient.create(new URL("https://foo_bar"))
        client.toBlocking().retrieve("/")

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connect Error: foo_bar')

        cleanup:
        client.close()
    }


    void "test host name with underscores and port"() {
        when:
        def client = HttpClient.create(new URL("https://foo_bar:8080"))
        client.toBlocking().retrieve("/")

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connect Error: foo_bar')

        cleanup:
        client.close()
    }

    void "test host name with dots and dashes and port"() {
        when:
        def client = HttpClient.create(new URL("https://slave1-6x8-build-agent-2.0.1-5h7sl:8080"))
        client.toBlocking().retrieve("/")

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Connect Error: slave1-6x8-build-agent-2.0.1-5h7sl')

        cleanup:
        client.close()
    }
}
