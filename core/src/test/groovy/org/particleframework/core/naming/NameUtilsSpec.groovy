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
package org.particleframework.core.naming

import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NameUtilsSpec extends Specification {

    void "test hyphenate"() {
        expect:
        NameUtils.hyphenate(value) == result

        where:
        value               | result
        "com.fooBar.FooBar" | "com.foo-bar.foo-bar"
        "FooBar"            | "foo-bar"
        "com.bar.FooBar"    | "com.bar.foo-bar"
        "Foo"               | 'foo'
        "FooBBar"           | 'foo-bbar'
    }

    void "test hyphenate no lower case"() {
        expect:
        NameUtils.hyphenate(value, false) == result

        where:
        value               | result
        "com.fooBar.FooBar" | "com.foo-Bar.Foo-Bar"
        "FooBar"            | "Foo-Bar"
        "com.bar.FooBar"    | "com.bar.Foo-Bar"
        "Foo"               | 'Foo'
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
}
