/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.binding

import okhttp3.Request
import org.particleframework.core.convert.format.Format
import org.particleframework.http.HttpHeaders
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Header
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.annotation.Controller
import org.particleframework.web.router.annotation.Get
import spock.lang.Shared
import spock.lang.Unroll

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HeaderBindingSpec extends AbstractParticleSpec {

    @Shared
    ZonedDateTime timeNow = ZonedDateTime.now()

    @Shared
    String now = timeNow.format(DateTimeFormatter.RFC_1123_DATE_TIME)

    @Shared
    String formatted = timeNow.format(DateTimeFormatter.ofPattern('dd/MM/yyy'))

    @Unroll
    void "test bind HTTP headers for URI #uri"() {
        expect:
        def request = new Request.Builder()
                .url("$server$uri")

        for (header in headers) {
            request = request.header(header.key, header.value)
        }
        client.newCall(
                request.build()
        ).execute().body().string() == result



        where:
        uri                     | result                       | headers
        '/header/formattedDate' | "Header: $formatted"         | ['Date': formatted]
        '/header/optional'      | "Header: application/json"   | ['Content-Type': 'application/json']
        '/header/optional'      | "Header: Not-Present"        | [:]
        '/header/date'          | "Header: $now"               | ['Date': now]
        '/header/withMediaType' | "Header: application/json"   | ['Content-Type': 'application/json']
        '/header/all'           | "Header: application/json"   | ['Content-Type': 'application/json']
        '/header/multiple'      | "Header: [application/json]" | ['Content-Type': 'application/json']
        '/header/simple'        | "Header: application/json"   | ['Content-Type': 'application/json']
        '/header/withValue'     | "Header: application/json"   | ['Content-Type': 'application/json']

    }

    @Controller
    static class HeaderController {

        @Get
        String simple(@Header String contentType) {
            "Header: $contentType"
        }

        @Get
        String optional(@Header Optional<MediaType> contentType) {
            "Header: ${contentType.map({ it.name }).orElse('Not-Present')}"
        }

        @Get
        String date(@Header ZonedDateTime date) {
            "Header: ${date.format(DateTimeFormatter.RFC_1123_DATE_TIME)}"
        }

        @Get
        String formattedDate(@Format('dd/MM/yyy') @Header LocalDate date) {
            "Header: ${date.format(DateTimeFormatter.ofPattern('dd/MM/yyy'))}"
        }

        @Get
        String multiple(@Header List<String> contentType) {
            "Header: $contentType"
        }

        @Get
        String withValue(@Header(HttpHeaders.CONTENT_TYPE) String contentType) {
            "Header: $contentType"
        }

        @Get
        String withMediaType(@Header MediaType contentType) {
            "Header: $contentType"
        }

        @Get
        String all(HttpHeaders httpHeaders) {
            "Header: ${httpHeaders.get(HttpHeaders.CONTENT_TYPE)}"
        }
    }
}
