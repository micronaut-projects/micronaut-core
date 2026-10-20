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
package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
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

class AnnotationTypeSpec extends AbstractKotlinCompilerSpec {

    private static final String ANNOTATIONS = '''
package ktdesc

import io.micronaut.core.annotation.RegisterAnnotations
import kotlin.reflect.KClass

enum class Shade { LIGHT, DARK }

interface Checker

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class Checked(val by: Array<KClass<out Checker>>)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class OverridesAttribute(val constraint: KClass<out Annotation>, val name: String = "")

@Retention(AnnotationRetention.RUNTIME)
annotation class Tag(val value: String = "none", val weight: Int = 1)

@MustBeDocumented
@java.lang.annotation.Inherited
@Checked(by = [Checker::class])
@Tag("composed")
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@JvmRepeatable(Sample.Several::class)
annotation class Sample(
    val name: String,
    @OverridesAttribute(constraint = Tag::class, name = "value") val message: String = "",
    val count: Int = 3,
    val big: Long = 4L,
    val flag: Boolean = true,
    val ratio: Double = 0.5,
    val fraction: Float = 1.5f,
    val small: Byte = 1,
    val tiny: Short = 2,
    val letter: Char = 'x',
    val type: KClass<*> = Any::class,
    val types: Array<KClass<*>> = [String::class],
    val shade: Shade = Shade.DARK,
    val shades: Array<Shade> = [Shade.LIGHT],
    val names: Array<String> = ["a", "b"],
    val numbers: IntArray = [1, 2],
    val tag: Tag = Tag("default"),
    val tags: Array<Tag> = [Tag("one")],
    val required: Int
) {
    @MustBeDocumented
    @java.lang.annotation.Inherited
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
    annotation class Several(val value: Array<Sample>)
}

annotation class Plain(val value: String)

@RegisterAnnotations(Sample::class, Plain::class, Named::class, java.lang.Deprecated::class)
class Holder
'''

    private static AnnotationType describe(ClassLoader classLoader, String annotation) {
        String name = 'ktdesc.Holder$' + AnnotationBuilderRegistry.mangle(annotation) + AnnotationBuilderRegistry.BUILDER_SUFFIX
        AnnotationBuilder builder = (AnnotationBuilder) classLoader.loadClass(name).getDeclaredConstructor().newInstance()
        AnnotationType type = builder.describe().orElseThrow()
        assert type.is(builder)
        return type
    }

    private static String source(String uses) {
        return ANNOTATIONS.replace("import kotlin.reflect.KClass", "import jakarta.inject.Named\nimport kotlin.reflect.KClass") + uses
    }

    def "describes a Kotlin annotation class"() {
        given:
        def classLoader = buildClassLoader("ktdesc.Holder", source(""))
        AnnotationType type = describe(classLoader, 'ktdesc.Sample')

        expect:
        type.type == classLoader.loadClass('ktdesc.Sample')
        type.retentionPolicy == RetentionPolicy.RUNTIME
        type.targets == [ElementType.TYPE, ElementType.METHOD, ElementType.FIELD] as Set
        type.inherited
        type.repeatableContainer.get() == 'ktdesc.Sample$Several'
        type.annotationMetadata.stringValue('ktdesc.Tag').get() == "composed"
        type.annotationMetadata.classValues('ktdesc.Checked', "by")*.name == ['ktdesc.Checker']
    }

    def "describes every kind of member, a class reference as a class"() {
        given:
        def classLoader = buildClassLoader("ktdesc.Holder", source(""))
        AnnotationType type = describe(classLoader, 'ktdesc.Sample')
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
        members.shade.type == classLoader.loadClass('ktdesc.Shade')
        members.shades.type.componentType == classLoader.loadClass('ktdesc.Shade')
        members.names.type == String[]
        members.numbers.type == int[]
        members.tag.type == classLoader.loadClass('ktdesc.Tag')
        members.tags.type.componentType == classLoader.loadClass('ktdesc.Tag')

        and:
        def overrides = members.message.annotationMetadata.getAnnotation('ktdesc.OverridesAttribute')
        overrides.classValue("constraint").get().name == 'ktdesc.Tag'
        overrides.stringValue("name").get() == "value"
        !type.annotationMetadata.hasAnnotation('ktdesc.OverridesAttribute')
    }

