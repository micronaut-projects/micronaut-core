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
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationType
import io.micronaut.core.annotation.AnnotationTypeMember
import io.micronaut.core.annotation.AnnotationValue
import jakarta.inject.Named
import jakarta.inject.Qualifier

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.RetentionPolicy

class AnnotationTypeSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package desc;

import io.micronaut.core.annotation.RegisterAnnotations;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@RegisterAnnotations({Holder.Sample.class, Holder.Plain.class, Holder.Hidden.class, jakarta.inject.Named.class, Deprecated.class})
public class Holder {

    public enum Shade { LIGHT, DARK }

    public interface Checker {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    public @interface Checked {
        Class<? extends Checker>[] by();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(OverridesAttribute.Group.class)
    public @interface OverridesAttribute {
        Class<? extends Annotation> constraint();
        String name() default "";

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        @interface Group {
            OverridesAttribute[] value();
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tag {
        String value() default "none";
        int weight() default 1;
    }

    @Documented
    @Inherited
    @Checked(by = Checker.class)
    @Tag("composed")
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Repeatable(Sample.Several.class)
    public @interface Sample {
        String name();
        @OverridesAttribute(constraint = Tag.class, name = "value")
        String message() default "";
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
        @OverridesAttribute(constraint = Tag.class, name = "weight")
        @OverridesAttribute(constraint = Checked.class, name = "by")
        int required();

        @Documented
        @Inherited
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
        @interface Several {
            Sample[] value();
        }
    }

    public @interface Plain {
        String value();
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE_USE)
    public @interface Hidden {
    }
}
'''

    private static AnnotationBuilder builder(ClassLoader classLoader, String annotation) {
        String name = 'desc.Holder$' + AnnotationBuilderRegistry.mangle(annotation) + AnnotationBuilderRegistry.BUILDER_SUFFIX
        return (AnnotationBuilder) classLoader.loadClass(name).getDeclaredConstructor().newInstance()
    }

    private static AnnotationType describe(ClassLoader classLoader, String annotation) {
        AnnotationBuilder builder = builder(classLoader, annotation)
        AnnotationType type = builder.describe().orElseThrow()
        assert type.is(builder)
        return type
    }

    def "describes the annotation type"() {
        given:
        def classLoader = buildClassLoader("desc.Holder", SOURCE)
        def sampleType = classLoader.loadClass('desc.Holder$Sample')

        when:
        AnnotationType type = describe(classLoader, 'desc.Holder$Sample')

        then:
        type.type == sampleType
        type.retentionPolicy == RetentionPolicy.RUNTIME
        type.targets == [ElementType.TYPE, ElementType.METHOD, ElementType.FIELD] as Set
        type.inherited
        type.repeatableContainer.get() == 'desc.Holder$Sample$Several'

        and: "the annotations of the annotation type are processed, without the java.lang.annotation ones"
        type.annotationMetadata.hasDeclaredAnnotation('desc.Holder$Tag')
        type.annotationMetadata.stringValue('desc.Holder$Tag').get() == "composed"
        type.annotationMetadata.classValues('desc.Holder$Checked', "by")*.name == ['desc.Holder$Checker']
        !type.annotationMetadata.hasAnnotation(Documented)
        !type.annotationMetadata.hasAnnotation(Inherited)
    }

    def "describes every kind of member, in declaration order"() {
        given:
        def classLoader = buildClassLoader("desc.Holder", SOURCE)
        AnnotationType type = describe(classLoader, 'desc.Holder$Sample')
        def members = type.members.collectEntries { [it.name, it] }

        expect:
        type.members*.name == ["name", "message", "count", "big", "flag", "ratio", "fraction", "small", "tiny", "letter",
                               "type", "types", "shade", "shades", "names", "numbers", "tag", "tags", "required"]
        members.name.type == String
        members.count.type == int
        members.big.type == long
        members.flag.type == boolean
        members.ratio.type == double
        members.fraction.type == float
        members.small.type == byte
        members.tiny.type == short
        members.letter.type == char
        members.type.type == Class
        members.types.type == Class[]
        members.shade.type == classLoader.loadClass('desc.Holder$Shade')
        members.shades.type.componentType == classLoader.loadClass('desc.Holder$Shade')
        members.names.type == String[]
        members.numbers.type == int[]
        members.tag.type == classLoader.loadClass('desc.Holder$Tag')
        members.tags.type.componentType == classLoader.loadClass('desc.Holder$Tag')
        members.count.asArgument().name == "count"
        type.findMember("count").get().is(members.count)
    }

    def "a member reports its default, or that it is required"() {
        given:
        def classLoader = buildClassLoader("desc.Holder", SOURCE)
        AnnotationType type = describe(classLoader, 'desc.Holder$Sample')
        def members = type.members.collectEntries { [it.name, it] }

        expect:
        members.name.defaultState == AnnotationTypeMember.DefaultState.ABSENT
        !members.name.defaultValue.present
        members.required.defaultState == AnnotationTypeMember.DefaultState.ABSENT

        and: "the defaults have the form the annotation metadata records"
        members.message.defaultState == AnnotationTypeMember.DefaultState.PRESENT
        members.message.defaultValue.get() == ""
        members.count.defaultValue.get() == 3
        members.big.defaultValue.get() == 4L
        members.flag.defaultValue.get() == true
        members.ratio.defaultValue.get() == 0.5d
        members.fraction.defaultValue.get() == 1.5f
        members.small.defaultValue.get() == (byte) 1
        members.tiny.defaultValue.get() == (short) 2
        members.letter.defaultValue.get() == ('x' as char)
        members.type.defaultValue.get() instanceof AnnotationClassValue
        members.type.defaultValue.get().name == Object.name
        members.types.defaultValue.get() instanceof AnnotationClassValue[]
        members.types.defaultValue.get()*.name == [String.name]
        members.shade.defaultValue.get() == "DARK"
        members.shades.defaultValue.get() == ["LIGHT"] as String[]
        members.names.defaultValue.get() == ["a", "b"] as String[]
        members.numbers.defaultValue.get() == [1, 2] as int[]
        (members.tag.defaultValue.get() as AnnotationValue).stringValue().get() == "default"
        (members.tags.defaultValue.get() as AnnotationValue[])*.stringValue()*.get() == ["one"]
    }

    def "the annotations of a member are read from the member"() {
        given:
        def classLoader = buildClassLoader("desc.Holder", SOURCE)
        AnnotationType type = describe(classLoader, 'desc.Holder$Sample')
        def members = type.members.collectEntries { [it.name, it] }

        when:
        def message = members.message.annotationMetadata.getAnnotationValuesByName('desc.Holder$OverridesAttribute')
        def required = members.required.annotationMetadata.getAnnotationValuesByName('desc.Holder$OverridesAttribute')

        then:
        message.size() == 1
        message[0].classValue("constraint").get().name == 'desc.Holder$Tag'
        message[0].stringValue("name").get() == "value"
        required*.stringValue("name")*.get() == ["weight", "by"]
        members.required.asArgument().annotationMetadata.is(members.required.annotationMetadata)
        members.count.annotationMetadata.isEmpty()

        and: "the annotations of the members are not those of the annotation type"
        !type.annotationMetadata.hasAnnotation('desc.Holder$OverridesAttribute')
    }

    def "an annotation without retention or target gets the defaults of the annotation metadata"() {
        given:
        def classLoader = buildClassLoader("desc.Holder", SOURCE)

        when:
        AnnotationType plain = describe(classLoader, 'desc.Holder$Plain')
        AnnotationType hidden = describe(classLoader, 'desc.Holder$Hidden')

        then:
        // as for the annotation metadata, an annotation that declares no retention is kept at runtime
        plain.retentionPolicy == RetentionPolicy.RUNTIME
        plain.targets == EnumSet.complementOf(EnumSet.of(ElementType.TYPE_USE, ElementType.TYPE_PARAMETER))
        !plain.inherited
        !plain.repeatableContainer.present
        plain.members*.name == ["value"]
        plain.annotationMetadata.isEmpty()

        hidden.retentionPolicy == RetentionPolicy.CLASS
        hidden.targets == [ElementType.TYPE_USE] as Set
        hidden.members.isEmpty()
    }

    def "describes a library annotation and a JDK annotation"() {
        given:
        def classLoader = buildClassLoader("desc.Holder", SOURCE)

        when:
        AnnotationType named = describe(classLoader, Named.name)
        AnnotationType deprecated = describe(classLoader, Deprecated.name)

        then:
        named.type == Named
        named.retentionPolicy == RetentionPolicy.RUNTIME
        named.members*.name == ["value"]
        named.members[0].defaultState == AnnotationTypeMember.DefaultState.PRESENT
        named.members[0].defaultValue.get() == ""
        named.annotationMetadata.hasDeclaredAnnotation(Qualifier)

        deprecated.type == Deprecated
        deprecated.retentionPolicy == RetentionPolicy.RUNTIME
        deprecated.targets.contains(ElementType.METHOD)
        deprecated.members*.name == ["since", "forRemoval"]
        deprecated.members*.defaultValue*.get() == ["", false]
    }
}
