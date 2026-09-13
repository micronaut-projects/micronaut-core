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

import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

/**
 * Asserts that annotation member defaults of every kind are reported for Groovy, identically to the other two
 * supported languages. The sibling copies of this spec live in the inject-java, inject-kotlin and inject-groovy test
 * suites and share the expectation literals below.
 */
class AnnotationMemberDefaultsSpec extends AbstractBeanDefinitionSpec {

    private void compileTestSource() {
        buildBeanDefinition('defaults.Test', '''
package defaults

import io.micronaut.context.annotation.Bean
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

enum Colour { RED, GREEN }

@Retention(RetentionPolicy.RUNTIME)
@interface Nested {
    String name() default "nested"
}

@Retention(RetentionPolicy.RUNTIME)
@interface Defaults {
    String stringValue() default "foo"
    String emptyStringValue() default ""
    int intValue() default 42
    Colour enumValue() default Colour.GREEN
    Class<?> classValue() default String.class
    String[] stringArray() default ["a", "b"]
    String[] emptyArray() default []
    Nested nested() default @Nested(name = "n")
}

@Defaults
@Bean
class Test {
}
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
