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

import io.micronaut.core.value.OptionalValues
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class MediaTypeSpec extends Specification {

    void "test media type"() {
        given:
        MediaType mediaType = new MediaType(fullName, ext, parameters)

        expect:
        mediaType.toString() == fullName
        mediaType.name == expectedName
        mediaType.extension == expectedExt
        mediaType.parameters == OptionalValues.of(String,expectedParams)
        mediaType.qualityAsNumber == quality
        mediaType.subtype == subtype
        mediaType.type == type

        where:
        fullName                    | ext   | parameters | expectedName           | expectedExt | expectedParams     | quality | subtype    | type
        "application/hal+xml;q=1.1" | null  | null       | "application/hal+xml"  | 'xml'       | [q: "1.1"]         | 1.1     | 'hal+xml'  | "application"
        "application/hal+xml;q=1.1" | 'foo' | null       | "application/hal+xml"  | 'foo'       | [q: "1.1"]         | 1.1     | 'hal+xml'  | "application"
        "application/hal+json"      | null  | null       | "application/hal+json" | 'json'      | [:]                | 1.0     | 'hal+json' | "application"
        "application/hal+xml"       | null  | null       | "application/hal+xml"  | 'xml'       | [:]                | 1.0     | 'hal+xml'  | "application"
        "application/json"          | null  | null       | "application/json"     | 'json'      | [:]                | 1.0     | 'json'     | "application"
        "text/html;charset=utf-8"   | null  | null       | "text/html"            | 'html'      | [charset: "utf-8"] | 1.0     | 'html'     | "text"
    }

    void "test equals case insensitive"() {
        given:
        MediaType mediaType1 = new MediaType("application/json")
        MediaType mediaType2 = new MediaType("application/JSON")

        expect:
        mediaType1 == mediaType2
    }

    void "test equals ignores params"() {
        given:
        MediaType mediaType1 = new MediaType("application/json")
        MediaType mediaType2 = new MediaType("application/json;charset=utf-8")

        expect:
        mediaType1 == mediaType2
    }

    @Unroll
    void "test #contentType is compressible = #expected"() {
        expect:
        MediaType.isTextBased(contentType) == expected

        where:
        contentType                  | expected
        "application/hal+xml;q=1.1"  | true
        "application/hal+xml;q=1.1"  | true
        "application/hal+json"       | true
        "application/hal+xml"        | true
        "application/json"           | true
        "application/xml"            | true
        "text/html;charset=utf-8"    | true
        "text/foo"                   | true
        "application/hal+text"       | true
        "application/javascript"     | true
        "image/png"                  | false
        "image/jpg"                  | false
        "multipart/form-data"        | false
        "application/x-json-stream"  | false
        "invalid"                    | false
    }
}
