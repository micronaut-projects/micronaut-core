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
package io.micronaut.core.naming

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NameUtilsSpec extends Specification {

    @Unroll
    void "test is valid service ID #name"() {
        expect:
        NameUtils.isHyphenatedLowerCase(name) == result

        where:
        name        | result
        "foo-bar"   | true
        "foobar"    | true
        "foo1-bar"  | true
        "Foo-bar"   | false
        "foo1-bar"  | true
        "1foo1-bar" | false
        "Foo1Bar"   | false
        "foo"       | true
    }

    void "test simple name"() {
        expect:
        NameUtils.getSimpleName(value) == result

        where:
        value               | result
        "com.fooBar.FooBar" | "FooBar"
        "FooBar"            | "FooBar"
        "com.bar.\$FooBar"  | "\$FooBar"
    }

    void "test package name"() {
        expect:
        NameUtils.getPackageName(value) == result

        where:
        value               | result
        "com.fooBar.FooBar" | "com.fooBar"
        "FooBar"            | ""
        "com.bar.\$FooBar"  | "com.bar"
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
        value                                       | result
        'gr8crm-notification-service'.toUpperCase() | 'gr8crm-notification-service'
        'gr8-notification-service'.toUpperCase()    | 'gr8-notification-service'
        '8gr8-notification-service'.toUpperCase()   | '8gr8-notification-service'
        '8gr8-notification-s3rv1c3'.toUpperCase()   | '8gr8-notification-s3rv1c3'
        'gr8-7notification-service'.toUpperCase()   | 'gr8-7notification-service'
        'ec55Metadata'                              | 'ec55-metadata'
        'micronaut.config-client.enabled'           | 'micronaut.config-client.enabled'
        "com.fooBar.FooBar"                         | "com.foo-bar.foo-bar"
        "FooBar"                                    | "foo-bar"
        "com.bar.FooBar"                            | "com.bar.foo-bar"
        "Foo"                                       | 'foo'
        "FooBBar"                                   | 'foo-bbar'
        "FOO_BAR"                                   | 'foo-bar'
        "fooBBar"                                   | 'foo-bbar'
        'gr8crm-notification-service'               | 'gr8crm-notification-service'
        'aws.disableEc2Metadata'                    | 'aws.disable-ec2-metadata'
        'aws.disableEcMetadata'                     | 'aws.disable-ec-metadata'
        'aws.disableEc2instanceMetadata'            | 'aws.disable-ec2instance-metadata'
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
        "test.Foo bar"                    | 'test.foo-bar'
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

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/10140')
    @Unroll
    void "test underscore separated #value with lowercase = #lowercase"() {
        expect:
        NameUtils.underscoreSeparate(value, lowercase) == result

        where:
        value               | result                    | lowercase
        // the fix handles this case, where previously it resulted in '__name_Of_Thing'
        "_nameOfThing"      | '_name_Of_Thing'          | false
        "__nameOfThing"     | '_name_Of_Thing'          | false
        "_nameOfThing"      | '_name_of_thing'          | true
        "__nameOfThing"     | '_name_of_thing'          | true
        // the following are passing cases from prior to fix
        "com.fooBar.FooBar" | "com.foo_bar.foo_bar"     | true
        "FooBar"            | "foo_bar"                 | true
        "com.bar.FooBar"    | "com.bar.foo_bar"         | true
        "Foo"               | 'foo'                     | true
        "FOO__BAR"          | 'foo_bar'                 | true
        "FooBBar"           | 'foo_bbar'                | true
        "com.fooBar.FooBar" | "com.foo_Bar.Foo_Bar"     | false
        "FooBar"            | "Foo_Bar"                 | false
        "com.bar.FooBar"    | "com.bar.Foo_Bar"         | false
        "Foo"               | 'Foo'                     | false
        "FOO__BAR"          | 'FOO_BAR'                 | false
        "FooBBar"           | 'Foo_BBar'                | false
    }

    void "test decapitalize"() {
        expect:
        NameUtils.decapitalize(name) == result

        where:
        name    | result
        "Title" | "title"
        "T"     | "t"
        "TiTLE" | "tiTLE"
        "aBCD"  | "aBCD"
        "ABCD"  | "ABCD"
        "aBC"   | "aBC"
        "ABC"   | "ABC"
        "AB"    | "AB"
        "ABc"   | "aBc"
        "S3abc" | "s3abc"
        "S3a"   | "s3a"
    }

    void "test decapitalize returns same ref"() {
        expect:
        NameUtils.decapitalize(name).is(name)

        where:
        name      | _
        ""        | _
        "a"       | _
        "aa"      | _
        "aB"      | _
        "AA"      | _
        "ABCD"    | _
        "a a"     | _
        "abcd ef" | _
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

    @Unroll
    void "test isGetterName #name"() {
        expect:
        NameUtils.isGetterName(name) == getter

        where:
        name     | getter
        "foo"    | false
        "isFoo"  | true
        "isfoo"  | false
        "getFoo" | true
        "getfoo" | false
        "a"      | false
        "get_foo" | true
        'get$foo' | true
    }

    @Unroll
    void "test is valid hypenated property name #name"() {
        expect:
        NameUtils.isValidHyphenatedPropertyName(name) == result

        where:
        name             | result
        "foo-bar"        | true
        "foobar"         | true
        "foo1-bar"       | true
        "Foo-bar"        | false
        "foo1-bar"       | true
        "1foo1-bar"      | true
        "Foo1Bar"        | false
        "fooBar"         | false
        "Foo"            | false
        "foo.bar"        | true
        "foo.bar-baz"    | true
        "foo-bar.baz"    | true
        "fooBar.baz"     | false
        "1foo.2bar"      | true
        "1foo.2bar-3baz" | true
    }

    @Unroll
    void "test is valid environment name #value"() {
        expect:
        NameUtils.isEnvironmentName(value) == result

        where:
        value         | result
        "FOO_BAR"     | true
        "COM_FOO_BAR" | true
        "foo_BAR"     | false
        "FOOBAR"      | true
        "Foo_BAR"     | false
        "FOO-BAR"     | false
    }

    void "test isReaderName (#name, #prefixes)"() {
        expect:
        NameUtils.isReaderName(name, prefixes as String[]) == isValid

        where:
        name      | prefixes        | isValid
        "foo"     | ["get"]         | false
        "isFoo"   | ["get"]         | true
        "isfoo"   | ["get"]         | false
        "getFoo"  | ["get"]         | true
        'get$foo' | ["get"]         | true
        'get_foo' | ["get"]         | true
        "getfoo"  | ["get"]         | false
        "a"       | ["get"]         | false
        "foo"     | ["with"]        | false
        "withFoo" | ["with"]        | true
        "withfoo" | ["with"]        | false
        "isfoo"   | ["with"]        | false
        "isFoo"   | ["with"]        | false
        "foo"     | [""]            | true
        "isfoo"   | [""]            | true
        "isFoo"   | [""]            | true
        "is"      | [""]            | true
        "getFoo"  | ["get", "with"] | true
        "getfoo"  | ["get", "with"] | false
        "withFoo" | ["get", "with"] | true
        "withfoo" | ["get", "with"] | false
    }

    void "test isWriterName (#name, #prefixes)"() {
        expect:
        NameUtils.isWriterName(name, prefixes as String[]) == isValid

        where:
        name      | prefixes        | isValid
        "foo"     | ["set"]         | false
        "setFoo"  | ["set"]         | true
        'set$foo' | ["set"]         | true
        'set_foo' | ["set"]         | true
        "setfoo"  | ["set"]         | false
        "a"       | ["set"]         | false
        "foo"     | ["with"]        | false
        "withFoo" | ["with"]        | true
        "withfoo" | ["with"]        | false
        "foo"     | [""]            | true
        "fooBar"  | [""]            | true
        "isfoo"   | [""]            | true
        "isFoo"   | [""]            | true
        "is"      | [""]            | true
        "setFoo"  | ["set", "with"] | true
        "setfoo"  | ["set", "with"] | false
        "withFoo" | ["set", "with"] | true
        "withfoo" | ["set", "with"] | false
    }

    void "test getPropertyNameForGetter (#getter, #prefixes)"() {
        expect:
        NameUtils.getPropertyNameForGetter(getter, prefixes as String[]) == propertyName

        where:
        getter    | prefixes        | propertyName
        "getFoo"  | ["get"]         | "foo"
        "isFoo"   | ["get"]         | "foo"
        "withFoo" | ["with"]        | "foo"
        "foo"     | [""]            | "foo"
        "isfoo"   | [""]            | "isfoo"
        "isFoo"   | [""]            | "isFoo"
        "is"      | [""]            | "is"
        "getFoo"  | ["get", "with"] | "foo"
        "withFoo" | ["get", "with"] | "foo"
    }

    void "test getPropertyNameForSetter (#setter, #prefixes)"() {
        expect:
        NameUtils.getPropertyNameForSetter(setter, prefixes as String[]) == propertyName

        where:
        setter    | prefixes        | propertyName
        "setFoo"  | ["set"]         | "foo"
        "isFoo"   | ["set"]         | "isFoo"
        "withFoo" | ["with"]        | "foo"
        "foo"     | [""]            | "foo"
        "isfoo"   | [""]            | "isfoo"
        "isFoo"   | [""]            | "isFoo"
        "is"      | [""]            | "is"
        "setFoo"  | ["set", "with"] | "foo"
        "withFoo" | ["set", "with"] | "foo"
    }

    void "test getterNameFor (#name, #prefixes)"() {
        expect:
        NameUtils.getterNameFor(name, prefixes as String[]) == getterName

        where:
        name     | prefixes        | getterName
        "foo"    | ["get"]         | "getFoo"
        "fooBar" | ["get"]         | "getFooBar"
        "fooBar" | [""]            | "fooBar"
        "fooBar" | ["is"]          | "isFooBar"
        "fooBar" | ["with"]        | "withFooBar"
        "fooBar" | ["set", "with"] | "setFooBar"
        "fooBar" | ["with", "set"] | "withFooBar"
    }

    void "test setterNameFor (#name, #prefixes)"() {
        expect:
        NameUtils.setterNameFor(name, prefixes as String[]) == setterName

        where:
        name     | prefixes        | setterName
        "foo"    | ["set"]         | "setFoo"
        "fooBar" | ["set"]         | "setFooBar"
        "fooBar" | [""]            | "fooBar"
        "fooBar" | ["is"]          | "isFooBar"
        "fooBar" | ["with"]        | "withFooBar"
        "fooBar" | ["set", "with"] | "setFooBar"
        "fooBar" | ["with", "set"] | "withFooBar"
    }

}
