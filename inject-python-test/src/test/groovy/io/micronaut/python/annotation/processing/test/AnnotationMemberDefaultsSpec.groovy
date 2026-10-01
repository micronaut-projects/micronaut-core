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
package io.micronaut.python.annotation.processing.test

import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * Asserts that annotation member defaults of every kind declared on a Python decorator are reported identically to
 * the three JVM languages. The sibling copies of this spec live in the inject-java, inject-kotlin and inject-groovy
 * test suites and share the expectation literals below.
 */
class AnnotationMemberDefaultsSpec extends AbstractPythonTypeElementSpec {

    private void compileTestSource() {
        buildBeanDefinition('python', 'Test', '''
from enum import Enum
from jakarta.inject import Singleton

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

class Colour(Enum):
    RED = "RED"
    GREEN = "GREEN"

class Target:
    pass

@micronaut_annotation("defaults.Nested")
def nested_ann(name: str = "nested"):
    def decorator(target):
        return target
    return decorator

@micronaut_annotation("defaults.Defaults")
def defaults_ann(
    stringValue: str = "foo",
    emptyStringValue: str = "",
    intValue: int = 42,
    enumValue: Colour = Colour.GREEN,
    classValue: type = Target,
    stringArray: list[str] = ["a", "b"],
    emptyArray: list[str] = [],
):
    def decorator(target):
        return target
    return decorator

@Singleton
@defaults_ann()
class Test:
    pass
''')
    }

    /**
     * The defaults reported by {@link io.micronaut.inject.visitor.VisitorContext#getAnnotationDefaultValues(String)},
     * which asks for every declared default including empty ones.
     *
     * <p>This is the JVM literal of the sibling specs minus the {@code nested} member, which has no Python
     * equivalent: a Python decorator member cannot declare another decorator application as its default.</p>
     */
    static final String EXPECTED_FROM_CONTEXT =
            "[classValue:AnnotationClassValue(python.Target), " +
            "emptyArray:String[], " +
            "emptyStringValue:String(), " +
            "enumValue:String(GREEN), " +
            "intValue:Integer(42), " +
            "stringArray:String[String(a), String(b)], " +
            "stringValue:String(foo)]"

    /**
     * The defaults baked into the written annotation metadata and seen through
     * {@link io.micronaut.core.annotation.AnnotationValue#getDefaultValues()}. The empty string default is
     * omitted, which is the deliberate metadata size optimization documented on
     * {@code JavaAnnotationMetadataBuilder#isValidDefaultValue} and the point of the second read.
     *
     * <p>Two shapes differ from the JVM languages, both because the Python class is visited before its generated
     * Java annotation type exists, so the member types are not yet known: a class value is written as the class
     * name rather than an {@code AnnotationClassValue}, and a list value as a {@code List} rather than an array.
     * A member value given at a usage site is written the same way, so the defaults are not a special case.</p>
     */
    static final String EXPECTED_FROM_ANNOTATION =
            "[classValue:String(python.Target), " +
            "emptyArray:List[], " +
            "enumValue:String(GREEN), " +
            "intValue:Integer(42), " +
            "stringArray:List[String(a), String(b)], " +
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
                'classValue', 'stringArray', 'emptyArray'
        ] as Set

        and: 'constants, enum constants, class literals and arrays all resolve'
        defaults['stringValue'] == 'foo'
        defaults['emptyStringValue'] == ''
        defaults['intValue'] == 42
        defaults['enumValue'] == 'GREEN'
        defaults['classValue'] instanceof AnnotationClassValue
        defaults['classValue'].name == 'python.Target'
        defaults['stringArray'] as List == ['a', 'b']
        defaults['emptyArray'].length == 0

        and: 'the result is identical to the other languages'
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
                'classValue', 'stringArray', 'emptyArray'
        ] as Set

        and:
        defaults['enumValue'] == 'GREEN'
        defaults['classValue'] == 'python.Target'
        defaults['stringArray'] as List == ['a', 'b']
        defaults['emptyArray'].isEmpty()

        and: 'the result is identical to the other languages'
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
         * Renders the defaults in a language neutral form so that the language suites can compare against the
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
            if (v instanceof Collection) {
                return "List[" + v.collect { render(it) }.join(", ") + "]"
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
            if (element.simpleName != "Test") {
                return
            }
            // The class is visited twice: once as the Python class, and once as the Java stub generated for it.
            // Only the Python class carries the decorator, while the annotation type only exists as a Java class
            // element in the second round, which is where the visitor context can resolve its defaults.
            def contextDefaults = context.getAnnotationDefaultValues("defaults.Defaults")
            if (contextDefaults) {
                fromContext = contextDefaults
            }
            def annotation = element.getAnnotation("defaults.Defaults")
            if (annotation != null) {
                fromAnnotation = annotation.getDefaultValues()
            }
        }
    }
}
