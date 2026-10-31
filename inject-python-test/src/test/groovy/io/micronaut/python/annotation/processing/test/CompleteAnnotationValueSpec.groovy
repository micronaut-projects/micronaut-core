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

/**
 * {@link VisitorContext#completeAnnotationValue(AnnotationValue)} completes a value a visitor builds itself with the
 * defaults and non-binding members of a decorator declared as an annotation, as the three JVM languages do. The
 * sibling copies of this spec live in the inject-java, inject-kotlin and inject-groovy test suites; they also cover
 * values nested in members, which a Python decorator member cannot hold.
 */
class CompleteAnnotationValueSpec extends AbstractPythonTypeElementSpec {

    void 'test a value a visitor builds is completed with the defaults and non-binding members of its type'() {
        given:
        CompletingVisitor.reset()

        when:
        buildBeanDefinition('python', 'CompleteTest', '''
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

@cylinders(value=4)
class Used:
    pass

@Singleton
class CompleteTest:
    pass
''')

        then: 'the value as built carries neither'
        def built = CompletingVisitor.built
        built != null
        !built.contains(AnnotationUtil.NON_BINDING_ATTRIBUTE)
        !built.getDefaultValues()

        and: 'the completed value carries the defaults, records the non-binding members and keeps its explicit values'
        def completed = CompletingVisitor.completed
        completed.intValue().asInt == 8
        completed.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        completed.getDefaultValues() == [unit: 'count']

        and: 'a value whose type cannot be resolved is returned as it is'
        CompletingVisitor.unresolvedCompleted.is(CompletingVisitor.unresolved)

        and: 'a complete value is returned as it is'
        CompletingVisitor.completedAgain.is(CompletingVisitor.completed)
    }

    /**
     * Builds a {@code complete.Cylinders} value while {@code python.CompleteTest} is visited and completes it.
     */
    static class CompletingVisitor implements TypeElementVisitor<Object, Object> {

        static AnnotationValue<?> built
        static AnnotationValue<?> completed
        static AnnotationValue<?> completedAgain
        static AnnotationValue<?> unresolved
        static AnnotationValue<?> unresolvedCompleted

        static void reset() {
            built = null
            completed = null
            completedAgain = null
            unresolved = null
            unresolvedCompleted = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name != 'python.CompleteTest') {
                return
            }
            built = AnnotationValue.builder('complete.Cylinders').member('value', 8).build()
            completed = context.completeAnnotationValue(built)
            completedAgain = context.completeAnnotationValue(completed)
            unresolved = AnnotationValue.builder('complete.Missing').member('value', 1).build()
            unresolvedCompleted = context.completeAnnotationValue(unresolved)
        }
    }
}
