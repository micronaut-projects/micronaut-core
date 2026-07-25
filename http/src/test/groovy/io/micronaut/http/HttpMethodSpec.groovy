/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http

import spock.lang.Specification
import spock.lang.Unroll

class HttpMethodSpec extends Specification {

    @Unroll
    void "#method.isSafe() == #expected (RFC 9110 §9.2.1)"() {
        expect:
        method.isSafe() == expected

        where:
        method                | expected
        HttpMethod.GET        | true
        HttpMethod.HEAD       | true
        HttpMethod.OPTIONS    | true
        HttpMethod.TRACE      | true
        HttpMethod.PUT        | false
        HttpMethod.DELETE     | false
        HttpMethod.POST       | false
        HttpMethod.PATCH      | false
        HttpMethod.CONNECT    | false
        HttpMethod.CUSTOM     | false
    }

    @Unroll
    void "#method.isIdempotent() == #expected (RFC 9110 §9.2.2)"() {
        expect:
        method.isIdempotent() == expected

        where:
        method                | expected
        HttpMethod.GET        | true
        HttpMethod.HEAD       | true
        HttpMethod.OPTIONS    | true
        HttpMethod.TRACE      | true
        HttpMethod.PUT        | true
        HttpMethod.DELETE     | true
        HttpMethod.POST       | false
        HttpMethod.PATCH      | false
        HttpMethod.CONNECT    | false
        HttpMethod.CUSTOM     | false
    }

    void "every safe method is also idempotent"() {
        expect:
        HttpMethod.values().every { !it.isSafe() || it.isIdempotent() }
    }
}
