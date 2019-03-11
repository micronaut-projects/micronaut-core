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

class HttpHeadersSpec extends Specification {

    def "HttpHeaders.accept() returns a list of media type for a comma separated string"() {
        when:
        HttpRequest request = HttpRequest.GET("/").header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        List<MediaType> mediaTypeList = request.headers.accept()

        then:
        mediaTypeList
        mediaTypeList.size() == 4

        mediaTypeList.find { it.name == 'text/html' && it.qualityAsNumber == 1.0 }
        mediaTypeList.find { it.name == 'application/xhtml+xml' && it.qualityAsNumber == 1.0 }
        mediaTypeList.find { it.name == 'application/xml' && it.qualityAsNumber == 0.9 }
        mediaTypeList.find { it.name == '*/*' && it.qualityAsNumber == 0.8 }
    }

    def "HttpHeaders.accept() returns a list of media type with one item for application/json"() {
        when:
        HttpRequest request = HttpRequest.GET("/").header("Accept", "application/json")
        List<MediaType> mediaTypeList = request.headers.accept()

        then:
        mediaTypeList
        mediaTypeList.size() == 1

        mediaTypeList.find { it.name == 'application/json' && it.qualityAsNumber == 1.0 }
    }

}