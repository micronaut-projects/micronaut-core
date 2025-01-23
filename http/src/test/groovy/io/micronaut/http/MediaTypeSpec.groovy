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

    void "test MediaType::of"() {
        expect:
        MediaType.of('multipart/form-data') == MediaType.MULTIPART_FORM_DATA_TYPE

        MediaType.of('application/vnd.github+json') == MediaType.APPLICATION_JSON_GITHUB_TYPE
        MediaType.of('application/feed+json') == MediaType.APPLICATION_JSON_FEED_TYPE
        MediaType.of('application/problem+json') == MediaType.APPLICATION_JSON_PROBLEM_TYPE
        MediaType.of('application/json-patch+json') == MediaType.APPLICATION_JSON_PATCH_TYPE
        MediaType.of('application/merge-patch+json') == MediaType.APPLICATION_JSON_MERGE_PATCH_TYPE
        MediaType.of('application/schema+json') == MediaType.APPLICATION_JSON_SCHEMA_TYPE
        MediaType.of('application/x-www-form-urlencoded') == MediaType.APPLICATION_FORM_URLENCODED_TYPE
        MediaType.of('application/xhtml+xml') == MediaType.APPLICATION_XHTML_TYPE
        MediaType.of('application/xml') == MediaType.APPLICATION_XML_TYPE
        MediaType.of('application/json') == MediaType.APPLICATION_JSON_TYPE
        MediaType.of('application/x-yaml') == MediaType.APPLICATION_YAML_TYPE
        MediaType.of('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') == MediaType.MICROSOFT_EXCEL_OPEN_XML_TYPE
        MediaType.of('application/vnd.ms-excel') == MediaType.MICROSOFT_EXCEL_TYPE
        MediaType.of('application/hal+json') == MediaType.APPLICATION_HAL_JSON_TYPE
        MediaType.of('application/hal+xml') == MediaType.APPLICATION_HAL_XML_TYPE
        MediaType.of('application/atom+xml') == MediaType.APPLICATION_ATOM_XML_TYPE
        MediaType.of('application/vnd.error+json') == MediaType.APPLICATION_VND_ERROR_TYPE
        MediaType.of('application/x-json-stream') == MediaType.APPLICATION_JSON_STREAM_TYPE
        MediaType.of('application/octet-stream') == MediaType.APPLICATION_OCTET_STREAM_TYPE
        MediaType.of('application/graphql') == MediaType.APPLICATION_GRAPHQL_TYPE
        MediaType.of('application/gpx+xml') == MediaType.GPX_XML_TYPE
        MediaType.of('application/zip') == MediaType.ZIP_TYPE
        MediaType.of('application/gzip') == MediaType.GZIP_TYPE
        MediaType.of('application/yang') == MediaType.YANG_TYPE
        MediaType.of('application/rtf') == MediaType.RTF_TYPE
        MediaType.of('application/zlib') == MediaType.ZLIB_TYPE
        MediaType.of('application/zstd') == MediaType.ZSTD_TYPE
        MediaType.of('application/pdf') == MediaType.APPLICATION_PDF_TYPE
        MediaType.of('application/toml') == MediaType.TOML_TYPE
        MediaType.of('application/x-cue') == MediaType.CUE_TYPE

        MediaType.of('text/html') == MediaType.TEXT_HTML_TYPE
        MediaType.of('text/csv') == MediaType.TEXT_CSV_TYPE
        MediaType.of('text/css') == MediaType.TEXT_CSS_TYPE
        MediaType.of('text/xml') == MediaType.TEXT_XML_TYPE
        MediaType.of('text/json') == MediaType.TEXT_JSON_TYPE
        MediaType.of('text/plain') == MediaType.TEXT_PLAIN_TYPE
        MediaType.of('text/markdown') == MediaType.TEXT_MARKDOWN_TYPE
        MediaType.of('text/event-stream') == MediaType.TEXT_EVENT_STREAM_TYPE
        MediaType.of('text/javascript') == MediaType.TEXT_JAVASCRIPT_TYPE
        MediaType.of('text/ecmascript') == MediaType.TEXT_ECMASCRIPT_TYPE

        MediaType.of('image/apng') == MediaType.IMAGE_APNG_TYPE
        MediaType.of('image/bmp') == MediaType.IMAGE_BMP_TYPE
        MediaType.of('image/x-icon') == MediaType.IMAGE_X_ICON_TYPE
        MediaType.of('image/tiff') == MediaType.IMAGE_TIFF_TYPE
        MediaType.of('image/avif') == MediaType.IMAGE_AVIF_TYPE
        MediaType.of('image/svg+xml') == MediaType.IMAGE_SVG_TYPE
        MediaType.of('image/xbm') == MediaType.IMAGE_XBM_TYPE
        MediaType.of('image/png') == MediaType.IMAGE_PNG_TYPE
        MediaType.of('image/jpeg') == MediaType.IMAGE_JPEG_TYPE
        MediaType.of('image/gif') == MediaType.IMAGE_GIF_TYPE
        MediaType.of('image/webp') == MediaType.IMAGE_WEBP_TYPE
        MediaType.of('image/wmf') == MediaType.IMAGE_WMF_TYPE
    }

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
        "application/x-yaml"        | true
        "application/x-cue"         | true
        "application/xml"           | true
        "application/graphql"       | true
        "application/toml"          | true
        "application/yang"          | true
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
        List<MediaType> orderedList = MediaType.orderedOf(commaSeparatedList.split(','))

        expect:
        orderedList.size() == expectedList.size()
        for (int i = 0; i < orderedList.size(); i++) {
            assert orderedList.get(i) == expectedList.get(i)
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

    @Unroll
    void "compare media type"() {
        expect:
            !MediaType.APPLICATION_JSON_TYPE.equals(MediaType.TEXT_XML_TYPE)
            MediaType.APPLICATION_JSON_TYPE != MediaType.TEXT_XML_TYPE
    }
}
