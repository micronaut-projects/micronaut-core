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
package io.micronaut.ast.groovy.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationType
import io.micronaut.core.annotation.AnnotationTypeMember
import io.micronaut.core.annotation.AnnotationValue
import jakarta.inject.Named
import jakarta.inject.Qualifier

import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

class AnnotationTypeSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package grdesc

import io.micronaut.core.annotation.RegisterAnnotations
import java.lang.annotation.Annotation
import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Repeatable
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

enum Shade { LIGHT, DARK }

interface Checker {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@interface Checked {
    Class<? extends Checker>[] by()
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface OverridesAttribute {
    Class<? extends Annotation> constraint()
    String name() default ""
}

@Retention(RetentionPolicy.RUNTIME)
@interface Tag {
    String value() default "none"
    int weight() default 1
}

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD, ElementType.FIELD])
@interface Several {
    Sample[] value()
}

@Documented
@Inherited
@Checked(by = Checker)
@Tag("composed")
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD, ElementType.FIELD])
@Repeatable(Several)
@interface Sample {
    String name()
    @OverridesAttribute(constraint = Tag, name = "value")
    String message() default ""
    int count() default 3
    long big() default 4L
    boolean flag() default true
    double ratio() default 0.5d
    float fraction() default 1.5f
    Class<?> type() default Object
    Class<?>[] types() default [String]
    Shade shade() default Shade.DARK
    Shade[] shades() default [Shade.LIGHT]
    String[] names() default ["a", "b"]
    int[] numbers() default [1, 2]
    Tag tag() default @Tag("default")
    Tag[] tags() default [@Tag("one")]
    int required()
}

@interface Plain {
    String value()
}

@RegisterAnnotations([Sample, Plain, jakarta.inject.Named, java.lang.Deprecated])
class Holder {
}
'''

    private static AnnotationType describe(ClassLoader classLoader, String annotation) {
        String name = 'grdesc.Holder$' + AnnotationBuilderRegistry.mangle(annotation) + AnnotationBuilderRegistry.BUILDER_SUFFIX
        AnnotationBuilder builder = (AnnotationBuilder) classLoader.loadClass(name).getDeclaredConstructor().newInstance()
        AnnotationType type = builder.describe().orElseThrow()
        assert type.is(builder)
        return type
    }

    void "describes a Groovy annotation type"() {
        given:
        def classLoader = buildClassLoader(SOURCE)
        AnnotationType type = describe(classLoader, 'grdesc.Sample')

        expect:
        type.type == classLoader.loadClass('grdesc.Sample')
        type.retentionPolicy == RetentionPolicy.RUNTIME
        type.targets == [ElementType.TYPE, ElementType.METHOD, ElementType.FIELD] as Set
        type.inherited
        type.repeatableContainer.get() == 'grdesc.Several'
        type.annotationMetadata.stringValue('grdesc.Tag').get() == "composed"
        type.annotationMetadata.classValues('grdesc.Checked', "by")*.name == ['grdesc.Checker']
    }

    void "describes every kind of member with its default and annotations"() {
        given:
        def classLoader = buildClassLoader(SOURCE)
        AnnotationType type = describe(classLoader, 'grdesc.Sample')
        def members = type.members.collectEntries { [it.name, it] }

        expect:
        type.members*.name == ["name", "message", "count", "big", "flag", "ratio", "fraction", "type", "types", "shade",
                               "shades", "names", "numbers", "tag", "tags", "required"]
        members.count.type == int
        members.big.type == long
        members.types.type == Class[]
        members.shades.type.componentType == classLoader.loadClass('grdesc.Shade')
        members.tags.type.componentType == classLoader.loadClass('grdesc.Tag')

        and:
        members.name.defaultState == AnnotationTypeMember.DefaultState.ABSENT
        members.required.defaultState == AnnotationTypeMember.DefaultState.ABSENT
        members.message.defaultState == AnnotationTypeMember.DefaultState.PRESENT
        members.message.defaultValue.get() == ""
        members.count.defaultValue.get() == 3
        members.big.defaultValue.get() == 4L
        members.flag.defaultValue.get() == true
        members.type.defaultValue.get() instanceof AnnotationClassValue
        members.type.defaultValue.get().name == Object.name
        members.types.defaultValue.get()*.name == [String.name]
        members.shade.defaultValue.get() == "DARK"
        members.names.defaultValue.get() == ["a", "b"] as String[]
        (members.tag.defaultValue.get() as AnnotationValue).stringValue().get() == "default"

        and:
        def overrides = members.message.annotationMetadata.getAnnotation('grdesc.OverridesAttribute')
        overrides.classValue("constraint").get().name == 'grdesc.Tag'
        overrides.stringValue("name").get() == "value"
        !type.annotationMetadata.hasAnnotation('grdesc.OverridesAttribute')
    }

    void "describes an annotation without retention, a library annotation and a JDK annotation"() {
        given:
        def classLoader = buildClassLoader(SOURCE)

        when:
        AnnotationType plain = describe(classLoader, 'grdesc.Plain')
        AnnotationType named = describe(classLoader, Named.name)
        AnnotationType deprecated = describe(classLoader, Deprecated.name)

        then:
        plain.retentionPolicy == RetentionPolicy.RUNTIME
        !plain.inherited
        plain.members[0].defaultState == AnnotationTypeMember.DefaultState.ABSENT

        named.type == Named
        named.members[0].defaultValue.get() == ""
        named.annotationMetadata.hasDeclaredAnnotation(Qualifier)

        deprecated.type == Deprecated
        deprecated.members*.name == ["since", "forRemoval"]
        deprecated.members*.defaultValue*.get() == ["", false]
    }
}
