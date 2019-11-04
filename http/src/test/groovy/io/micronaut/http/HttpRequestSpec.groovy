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
package io.micronaut.http

import spock.lang.Specification

class HttpRequestSpec extends Specification {

    def "Converting to url and getting host and authority work as expected"() {
        expect:
        new URI("https://foo_bar").toString() == "https://foo_bar"
        HttpRequest.GET("https://localhost.company.com").getUri().toString() == "https://localhost.company.com"
        HttpRequest.GET("https://localhost").getUri().toString() == "https://localhost"
        HttpRequest.GET("https://foo_bar").getUri().toString() == "https://foo_bar"
        HttpRequest.GET("https://slave1-6x8-build-agent-2.0.1-5h7sl").getUri().toString() == "https://slave1-6x8-build-agent-2.0.1-5h7sl"
        HttpRequest.GET("https://slave1-6x8-build-agent-2.0.1-5h7sl:8080").getUri().toString() == "https://slave1-6x8-build-agent-2.0.1-5h7sl:8080"
        HttpRequest.GET(new URI("https://slave1-6x8-build-agent-2.0.1-5h7sl:8080")).getUri().toString() == "https://slave1-6x8-build-agent-2.0.1-5h7sl:8080"
        HttpRequest.GET(new URI("https://slave1-6x8-build-agent-2.0.1-5h7sl:8080")).getUri().getHost() == null
        HttpRequest.GET(new URI("https://slave1-6x8-build-agent-2.0.1-5h7sl:8080")).getUri().getAuthority() == "slave1-6x8-build-agent-2.0.1-5h7sl:8080"
        HttpRequest.GET(new URI("https://slave1-6x8-build-agent-2.0.1-5h7sl")).getUri().getAuthority() == "slave1-6x8-build-agent-2.0.1-5h7sl"
    }
}
