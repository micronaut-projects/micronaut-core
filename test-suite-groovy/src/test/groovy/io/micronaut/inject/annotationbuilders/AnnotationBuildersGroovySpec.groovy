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

import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.core.annotation.RegisterAnnotations
import jakarta.inject.Named
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

@Retention(RetentionPolicy.RUNTIME)
@interface GroovyLeaf {
    int value() default 7
}

@Retention(RetentionPolicy.RUNTIME)
@interface GroovyInner {
    String name()

    GroovyLeaf[] leaves() default [@GroovyLeaf]
}

@Retention(RetentionPolicy.RUNTIME)
@interface GroovyOuter {
    GroovyInner[] value()
}

// only the outer annotation is listed: the annotation types of its members get a builder with it
@RegisterAnnotations([GroovyOuter, Named])
class GroovyNestedBuilders {
}

@GroovyOuter([@GroovyInner(name = "a", leaves = [@GroovyLeaf(1), @GroovyLeaf(2)]), @GroovyInner(name = "b", leaves = [@GroovyLeaf])])
@Named("library")
class GroovyNestedAnnotated {
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

    def "reads the members as typed values"() {
        given:
        def builder = AnnotationBuilder.find(GroovySample).orElseThrow()
        def sample = builder.build([name: "n", shade: "LIGHT", tags: [[value: "t1"], [value: "t2"]]])

        when:
        Map<String, Object> members = builder.toMap(sample)

        then:
        members.keySet() as List == ["name", "count", "flag", "shade", "type", "names", "numbers", "tag", "tags"]
        members.count == 3
        members.shade.is(GroovyShade.LIGHT)
        members.type == Object
        members.names.class == String[]
        members.numbers.class == int[]
        members.tag instanceof GroovyTag
        members.tags.class == GroovyTag[]
        members.tags*.value() == ["t1", "t2"]
    }

    def "reads the members of the annotation of the JVM, and its arrays are copies"() {
        given:
        def builder = AnnotationBuilder.find(GroovySample).orElseThrow()
        def jvm = GroovyAnnotated.getAnnotation(GroovySample)

        when:
        Map<String, Object> members = builder.toMap(jvm)
        members.names[0] = "changed"
        members.numbers[0] = 42

        then:
        members.name == "jvm"
        members.count == 9
        jvm.names() == ["a", "b"] as String[]
        jvm.numbers() == [1, 2] as int[]
        builder.toMap(jvm).names == ["a", "b"] as String[]
    }

    def "the annotation types of the members get a builder"() {
        given:
        def outer = AnnotationBuilder.find(GroovyOuter).orElseThrow()
        def leaf = AnnotationBuilder.find(GroovyLeaf).orElseThrow()

        when:
        def built = outer.build([value: [[name: "a", leaves: [[value: 1], [value: 2]]], [name: "b"]]])

        then:
        AnnotationBuilder.find(GroovyInner).isPresent()
        built.value()*.name() == ["a", "b"]
        built.value()[0].leaves()*.value() == [1, 2]
        leaf.toMap(built.value()[1].leaves()[0]) == [value: 7]
        outer.toMap(built).value.class == GroovyInner[]
        outer.build(outer.toMap(built)) == GroovyNestedAnnotated.getAnnotation(GroovyOuter)
    }

    def "an annotation of a library gets a builder"() {
        given:
        def named = AnnotationBuilder.find(Named).orElseThrow()
        def jvm = GroovyNestedAnnotated.getAnnotation(Named)

        expect:
        named.toMap(jvm) == [value: "library"]
        named.build([value: "library"]) == jvm
    }
}
