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
package io.micronaut.core.util

import spock.lang.Specification
import spock.lang.Unroll

class StringUtilsSpec extends Specification {

    @Unroll
    void "test convertDotToUnderscore"() {

        expect:
        result == StringUtils.convertDotToUnderscore(value)

        where:
        value                    | result
        ""                       | ""
        "micronaut.config.files" | "MICRONAUT_CONFIG_FILES"
        "micronaut"              | "MICRONAUT"
        null                     | null
    }

    @Unroll
    void "test prependUri(#base, #uri) == #expected"() {

        expect:
        StringUtils.prependUri(base, uri) == expected

        where:
        base  | uri   | expected
        "a"   | "b"   | "a/b"
        "a/"  | "b"   | "a/b"
        "/a"  | "b"   | "/a/b"
        "/a/" | "b"   | "/a/b"
        "a"   | "b/"  | "a/b/"
        "a/"  | "b/"  | "a/b/"
        "/a"  | "b/"  | "/a/b/"
        "/a/" | "b/"  | "/a/b/"
        "a"   | "/b"  | "a/b"
        "a/"  | "/b"  | "a/b"
        "/a"  | "/b"  | "/a/b"
        "/a/" | "/b"  | "/a/b"
        "a"   | "/b/" | "a/b/"
        "a/"  | "/b/" | "a/b/"
        "/a"  | "/b/" | "/a/b/"
        "/a/" | "/b/" | "/a/b/"
        "/"   | "/b"  | "/b"
    }

    @Unroll
    void "test full prependUri(#base, #uri) == #expected"() {

        expect:
        StringUtils.prependUri("http://" + base, uri) == "http://" + expected

        where:
        base  | uri   | expected
        "a"   | "b"   | "a/b"
        "a/"  | "b"   | "a/b"
        "a"   | "b/"  | "a/b/"
        "a/"  | "b/"  | "a/b/"
        "a"   | "/b"  | "a/b"
        "a/"  | "/b"  | "a/b"
        "a"   | "/b/" | "a/b/"
        "a/"  | "/b/" | "a/b/"
    }

    @Unroll
    void 'test trim the string "#input" == #expected'() {
        expect:
        StringUtils.trimToNull(input) == expected

        where:
        input | expected
        'a'   | 'a'
        ' a ' | 'a'
        '  '  | null
        ''    | null
        null  | null
    }
}
