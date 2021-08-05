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
package io.micronaut.core.annotation

import spock.lang.Specification
import spock.lang.Unroll

import java.lang.annotation.RetentionPolicy

class AnnotationValueBuilderSpec extends Specification {

    void "test class value"() {
        given:
        def av = AnnotationValue.builder("Foo")
                .value(String.class)
                .build()

        expect:
        av.getValue(String.class).isPresent()
        av.getValue(AnnotationClassValue.class).isPresent()
    }

    void "test default retention policy is runtime value"() {
        given:
        def av = AnnotationValue.builder("Foo")
                .value(String.class)
                .build()

        expect:
        av.retentionPolicy == RetentionPolicy.RUNTIME
    }

    void "test custom retention policy"() {
        given:
        def av = AnnotationValue.builder(AnnotationValue.builder("Foo")
                .value(String.class)
                .build(), RetentionPolicy.SOURCE).build()

        expect:
        av.retentionPolicy == RetentionPolicy.SOURCE
    }

    @Unroll
    void "test value for #type"() {
        given:
        def av = AnnotationValue.builder("Foo")
                .value(val)
                .build()

        expect:
        av.getValue(type).isPresent()
        av.getValue(type).get() == val

        where:
        val                           | type
        true                          | Boolean
        20 as Byte                    | Byte
        'c' as char                   | Character
        1.1 as double                 | Double
        1.1 as float                  | Float
        1                             | Integer
        1L                            | Long
        "str"                         | String
        1 as short                    | Short
        String                        | Class
        Introspected.AccessKind.FIELD | Enum
    }

    @Unroll
    void "test member for #type"() {
        given:
        def av = AnnotationValue.builder("Foo")
                .member("foo", val)
                .build()

        expect:
        av.get("foo", type).isPresent()
        av.get("foo", type).get() == val
        (method == 'enum' ? av.enumValue("foo", Introspected.AccessKind) : av."${method}Value"("foo")).isPresent()

        where:
        val                           | method    | type
        true                          | "boolean" | Boolean
        20 as Byte                    | "byte"    | Byte
        'c' as char                   | "char"    | Character
        Double.MAX_VALUE              | "double"  | Double
        Float.MAX_VALUE               | "float"   | Float
        Integer.MAX_VALUE             | "int"     | Integer
        Long.MAX_VALUE                | "long"    | Long
        Short.MAX_VALUE               | "short"   | Short
        "str"                         | "string"  | String
        String                        | "class"   | Class
        Introspected.AccessKind.FIELD | "enum"    | Enum
    }

    @Unroll
    void "test member for array type #type"() {
        given:
        def av = AnnotationValue.builder("Foo")
                .member("foo", val)
                .build()

        expect:
        av.get("foo", type).isPresent()
        av.get("foo", type).get() == val
        (method == 'enum' ? av.enumValues("foo", Introspected.AccessKind) : av."${method}Values"("foo")) == val

        where:
        val                                       | method    | type
        [true] as boolean[]                       | "boolean" | boolean[].class
        [20 as byte] as byte[]                    | "byte"    | byte[].class
        ['c' as char] as char[]                   | "char"    | char[].class
        [Double.MAX_VALUE] as double[]            | "double"  | double[].class
        [Float.MAX_VALUE] as float[]              | "float"   | float[].class
        [Integer.MAX_VALUE] as int[]              | "int"     | int[].class
        [Long.MAX_VALUE] as long[]                | "long"    | long[].class
        [Short.MAX_VALUE] as short[]              | "short"   | short[].class
        ["str"] as String[]                       | "string"  | String[].class
        [String] as Class[]                       | "class"   | Class[].class
        [Introspected.AccessKind.FIELD] as Enum[] | "enum"    | Enum[].class
    }


    void "test passing a map of members"() {
        when:
        def av = AnnotationValue.builder("Foo")
                .members([
                        "invalid": BigDecimal.valueOf(2)
                ])
                .build()

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "The member named [invalid] with type [java.math.BigDecimal] is not a valid member type"

        when:
        av = AnnotationValue.builder("Foo")
                .members([
                        "multidimensional array": new int[][]{new int[]{1}, new int[]{2}}
                ])
                .build()

        then:
        ex = thrown(IllegalArgumentException)
        ex.message == "The member named [multidimensional array] with type [[[I] is not a valid member type"

        when:
        av = AnnotationValue.builder("Foo")
                .members([
                        "primitive wrapper array": new Long[]{1L}
                ])
                .build()

        then:
        ex = thrown(IllegalArgumentException)
        ex.message == "The member named [primitive wrapper array] with type [[Ljava.lang.Long;] is not a valid member type"

        when:
        av = AnnotationValue.builder("Foo")
                .members([
                        "primitive"             : 1L,
                        "primitive wrapper"     : Long.valueOf(1),
                        "primitive array"       : new int[]{1, 2},
                        "enum"                  : RetentionPolicy.RUNTIME,
                        "enum array"            : new RetentionPolicy[]{
                                RetentionPolicy.RUNTIME,
                                RetentionPolicy.SOURCE
                        },
                        "class"                 : String.class,
                        "class array"           : new Class[]{
                                String.class,
                                Long.class
                        },
                        "annotation class"      : new AnnotationClassValue<>(String.class),
                        "annotation class array": new AnnotationClassValue<>[]{
                                new AnnotationClassValue<>(String.class),
                                new AnnotationClassValue<>(Long.class)
                        },
                        "annotation"            : AnnotationValue.builder("Bar").value(true).build(),
                        "annotation array"      : new AnnotationValue[]{
                                AnnotationValue.builder("Bar").value(true).build(),
                                AnnotationValue.builder("Bar").value(false).build()
                        }
                ])
                .build()

        then:
        noExceptionThrown()
    }
}
