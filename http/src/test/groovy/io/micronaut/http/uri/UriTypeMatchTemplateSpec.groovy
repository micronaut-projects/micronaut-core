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
package io.micronaut.http.uri

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class UriTypeMatchTemplateSpec extends Specification {

    @Unroll
    void "Test URI template #template matches #uri when nested with #nested"() {
        given:
        UriTypeMatchTemplate matchTemplate = new UriTypeMatchTemplate(template, types as Class[])
        Optional<UriMatchInfo> info = matchTemplate.nest(nested, nestedTypes as Class[]).match(uri)

        expect:
        info.isPresent() == matches
        info.orElse(null)?.variables == variables


        where:
        template      | uri                     | nested                | matches | variables                | types  | nestedTypes
        "/books{/id}" | "/books/1/authors/2"    | '/authors{/authorId}' | true    | [id: '1', authorId: '2'] | [Long] | [Long]
        "/books{/id}" | "/books/test/authors/2" | '/authors{/authorId}' | false   | null                     | [Long] | [Long]
        "/books{/id}" | "/books/1/authors/test" | '/authors{/authorId}' | false   | null                     | [Long] | [Long]

    }

    @Unroll
    void "Test URI template #template matches #uri"() {
        given:
        UriMatchTemplate matchTemplate = new UriTypeMatchTemplate(template, types as Class[])
        Optional<UriMatchInfo> info = matchTemplate.match(uri)

        expect:
        info.isPresent() == matches
        info.orElse(null)?.variables == variables


        where:
        template                         | uri                  | matches | variables                | types
        "/books{/id}/authors{/authorId}" | "/books/1/authors/2" | true    | [id: '1', authorId: '2'] | [Integer, Long]
        "/books/{id}"                    | '/books/1'           | true    | [id: '1']                | [Long]
        "/books/{id}"                    | '/books/test'        | false   | null                     | [Long]
        "/books/{id}"                    | '/books/1.1'         | false   | null                     | [Integer]
        "/books"                         | "/books"             | true    | [:]                      | null
    }
}

