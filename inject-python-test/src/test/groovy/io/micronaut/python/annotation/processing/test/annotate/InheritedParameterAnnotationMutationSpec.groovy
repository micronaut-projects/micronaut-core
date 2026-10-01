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
package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class InheritedParameterAnnotationMutationSpec extends AbstractPythonTypeElementSpec {

    void 'mutations of an inherited parameter are visible through the subclass'() {
        given:
        InheritedParameterMutationVisitor.reset()

        when:
        def definition = buildBeanDefinition('python', 'InheritedMutationSub', '''
from typing import Annotated
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Executable

class InheritedMutationBase:
    @Executable
    def inherited(self, value: Annotated[str, Named("marker")]) -> None:
        pass

@Singleton
class InheritedMutationSub(InheritedMutationBase):
    pass
''')

        then: 'the parameter read through the subclass retains the mutation made through the declaring class'
        InheritedParameterMutationVisitor.baseVisitedFirst
        InheritedParameterMutationVisitor.parameterOwner == 'python.InheritedMutationSub'
        InheritedParameterMutationVisitor.parameterAnnotations.contains('test.Added')
        !InheritedParameterMutationVisitor.parameterAnnotations.contains('jakarta.inject.Named')

        and: 'the compiled bean definition carries the inherited mutation'
        def argument = definition.findPossibleMethods('inherited').findAny().get().arguments[0]
        argument.annotationMetadata.hasAnnotation('test.Added')
        !argument.annotationMetadata.hasAnnotation('jakarta.inject.Named')
    }

    static class InheritedParameterMutationVisitor implements TypeElementVisitor<Object, Object> {

        static boolean baseVisited
        static boolean baseVisitedFirst
        static String parameterOwner
        static List<String> parameterAnnotations

        static void reset() {
            baseVisited = false
            baseVisitedFirst = false
            parameterOwner = null
            parameterAnnotations = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'python.InheritedMutationBase') {
                def parameter = element.getEnclosedElement(ElementQuery.ALL_METHODS.named('inherited')).get().parameters[0]
                parameter.removeAnnotation('jakarta.inject.Named')
                parameter.annotate('test.Added')
                baseVisited = true
            } else if (element.name == 'python.InheritedMutationSub') {
                baseVisitedFirst = baseVisited
                def method = element.getEnclosedElement(ElementQuery.ALL_METHODS.named('inherited')).get()
                parameterOwner = method.owningType.name
                parameterAnnotations = method.parameters[0].annotationMetadata.annotationNames.asList()
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
