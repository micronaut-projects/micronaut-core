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

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.processing.element.AbstractPythonClassElement

/**
 * An annotation a visitor builds with {@link AnnotationValue#builder(String)} and nests in another annotation it adds
 * to a Python class is completed as the outer annotation is: with the defaults of its type and its non-binding
 * members. A Python decorator member cannot hold another decorator application in the sources, but a visitor can
 * nest one. The sibling copies of this spec live in the inject-java, inject-kotlin and inject-groovy test suites.
 */
class NestedAnnotationValueDefaultsSpec extends AbstractPythonTypeElementSpec {

    void 'test the annotations a visitor nests in an annotation it adds are completed'() {
        given:
        CompletingVisitor.reset()
        def definition = buildBeanDefinition('python', 'NestedDefaultsTest', '''
from jakarta.inject import Singleton
from micronaut.context.annotation import NonBinding
from typing import Annotated

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("complete.Cylinders")
def cylinders(value: int, description: Annotated[str, NonBinding] = "", unit: str = "count"):
    def decorator(target):
        return target
    return decorator

@micronaut_annotation("complete.Engine")
def engine(name: str = "v8"):
    def decorator(target):
        return target
    return decorator

@cylinders(value=4)
@engine()
class Used:
    pass

@Singleton
class NestedDefaultsTest:
    pass
''')

        expect: 'the nested annotation carries the defaults and non-binding members of its type, and its own values'
        def added = CompletingVisitor.added
        added.getDefaultValues().name == 'v8'
        def nested = added.getAnnotation('cylinders').get()
        nested.intValue().asInt == 8
        nested.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nested.getDefaultValues() == [unit: 'count']

        and: 'so do the annotations nested in an array'
        def spares = added.getAnnotations('spares')
        spares*.intValue()*.asInt == [8, 2]
        spares*.stringValue('unit')*.orElse(null) == [null, 'pairs']
        spares.every { it.getDefaultValues() == [unit: 'count'] }
        spares.every { it.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE).contains('description') }

        and: 'the written metadata records the non-binding members'
        def written = definition.getAnnotationMetadata().getAnnotation('complete.Engine')
        written.getAnnotation('cylinders').get().stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        written.getAnnotations('spares').every { it.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE).contains('description') }
    }

    /**
     * Adds to {@code python.NestedDefaultsTest} a {@code complete.Engine} built with
     * {@link AnnotationValue#builder(String)} that holds {@code complete.Cylinders} values built the same way, and
     * keeps the occurrence the Python element then holds.
     */
    static class CompletingVisitor implements TypeElementVisitor<Object, Object> {

        static AnnotationValue<?> added

        static void reset() {
            added = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            // the generated Java stub of the class is visited too, by the Java compilation
            if (element.name != 'python.NestedDefaultsTest' || !(element instanceof AbstractPythonClassElement)) {
                return
            }
            def eight = AnnotationValue.builder('complete.Cylinders').member('value', 8).build()
            def two = AnnotationValue.builder('complete.Cylinders').member('value', 2).member('unit', 'pairs').build()
            def engine = AnnotationValue.builder('complete.Engine')
                .member('cylinders', eight)
                .member('spares', eight, two)
                .build()
            element.annotate(engine)
            added = element.getAnnotation('complete.Engine')
        }
    }
}
