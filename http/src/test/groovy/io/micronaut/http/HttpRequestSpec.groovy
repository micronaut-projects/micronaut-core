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


import io.micronaut.http.cookie.Cookie
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

    void "test request add simple cookie"() {
        given:
            MutableHttpRequest request = HttpRequest.GET("/")

            request.cookie(Cookie.of("foo", "bar"))

        expect:
            request.headers.get(header) == value
            request.getCookies().size() == 1
            request.getCookies().get("foo").value == "bar"

        where:
            header             | value
            HttpHeaders.COOKIE | "foo=bar"
    }

    void "test request add multiple cookies"() {
        given:
            MutableHttpRequest request = HttpRequest.GET("/")

            request.cookies(Set.of(Cookie.of("foo", "bar"), Cookie.of("xyz", "abc")))

        expect:
            request.headers.getAll(header).toSet() == value
            request.getCookies().size() == 2
            request.getCookies().get("foo").value == "bar"
            request.getCookies().get("xyz").value == "abc"

        where:
            header             | value
            HttpHeaders.COOKIE | ["foo=bar", "xyz=abc"] as Set
    }

    void "test response add simple cookie"() {
        given:
            MutableHttpResponse response = HttpResponse.ok()

            response.status(HttpStatus."$status")
            response.cookie(Cookie.of("foo", "bar"))

        expect:
            response.status == HttpStatus."$status"
            response.headers.get(header) == value
            response.getCookies().size() == 1
            response.getCookies().get("foo").value == "bar"
            response.getCookie("foo").get().value == "bar"

        where:
            status        | header                 | value
            HttpStatus.OK | HttpHeaders.SET_COOKIE | "foo=bar"
    }

    void "test response add multiple cookies"() {
        given:
            MutableHttpResponse response = HttpResponse.ok()

            response.status(HttpStatus."$status")
            response.cookies(Set.of(Cookie.of("foo", "bar"), Cookie.of("xyz", "abc")))

        expect:
            response.status == HttpStatus."$status"
            response.headers.getAll(header).toSet() == value
            response.getCookies().size() == 2
            response.getCookies().get("foo").value == "bar"
            response.getCookie("foo").get().value == "bar"
            response.getCookies().get("xyz").value == "abc"
            response.getCookie("xyz").get().value == "abc"

        where:
            status        | header                 | value
            HttpStatus.OK | HttpHeaders.SET_COOKIE | ["foo=bar", "xyz=abc"] as Set
    }

}
