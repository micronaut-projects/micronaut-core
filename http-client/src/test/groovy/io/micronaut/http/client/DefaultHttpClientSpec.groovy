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

import io.micronaut.http.client.loadbalance.FixedLoadBalancer
import spock.lang.Specification
import spock.lang.Unroll

class DefaultHttpClientSpec extends Specification {

    @Unroll
    def "Host header for #url is #expected"() {
        given:
        def balancer = new FixedLoadBalancer(new URL(url))
        def configuration = new DefaultHttpClientConfiguration()

        when:
        def header = new DefaultHttpClient(balancer, configuration).getHostHeader(new URI(url))

        then:
        header == expected

        where:
        url                                               | expected
        "https://slave1-6x8-build-agent-2.0.1-5h7sl:8080" | "slave1-6x8-build-agent-2.0.1-5h7sl:8080"
        "https://foo_bar:8080"                            | "foo_bar:8080"
        "https://foobar:8080"                             | "foobar:8080"
        "http://foobar:8080"                              | "foobar:8080"
        "http://foobar"                                   | "foobar"
        "http://foobar:80"                                | "foobar"
        "https://foobar:443"                              | "foobar"
        "https://service.url.com"                         | "service.url.com"
        "https://service.url.com:91"                      | "service.url.com:91"
    }

}
