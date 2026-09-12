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
 * Asserts that annotation member defaults of every kind are reported for Python. In Python an annotation type is a
 * decorator factory function and its member defaults are that function's keyword argument defaults.
 *
 * <p>The sibling specs of the same name in the inject-java, inject-kotlin and inject-groovy suites assert the same
 * thing for those languages; the expectation literals here are kept in step with theirs, allowing for the one
 * difference Python's model forces (a Python class reference is its own type, so the class literal default names the
 * Python type rather than a Java one).</p>
 */
class AnnotationMemberDefaultsSpec extends AbstractPythonTypeElementSpec {

    private static final String PYTHON_SOURCE = '''
from enum import Enum
from jakarta.inject import Singleton

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

class Colour(Enum):
    RED = "RED"
    GREEN = "GREEN"

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
        classValue: type = Colour,
        stringArray: list[str] = ["a", "b"],
        emptyArray: list[str] = []):
    def decorator(target):
        return target
    return decorator

@Singleton
@defaults_ann()
class Test:
    pass
'''

    void 'test every annotation member default is reported through the visitor context'() {
        given:
        DefaultsRecordingVisitor.reset()

        when:
        buildClassElement(PYTHON_SOURCE) { ClassElement ce -> ce }
        def defaults = DefaultsRecordingVisitor.fromContext

        then: 'every declared default is reported, including the empty string and the empty array'
        defaults != null
        defaults.keySet()*.toString() as Set == [
                'stringValue', 'emptyStringValue', 'intValue', 'enumValue',
                'classValue', 'stringArray', 'emptyArray'
        ] as Set

        and: 'constants and enum constants resolve as they do in the other languages'
        defaults['stringValue'] == 'foo'
        defaults['emptyStringValue'] == ''
        defaults['intValue'] == 42
        defaults['enumValue'] == 'GREEN'

        and: '''a class reference and a list are reported as the Python processor read them. Converting them to an
                AnnotationClassValue and to an array needs the member's declared type, and resolving that inside the
                defaults reader re-enters Python class element construction, which reads the defaults again. See
                PythonAnnotationMetadataBuilder#resolvePythonAnnotationMember.'''
        defaults['classValue'] == 'python.Colour'
        defaults['stringArray'] as List == ['a', 'b']
        (defaults['emptyArray'] as List).isEmpty()
    }

    void 'test annotation member defaults are visible on the AnnotationValue read from the element API'() {
        given:
        DefaultsRecordingVisitor.reset()

        when:
        buildClassElement(PYTHON_SOURCE) { ClassElement ce -> ce }
        def defaults = DefaultsRecordingVisitor.fromAnnotation

        then: 'the written metadata carries every default except the empty string'
        defaults != null
        defaults.keySet()*.toString() as Set == [
                'stringValue', 'intValue', 'enumValue',
                'classValue', 'stringArray', 'emptyArray'
        ] as Set

        and:
        defaults['stringValue'] == 'foo'
        defaults['intValue'] == 42
        defaults['enumValue'] == 'GREEN'
        defaults['classValue'] == 'python.Colour'
        defaults['stringArray'] as List == ['a', 'b']
        (defaults['emptyArray'] as List).isEmpty()
    }

    static class DefaultsRecordingVisitor implements TypeElementVisitor<Object, Object> {

        static Map<CharSequence, Object> fromContext = null
        static Map<CharSequence, Object> fromAnnotation = null

        static void reset() {
            fromContext = null
            fromAnnotation = null
        }

        /**
         * Renders the defaults in a language neutral form, matching the renderer the sibling specs use.
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
            if (element.simpleName == "Test" && element.hasAnnotation("defaults.Defaults")) {
                fromContext = context.getAnnotationDefaultValues("defaults.Defaults")
                fromAnnotation = element.getAnnotation("defaults.Defaults")?.getDefaultValues()
                println "PY FROM CONTEXT: " + describe(fromContext)
                println "PY FROM ANNOTATION: " + describe(fromAnnotation)
            }
        }
    }
}