    def "without a use of the annotation the defaults are unknown, and the members without one required"() {
        given:
        def classLoader = buildClassLoader("ktdesc.Holder", source(""))
        AnnotationType type = describe(classLoader, 'ktdesc.Sample')
        def members = type.members.collectEntries { [it.name, it] }

        expect:
        members.name.defaultState == AnnotationTypeMember.DefaultState.ABSENT
        members.required.defaultState == AnnotationTypeMember.DefaultState.ABSENT
        members.count.defaultState == AnnotationTypeMember.DefaultState.UNKNOWN
        !members.count.defaultValue.present
        members.message.defaultState == AnnotationTypeMember.DefaultState.UNKNOWN

        and: "a Java annotation has no Kotlin constructor to tell a required member"
        describe(classLoader, Named.name).members[0].defaultState == AnnotationTypeMember.DefaultState.UNKNOWN
    }

    def "a use of the annotation shows the defaults"() {
        given:
        def classLoader = buildClassLoader("ktdesc.Holder", source('''

@Sample(name = "used", required = 1)
class Used

@Named("named")
class NamedUse
'''))
        AnnotationType type = describe(classLoader, 'ktdesc.Sample')
        def members = type.members.collectEntries { [it.name, it] }

        expect:
        members.name.defaultState == AnnotationTypeMember.DefaultState.ABSENT
        members.required.defaultState == AnnotationTypeMember.DefaultState.ABSENT
        members.message.defaultState == AnnotationTypeMember.DefaultState.PRESENT
        members.message.defaultValue.get() == ""
        members.count.defaultValue.get() == 3
        members.big.defaultValue.get() == 4L
        members.flag.defaultValue.get() == true
        members.letter.defaultValue.get() == ('x' as char)
        members.type.defaultValue.get() instanceof AnnotationClassValue
        members.type.defaultValue.get().name == Object.name
        members.types.defaultValue.get()*.name == [String.name]
        members.shade.defaultValue.get() == "DARK"
        members.names.defaultValue.get() == ["a", "b"] as String[]
        members.numbers.defaultValue.get() == [1, 2] as int[]
        (members.tag.defaultValue.get() as AnnotationValue).stringValue().get() == "default"

        and:
        describe(classLoader, Named.name).members[0].defaultState == AnnotationTypeMember.DefaultState.PRESENT
        describe(classLoader, Named.name).members[0].defaultValue.get() == ""
    }

    def "an annotation without retention or target gets the defaults of the annotation metadata"() {
        given:
        def classLoader = buildClassLoader("ktdesc.Holder", source(""))
        AnnotationType plain = describe(classLoader, 'ktdesc.Plain')

        expect:
        plain.retentionPolicy == RetentionPolicy.RUNTIME
        !plain.inherited
        !plain.repeatableContainer.present
        plain.members*.name == ["value"]
        plain.members[0].defaultState == AnnotationTypeMember.DefaultState.ABSENT
    }

    def "describes a library annotation and a JDK annotation"() {
        given:
        def classLoader = buildClassLoader("ktdesc.Holder", source(""))

        when:
        AnnotationType named = describe(classLoader, Named.name)
        AnnotationType deprecated = describe(classLoader, Deprecated.name)

        then:
        named.type == Named
        named.retentionPolicy == RetentionPolicy.RUNTIME
        named.members*.name == ["value"]
        named.members[0].type == String
        named.annotationMetadata.hasDeclaredAnnotation(Qualifier)

        deprecated.type == Deprecated
        deprecated.retentionPolicy == RetentionPolicy.RUNTIME
        deprecated.targets.contains(ElementType.METHOD)
        deprecated.members*.name == ["since", "forRemoval"]
    }
}
