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
package io.micronaut.http.server.netty.binding

import io.micronaut.core.convert.format.Format
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Header
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import spock.lang.Shared
import spock.lang.Unroll

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HeaderBindingSpec extends AbstractMicronautSpec {

    @Shared
    ZonedDateTime timeNow = ZonedDateTime.now()

    @Shared
    String now = timeNow.format(DateTimeFormatter.RFC_1123_DATE_TIME)

    @Shared
    String formatted = timeNow.format(DateTimeFormatter.ofPattern('dd/MM/yyy'))

    @Unroll
    void "test bind HTTP headers for URI #uri"() {
        expect:
        def request = HttpRequest.GET(uri)
        for (header in headers) {
            request = request.header(header.key, header.value)
        }
        rxClient.retrieve(request).blockingFirst() == result

        where:
        uri                       | result                       | headers
        '/header/formatted-date'  | "Header: $formatted"         | ['Date': formatted]
        '/header/optional'        | "Header: application/json"   | ['Content-Type': 'application/json']
        '/header/optional'        | "Header: Not-Present"        | [:]
        '/header/date'            | "Header: $now"               | ['Date': timeNow.toString()]
        '/header/with-media-type' | "Header: application/json"   | ['Content-Type': 'application/json']
        '/header/all'             | "Header: application/json"   | ['Content-Type': 'application/json']
        '/header/multiple'        | "Header: [application/json]" | ['Content-Type': 'application/json']
        '/header/simple'          | "Header: application/json"   | ['Content-Type': 'application/json']
        '/header/with-value'      | "Header: application/json"   | ['Content-Type': 'application/json']
    }

    @Controller("/header")
    static class HeaderController {

        @Get("/simple")
        String simple(@Header String contentType) {
            "Header: $contentType"
        }

        @Get("/optional")
        String optional(@Header Optional<MediaType> contentType) {
            "Header: ${contentType.map({ it.name }).orElse('Not-Present')}"
        }

        @Get("/date")
        String date(@Header ZonedDateTime date) {
            "Header: ${date.format(DateTimeFormatter.RFC_1123_DATE_TIME)}"
        }

        @Get("/formatted-date")
        String formattedDate(@Format('dd/MM/yyy') @Header LocalDate date) {
            "Header: ${date.format(DateTimeFormatter.ofPattern('dd/MM/yyy'))}"
        }

        @Get("/multiple")
        String multiple(@Header List<String> contentType) {
            "Header: $contentType"
        }

        @Get("/with-value")
        String withValue(@Header(HttpHeaders.CONTENT_TYPE) String contentType) {
            "Header: $contentType"
        }

        @Get("/with-media-type")
        String withMediaType(@Header MediaType contentType) {
            "Header: $contentType"
        }

        @Get("/all")
        String all(HttpHeaders httpHeaders) {
            "Header: ${httpHeaders.get(HttpHeaders.CONTENT_TYPE)}"
        }
    }
}
