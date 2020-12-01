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

import java.nio.charset.Charset

class HttpRequestSpec extends Specification {

    def "HttpRequest.getPath() returns the non-decoded URI path component"() {
        when:
        String pathSegment = "?bar"
        String encodedPathSegment = URLEncoder.encode(pathSegment, Charset.defaultCharset().name())
        String path = "http://www.example.org/foo/$encodedPathSegment?queryParam=true"
        HttpRequest request = HttpRequest.GET(path)

        then:
        request.getPath() == "/foo/%3Fbar"
    }

}