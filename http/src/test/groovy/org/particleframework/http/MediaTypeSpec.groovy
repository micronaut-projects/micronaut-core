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
package org.particleframework.http

import org.particleframework.core.value.OptionalValues
import spock.lang.Specification

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
}
