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
package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueProvider

class AnnotationBuildersSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.core.annotation.AnnotationBuilders;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@AnnotationBuilders({Holder.Sample.class, Holder.Tag.class})
public class Holder {

    public enum Shade { LIGHT, DARK }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tag {
        String value() default "none";
        int weight() default 1;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Sample {
        String name();
        int count() default 3;
        long big() default 4L;
        boolean flag() default true;
        double ratio() default 0.5;
        float fraction() default 1.5f;
        byte small() default 1;
        short tiny() default 2;
        char letter() default 'x';
        Class<?> type() default Object.class;
        Class<?>[] types() default {String.class};
        Shade shade() default Shade.DARK;
        Shade[] shades() default {Shade.LIGHT};
        String[] names() default {"a", "b"};
        int[] numbers() default {1, 2};
        Tag tag() default @Tag("default");
        Tag[] tags() default {@Tag("one")};
    }
}

@Holder.Sample(name = "jvm", count = 9)
class Annotated {
}
'''

    /**
     * The generated builder, loaded by its name: the class loader of a test cannot list the service entries.
     */
    private static AnnotationBuilder builder(ClassLoader classLoader, String holder, String annotation) {
        String name = holder + '$' + AnnotationBuilderRegistry.mangle(annotation) + AnnotationBuilderRegistry.BUILDER_SUFFIX
        return (AnnotationBuilder) classLoader.loadClass(name).getDeclaredConstructor().newInstance()
    }

    def "generates the builders and the implementations"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")
        def sampleType = classLoader.loadClass("test.Holder\$Sample")
        def shade = classLoader.loadClass("test.Holder\$Shade")

        when:
        def sample = builder.build([name: "n", count: "7", shade: "LIGHT", type: "java.lang.Long",
                                    names: "p,q", numbers: [3, "4"], shades: ["DARK", "LIGHT"]])

        then:
        builder.annotationType() == sampleType
        sample.name() == "n"
        sample.count() == 7
        sample.big() == 4L
        sample.flag()
        sample.ratio() == 0.5d
        sample.fraction() == 1.5f
        sample.small() == (byte) 1
        sample.tiny() == (short) 2
        sample.letter() == (char) 'x'
        sample.type() == Long
        sample.types() == [String] as Class[]
        sample.shade().name() == "LIGHT"
        sample.shades()*.name() == ["DARK", "LIGHT"]
        sample.names() == ["p", "q"] as String[]
        sample.numbers() == [3, 4] as int[]
        shade.isInstance(sample.shade())
        sample.annotationType() == sampleType
    }

    def "arrays are copied and the annotation value has the members"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")

        when:
        def sample = builder.build([name: "n"])
        sample.names()[0] = "changed"
        AnnotationValue<?> value = ((AnnotationValueProvider) sample).annotationValue()

        then:
        sample.names() == ["a", "b"] as String[]
        value.annotationName == "test.Holder\$Sample"
        value.stringValue("name").get() == "n"
        value.intValue("count").asInt == 3
        value.stringValues("names") == ["a", "b"] as String[]
        value.annotationClassValue("type").get().name == "java.lang.Object"
        value.stringValue("shade").get() == "DARK"
        value.toString() == sample.toString()
    }

    def "equals and hash code agree with the annotation of the JVM"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")
        def sampleType = classLoader.loadClass("test.Holder\$Sample")
        def jvm = classLoader.loadClass("test.Annotated").getAnnotation(sampleType)

        when:
        def built = builder.build([name: "jvm", count: 9])
        def other = builder.build([name: "other"])

        then:
        built == jvm
        jvm == built
        built != other
        other != built
        built == built
        built != "not an annotation"
        built.hashCode() == jvm.hashCode()
        built.hashCode() == builder.build([name: "jvm", count: 9]).hashCode()
    }
}
