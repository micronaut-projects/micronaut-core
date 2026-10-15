/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.annotationbuilders

import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.core.annotation.RegisterAnnotations
import spock.lang.Specification

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

enum GroovyShade { LIGHT, DARK }

@Retention(RetentionPolicy.RUNTIME)
@interface GroovyTag {
    String value() default "none"

    int weight() default 1
}

@Retention(RetentionPolicy.RUNTIME)
@interface GroovySample {
    String name()

    int count() default 3

    boolean flag() default true

    GroovyShade shade() default GroovyShade.DARK

    Class<?> type() default Object

    String[] names() default ["a", "b"]

    int[] numbers() default [1, 2]

    GroovyTag tag() default @GroovyTag("default")

    GroovyTag[] tags() default [@GroovyTag("one")]
}

@RegisterAnnotations([GroovySample, GroovyTag])
class GroovySampleBuilders {
}

@GroovyTag
// the tags are given: Groovy compiles the default of a nested annotation array without its members
@GroovySample(name = "jvm", count = 9, tag = @GroovyTag("jvm-tag"), tags = [@GroovyTag("t")])
class GroovyAnnotated {
}

class AnnotationBuildersGroovySpec extends Specification {

    def "builds from members and defaults"() {
        given:
        def registry = AnnotationBuilderRegistry.shared()

        when:
        def sample = registry.build(GroovySample, [name: "n", shade: "LIGHT", tag: [value: "nested"]])

        then:
        sample.name() == "n"
        sample.count() == 3
        sample.flag()
        sample.shade() == GroovyShade.LIGHT
        sample.type() == Object
        sample.names() == ["a", "b"] as String[]
        sample.numbers() == [1, 2] as int[]
        sample.tag().value() == "nested"
        sample.tag().weight() == 1
        sample.tags()*.value() == ["one"]
    }

    def "equals the annotation of the JVM"() {
        given:
        def jvm = GroovyAnnotated.getAnnotation(GroovySample)
        def built = AnnotationBuilderRegistry.shared().build(GroovySample, [name: "jvm", count: 9, tag: [value: "jvm-tag"], tags: [[value: "t"]]])

        expect:
        jvm == built
        built == jvm
    }
}
