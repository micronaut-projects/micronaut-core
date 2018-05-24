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
package io.micronaut.http.client

import io.micronaut.http.HttpRequest
import spock.lang.Specification

class BasicAuthSpec extends Specification {

    def "basicAuth() sets Authorization Header with Basic base64(username:password)"() {
        when:
        HttpRequest request = HttpRequest.GET("/home").basicAuth('sherlock', 'password')

        then:
        request.headers.get('Authorization')
        request.headers.get('Authorization') == "Basic ${'sherlock:password'.bytes.encodeBase64().toString()}"
    }
}
