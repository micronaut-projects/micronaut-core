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

package io.micronaut.core.naming

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NameUtilsSpec extends Specification {
    void "test simple name"() {
        expect:
        NameUtils.getSimpleName(value) == result

        where:
        value               | result
        "com.fooBar.FooBar" | "FooBar"
        "FooBar"            | "FooBar"
        "com.bar.\$FooBar"  | "\$FooBar"
    }

    @Unroll
    void "test camel case value #value"() {
        expect:
        NameUtils.camelCase(value) == result

        where:
        value                             | result
        'micronaut.config-client.enabled' | 'micronaut.configClient.enabled'
        'foo-bar'                         | 'fooBar'
    }


    @Unroll
    void "test hyphenate #value"() {
        expect:
        NameUtils.hyphenate(value) == result

        where:
        value                             | result
        'micronaut.config-client.enabled' | 'micronaut.config-client.enabled'
        "com.fooBar.FooBar"               | "com.foo-bar.foo-bar"
        "FooBar"                          | "foo-bar"
        "com.bar.FooBar"                  | "com.bar.foo-bar"
        "Foo"                             | 'foo'
        "FooBBar"                         | 'foo-bbar'
        "FOO_BAR"                         | 'foo-bar'
        "fooBBar"                         | 'foo-bbar'
    }

    @Unroll
    void "test hyphenate #value lowercase"() {
        expect:
        NameUtils.hyphenate(value, true) == result

        where:
        value                             | result
        'micronaut.config-client.enabled' | 'micronaut.config-client.enabled'
        "com.fooBar.FooBar"               | "com.foo-bar.foo-bar"
        "FooBar"                          | "foo-bar"
        "com.bar.FooBar"                  | "com.bar.foo-bar"
        "Foo"                             | 'foo'
        "FooBBar"                         | 'foo-bbar'
        "FOO_BAR"                         | 'foo-bar'
        "fooBBar"                         | 'foo-bbar'
        "fooBar"                          | 'foo-bar'
    }

    @Unroll
    void "test environment name separate #value"() {
        expect:
        NameUtils.environmentName(value) == result

        where:
        value               | result
        "com.fooBar.FooBar" | "COM_FOO_BAR_FOO_BAR"
        "FooBar"            | "FOO_BAR"
        "com.bar.FooBar"    | "COM_BAR_FOO_BAR"
        "Foo"               | 'FOO'
        "FooBBar"           | 'FOO_BBAR'
        "FOO_BAR"           | 'FOO_BAR'
        "FOO-BAR"           | 'FOO_BAR'
        "foo-bar-baz"       | 'FOO_BAR_BAZ'
        "fooBBar"           | 'FOO_BBAR'
    }

    @Unroll
    void "test hyphenate #value - no lower case"() {
        expect:
        NameUtils.hyphenate(value, false) == result

        where:
        value               | result
        "com.fooBar.FooBar" | "com.foo-Bar.Foo-Bar"
        "FooBar"            | "Foo-Bar"
        "com.bar.FooBar"    | "com.bar.Foo-Bar"
        "Foo"               | 'Foo'
        "FOO_BAR"           | 'FOO-BAR'
        "FooBBar"           | 'Foo-BBar'
    }

    void "test hyphenate no lower case capitalize"() {
        expect:
        NameUtils.capitalize(NameUtils.hyphenate(value, false)) == result

        where:
        value         | result
        "contentType" | "Content-Type"
    }

    void "test dehyphenate"() {
        expect:
        NameUtils.dehyphenate(value) == result

        where:
        value         | result
        "foo-bar"     | "FooBar"
        "foo-bar-baz" | 'FooBarBaz'
        "foo"         | 'Foo'
        "foo-1"       | 'Foo1'
    }

    @Unroll
    void "test extension #filename"() {
        expect:
        NameUtils.extension(filename) == extension

        where:
        filename                   | extension
        "test.xml"                 | "xml"
        "test/one/two.xml"         | "xml"
        "test.one.two.xml"         | "xml"
        "test-one.json"            | "json"
        "three.one-two.properties" | "properties"
        ""                         | ""
        "one/two/three"            | ""
    }

    @Unroll
    void "test filename #path"() {
        expect:
        NameUtils.filename(path) == filename

        where:
        path                       | filename
        "test.xml"                 | "test"
        "/test/one/two.xml"        | "two"
        "test.one.two.xml"         | "test.one.two"
        "test-one.json"            | "test-one"
        "three.one-two.properties" | "three.one-two"
        ""                         | ""
        "one/two/three"            | "three"
    }
}
