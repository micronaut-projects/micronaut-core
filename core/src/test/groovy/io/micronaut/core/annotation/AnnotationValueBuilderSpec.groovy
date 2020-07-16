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
        val   | type
        1     | Integer
        1L    | Long
        true  | Boolean
        "str" | String
    }
}
