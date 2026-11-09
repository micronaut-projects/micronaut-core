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

import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

/**
 * Asserts that annotation member defaults of every kind are reported for Kotlin, identically to the other two
 * supported languages. The sibling copies of this spec live in the inject-java, inject-kotlin and inject-groovy test
 * suites and share the expectation literals below.
 */
class AnnotationMemberDefaultsSpec extends AbstractKotlinCompilerSpec {

    private void compileTestSource() {
        buildBeanDefinition('defaults.Test', '''
package defaults

import io.micronaut.context.annotation.Bean
import kotlin.reflect.KClass

enum class Colour { RED, GREEN }

annotation class Nested(val name: String = "nested")

annotation class Defaults(
    val stringValue: String = "foo",
    val emptyStringValue: String = "",
    val intValue: Int = 42,
    val enumValue: Colour = Colour.GREEN,
    val classValue: KClass<*> = String::class,
    val stringArray: Array<String> = ["a", "b"],
    val emptyArray: Array<String> = [],
    val nested: Nested = Nested("n")
)

@Defaults
@Bean
class Test
''')
    }

    /**
     * The defaults reported by {@link io.micronaut.inject.visitor.VisitorContext#getAnnotationDefaultValues(String)},
     * which asks for every declared default including empty ones.
     *
     * <p>This literal is shared, verbatim, by the inject-java, inject-kotlin and inject-groovy copies of this spec.
     * All three languages must report exactly the same defaults for the equivalent annotation.</p>
     */
    static final String EXPECTED_FROM_CONTEXT =
            "[classValue:AnnotationClassValue(java.lang.String), " +
            "emptyArray:String[], " +
            "emptyStringValue:String(), " +
            "enumValue:String(GREEN), " +
            "intValue:Integer(42), " +
            "nested:AnnotationValue(defaults.Nested, [name:n]), " +
            "stringArray:String[String(a), String(b)], " +
            "stringValue:String(foo)]"

    /**
     * The defaults baked into the written annotation metadata and seen through
     * {@link io.micronaut.core.annotation.AnnotationValue#getDefaultValues()}. Identical to
     * {@link #EXPECTED_FROM_CONTEXT} except that the empty string default is omitted, which is a deliberate
     * metadata size optimization documented on {@code JavaAnnotationMetadataBuilder#isValidDefaultValue}.
     */
    static final String EXPECTED_FROM_ANNOTATION =
            "[classValue:AnnotationClassValue(java.lang.String), " +
            "emptyArray:String[], " +
            "enumValue:String(GREEN), " +
            "intValue:Integer(42), " +
            "nested:AnnotationValue(defaults.Nested, [name:n]), " +
            "stringArray:String[String(a), String(b)], " +
            "stringValue:String(foo)]"

    void 'test every annotation member default is reported through the visitor context'() {
        given:
        DefaultsRecordingVisitor.reset()

        when:
        compileTestSource()
        def defaults = DefaultsRecordingVisitor.fromContext

        then: 'every declared default is reported, including the empty string and the empty array'
        defaults != null
        defaults.keySet()*.toString() as Set == [
                'stringValue', 'emptyStringValue', 'intValue', 'enumValue',
                'classValue', 'stringArray', 'emptyArray', 'nested'
        ] as Set

        and: 'constants, enum constants, class literals, arrays and nested annotations all resolve'
        defaults['stringValue'] == 'foo'
        defaults['emptyStringValue'] == ''
        defaults['intValue'] == 42
        defaults['enumValue'] == 'GREEN'
        defaults['classValue'] instanceof AnnotationClassValue
        defaults['classValue'].name == 'java.lang.String'
        defaults['stringArray'] as List == ['a', 'b']
        defaults['emptyArray'].length == 0
        defaults['nested'] instanceof AnnotationValue
        defaults['nested'].annotationName == 'defaults.Nested'
        defaults['nested'].stringValue('name').get() == 'n'

        and: 'the result is identical to the other two languages'
        DefaultsRecordingVisitor.describe(defaults) == EXPECTED_FROM_CONTEXT
    }

    void 'test annotation member defaults are visible on the AnnotationValue read from the element API'() {
        given:
        DefaultsRecordingVisitor.reset()

        when:
        compileTestSource()
        def defaults = DefaultsRecordingVisitor.fromAnnotation

        then: 'the written metadata carries every default except the empty string'
        defaults != null
        defaults.keySet()*.toString() as Set == [
                'stringValue', 'intValue', 'enumValue',
                'classValue', 'stringArray', 'emptyArray', 'nested'
        ] as Set

        and:
        defaults['enumValue'] == 'GREEN'
        defaults['classValue'].name == 'java.lang.String'
        defaults['stringArray'] as List == ['a', 'b']
        defaults['emptyArray'].length == 0
        defaults['nested'].stringValue('name').get() == 'n'

        and: 'the result is identical to the other two languages'
        DefaultsRecordingVisitor.describe(defaults) == EXPECTED_FROM_ANNOTATION
    }

    private static final String ADDED_DEFAULTS_SOURCE = '''
package addeddefaults

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

@Singleton
class AddedDefaultsBean {

    @Executable
    fun method() {
    }
}
'''

