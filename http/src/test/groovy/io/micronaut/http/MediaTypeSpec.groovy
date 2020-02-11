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
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

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
        mediaType.parameters == OptionalValues.of(String, expectedParams)
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
        contentType                 | expected
        "application/hal+xml;q=1.1" | true
        "application/hal+xml;q=1.1" | true
        "application/hal+json"      | true
        "application/hal+xml"       | true
        "application/json"          | true
        "application/xml"           | true
        "text/html;charset=utf-8"   | true
        "text/foo"                  | true
        "application/hal+text"      | true
        "application/javascript"    | true
        "image/png"                 | false
        "image/jpg"                 | false
        "multipart/form-data"       | false
        "application/x-json-stream" | false
        "invalid"                   | false
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2746")
    void "test creating a media type with params"() {
        when:
        MediaType mt = new MediaType("application/json", ["charset": StandardCharsets.UTF_8.name()])

        then:
        noExceptionThrown()
        mt.getParameters().get("charset").get() == "UTF-8"
    }

    @Unroll
    void "test order types: #commaSeparatedList"() {
        given:
        List<MediaType> orderedList = MediaType.of(commaSeparatedList.split(',')).toList().sort()

        expect:
        orderedList.size == expectedList.size
        for (int i = 0; i < orderedList.size(); i++) {
            assert (orderedList.get(i).equals(expectedList.get(i)) == true)
        }

        where:
        commaSeparatedList                                | expectedList
        "audio/basic;q=.5, application/json"              | [new MediaType("application/json"), new MediaType("audio/basic;q=.5")]
        "text/html"                                       | [new MediaType("text/html")]
        "*/*, text/*, text/html"                          | [new MediaType("text/html"), new MediaType("text/*"), new MediaType("*/*")]
        "text/html;level=1, text/html;level=2;q=.3"       | [new MediaType("text/html;level=1"), new MediaType("text/html;level=2;q=.3")]
        "text/*;blah=1, text/html;q=.3, audio/basic;q=.4" | [new MediaType("audio/basic;q=.4"), new MediaType("text/html;q=.3"), new MediaType("text/*;blah=1")]
        "text/plain, text/html, application/json;q=1"     | [new MediaType("text/plain"), new MediaType("text/html"), new MediaType("application/json;q=1")]
    }

    @Unroll
    void "test type match #desiredType"() {
        given:
        boolean match = new MediaType(desiredType).matches(new MediaType(expectedType))

        expect:
        match == expectedMatch

        where:
        desiredType             | expectedType          | expectedMatch
        "text/html"             | "text/html"           | true
        "text/*"                | "text/html"           | true
        "*/*"                   | "application/xml"     | true
        "text/plain"            | "text/hml"            | false
        "text/*"                | "application/json"    | false
    }
}
