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

import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * An annotation mutation a visitor makes on an inherited attribute or on a parameter of an inherited method while
 * visiting one subclass belongs to that subclass: another subclass of the same base, and the base itself, keep
 * the source annotations.
 */
class SubclassMemberAnnotationMutationSpec extends AbstractPythonTypeElementSpec {

    void 'mutations of inherited members made through one subclass are not visible through another subclass'() {
        given:
        MarkingVisitor.reset()

        when:
        def context = buildContext('''
from typing import Annotated
from jakarta.inject import Inject, Singleton

@Singleton
class SubMutFoo:
    pass

@Singleton
class SubMutBar:
    pass

class SubMutBase:
    foo: Annotated[SubMutFoo, Inject] = None

    @Inject
    def set_bars(self, bars: list[SubMutBar]) -> None:
        self.bars = bars

@Singleton
class SubMutB(SubMutBase):
    pass

@Singleton
class SubMutC(SubMutBase):
    pass
''')
        BeanDefinition<?> b = getBeanDefinition(context, 'python.SubMutB')
        BeanDefinition<?> c = getBeanDefinition(context, 'python.SubMutC')

        then: 'the members were annotated through SubMutB'
        MarkingVisitor.fieldOwner == 'python.SubMutB'
        MarkingVisitor.parameterOwner == 'python.SubMutB'

        and: 'the definition of SubMutB carries the mutation'
        b.injectedMethods.find { it.methodName == 'setFoo' }.arguments[0].annotationMetadata.hasAnnotation('test.Marked')
        b.injectedMethods.find { it.methodName == 'set_bars' }.arguments[0].annotationMetadata.hasAnnotation('test.Marked')

        and: 'the definition of SubMutC keeps the source annotations'
        !c.injectedMethods.find { it.methodName == 'setFoo' }.arguments[0].annotationMetadata.hasAnnotation('test.Marked')
        !c.injectedMethods.find { it.methodName == 'set_bars' }.arguments[0].annotationMetadata.hasAnnotation('test.Marked')

        and: 'the members read through SubMutC once every type was visited keep the source annotations'
        MarkingVisitor.subFieldAnnotations != null
        !MarkingVisitor.subFieldAnnotations.contains('test.Marked')
        !MarkingVisitor.subParameterAnnotations.contains('test.Marked')

        and: 'the members read through SubMutBase keep the source annotations'
        MarkingVisitor.baseFieldAnnotations != null
        !MarkingVisitor.baseFieldAnnotations.contains('test.Marked')
        !MarkingVisitor.baseParameterAnnotations.contains('test.Marked')

        cleanup:
        context.close()
    }

    /**
     * Annotates the inherited injection points of SubMutB with test.Marked while visiting SubMutB, and records once
     * every type was visited what the members look like read through SubMutBase and through SubMutC.
     */
    static class MarkingVisitor implements TypeElementVisitor<Object, Object> {

        static String fieldOwner
        static String parameterOwner
        static List<String> baseFieldAnnotations
        static List<String> baseParameterAnnotations
        static List<String> subFieldAnnotations
        static List<String> subParameterAnnotations
        static ClassElement base
        static ClassElement sub

        static void reset() {
            fieldOwner = null
            parameterOwner = null
            baseFieldAnnotations = null
            baseParameterAnnotations = null
            subFieldAnnotations = null
            subParameterAnnotations = null
            base = null
            sub = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getClass().getName().containsIgnoreCase("java")) {
                // the generated Java classes are visited after the Python ones
                return
            }
            if (element.name == 'python.SubMutBase') {
                base = element
            }
            if (element.name == 'python.SubMutB') {
                def field = element.getEnclosedElement(ElementQuery.ALL_FIELDS.named('foo')).get()
                fieldOwner = field.owningType.name
                field.annotate('test.Marked')

                def method = element.getEnclosedElement(ElementQuery.ALL_METHODS.named('set_bars')).get()
                parameterOwner = method.owningType.name
                method.parameters[0].annotate('test.Marked')
            }
            if (element.name == 'python.SubMutC') {
                sub = element
            }
        }

        @Override
        void finish(VisitorContext visitorContext) {
            if (base == null || sub == null) {
                return
            }
            baseFieldAnnotations = annotationsOfField(base)
            baseParameterAnnotations = annotationsOfParameter(base)
            subFieldAnnotations = annotationsOfField(sub)
            subParameterAnnotations = annotationsOfParameter(sub)
            // The visitor is registered for every compilation of the module: the elements belong to this one
            base = null
            sub = null
        }

        private static List<String> annotationsOfField(ClassElement element) {
            return element.getEnclosedElement(ElementQuery.ALL_FIELDS.named('foo')).get().annotationMetadata.annotationNames.asList()
        }

        private static List<String> annotationsOfParameter(ClassElement element) {
            return element.getEnclosedElement(ElementQuery.ALL_METHODS.named('set_bars')).get().parameters[0].annotationMetadata.annotationNames.asList()
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