    /**
     * KSP resolves the defaults of an annotation type from a usage of it, and finds usages in the files of the round
     * only, which in an incremental run are the changed ones: the defaults of an annotation a visitor adds by its type
     * must not depend on whether one of those uses it.
     */
    void 'test the defaults of an annotation a visitor adds by type do not depend on a usage in the compilation'() {
        given:
        AddedDefaultsVisitor.reset()

        when: 'no source uses the annotation'
        def definition = buildBeanDefinition('addeddefaults.AddedDefaultsBean', ADDED_DEFAULTS_SOURCE)
        def withoutUsage = AddedDefaultsVisitor.added
        def withoutUsageFromContext = AddedDefaultsVisitor.fromContext

        then: 'the defaults are read off the compiled annotation, empty strings left out of the metadata, classes named as a usage names them'
        DefaultsRecordingVisitor.describe(withoutUsage) ==
                "[arrayClass:AnnotationClassValue(java.lang.Object[]), " +
                "booleanValue:Boolean(true), " +
                "classArray:AnnotationClassValue[AnnotationClassValue(java.lang.String), AnnotationClassValue(java.lang.Integer)], " +
                "classValue:AnnotationClassValue(java.lang.String), " +
                "emptyArray:String[], " +
                "enumArray:String[String(FIELD), String(TYPE)], " +
                "enumValue:String(METHOD), " +
                "intArray:Integer[Integer(1), Integer(2)], " +
                "intValue:Integer(42), " +
                "primitiveArrayClass:AnnotationClassValue(int[]), " +
                "primitiveClass:AnnotationClassValue(java.lang.Integer), " +
                "stringArray:String[String(a), String(b)], " +
                "stringValue:String(foo), " +
                "voidClass:AnnotationClassValue(kotlin.Unit), " +
                "wrapperClass:AnnotationClassValue(java.lang.Integer)]"
        withoutUsageFromContext['emptyStringValue'] == ''

        and: 'the generated definition registers them'
        definition.getRequiredMethod('method').getAnnotationMetadata()
                .getDefaultValues(AddedDefaults.name)['intValue'] == 42

        when: 'a source uses the annotation, so that KSP resolves its defaults from the usage'
        AddedDefaultsVisitor.reset()
        buildBeanDefinition('addeddefaults.AddedDefaultsBean', ADDED_DEFAULTS_SOURCE + '''
@io.micronaut.kotlin.processing.annotations.AddedDefaults
class User
''')

        then: 'the defaults are the same, in the same shapes and the same order'
        DefaultsRecordingVisitor.describe(AddedDefaultsVisitor.added) == DefaultsRecordingVisitor.describe(withoutUsage)
        AddedDefaultsVisitor.added.keySet()*.toString() == withoutUsage.keySet()*.toString()
        DefaultsRecordingVisitor.describe(AddedDefaultsVisitor.fromContext) == DefaultsRecordingVisitor.describe(withoutUsageFromContext)
        AddedDefaultsVisitor.fromContext.keySet()*.toString() == withoutUsageFromContext.keySet()*.toString()
    }

    /**
     * When the compile classpath holds another version of an annotation than the processor path, a member only one of
     * them has must not leave the others' defaults standing for all of them: those are cached for the whole JVM.
     */
    void 'test an annotation whose compiled class lacks a member its declaration has gets no defaults off the class'() {
        given:
        VersionedDefaultsVisitor.reset()

        when: 'the sources declare the annotation again with a member the class on the processor path lacks'
        buildBeanDefinition('io.micronaut.kotlin.processing.annotations.VersionedDefaultsBean', '''
package io.micronaut.kotlin.processing.annotations

import jakarta.inject.Singleton

annotation class VersionedDefaults(val value: String = "a", val added: String = "b")

@Singleton
class VersionedDefaultsBean
''')

        then:
        VersionedDefaultsVisitor.visited
        !VersionedDefaultsVisitor.fromContext
    }

    static class VersionedDefaultsVisitor implements TypeElementVisitor<Object, Object> {

        static boolean visited
        static Map<CharSequence, Object> fromContext = null

        static void reset() {
            visited = false
            fromContext = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.simpleName == "VersionedDefaultsBean") {
                visited = true
                fromContext = context.getAnnotationDefaultValues(VersionedDefaults.name)
            }
        }
    }

    static class AddedDefaultsVisitor implements TypeElementVisitor<Object, Object> {

        static Map<CharSequence, Object> added = null
        static Map<CharSequence, Object> fromContext = null

        static void reset() {
            added = null
            fromContext = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.simpleName == "AddedDefaultsBean") {
                def method = element.findMethod("method").get()
                method.annotate(AddedDefaults)
                added = method.getAnnotation(AddedDefaults)?.getDefaultValues()
                fromContext = context.getAnnotationDefaultValues(AddedDefaults.name)
            }
        }
    }

    static class DefaultsRecordingVisitor implements TypeElementVisitor<Object, Object> {

        static Map<CharSequence, Object> fromContext = null
        static Map<CharSequence, Object> fromAnnotation = null

        static void reset() {
            fromContext = null
            fromAnnotation = null
        }

        /**
         * Renders the defaults in a language neutral form so that the three language suites can compare against the
         * same literal.
         */
        static String describe(Map<CharSequence, Object> values) {
            if (values == null) {
                return "<not recorded>"
            }
            return values.collectEntries { k, v -> [(k.toString()): render(v)] }
                    .sort { it.key }
                    .toString()
        }

        private static String render(Object v) {
            if (v == null) {
                return "null"
            }
            if (v.getClass().isArray()) {
                return "${v.getClass().componentType.simpleName}[" + (v as Object[]).collect { render(it) }.join(", ") + "]"
            }
            if (v instanceof AnnotationClassValue) {
                return "AnnotationClassValue(${v.name})"
            }
            if (v instanceof AnnotationValue) {
                return "AnnotationValue(${v.annotationName}, ${v.values})"
            }
            return "${v.getClass().simpleName}(${v})"
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.simpleName == "Test") {
                fromContext = context.getAnnotationDefaultValues("defaults.Defaults")
                fromAnnotation = element.getAnnotation("defaults.Defaults")?.getDefaultValues()
            }
        }
    }
}
