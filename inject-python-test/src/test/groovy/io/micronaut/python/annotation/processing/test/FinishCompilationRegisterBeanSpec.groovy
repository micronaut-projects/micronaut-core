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

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.visitor.rounds.Recorded
import io.micronaut.inject.visitor.rounds.RegisteredRecord
import io.micronaut.python.processing.element.PythonClassElement

/**
 * A bean registered from {@link TypeElementVisitor#finishCompilation(VisitorContext)} is written for a Python
 * compilation, with what every class of the compilation contained.
 */
class FinishCompilationRegisterBeanSpec extends AbstractPythonTypeElementSpec {

    void "test a bean registered once the compilation is finished is written"() {
        given:
        def context = buildContext('''
class CompiledFirst:
    pass

class CompiledSecond:
    pass
''')

        expect:
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('compilation'))
                .stringValues(Recorded, 'classes') as List == ['python.CompiledFirst', 'python.CompiledSecond']
        context.getBean(RegisteredRecord, Qualifiers.byName('compilation')) instanceof RegisteredRecord

        cleanup:
        context?.close()
    }

    /**
     * Registers a bean of {@link RegisteredRecord} once the compilation is finished, for the Python classes whose
     * name starts with {@code Compiled}.
     */
    static class CompilationRecordingVisitor implements TypeElementVisitor<Object, Object> {

        private final List<String> compiled = []

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element instanceof PythonClassElement && element.simpleName.startsWith('Compiled')) {
                compiled.add(element.name)
            }
        }

        @Override
        void finishCompilation(VisitorContext context) {
            if (compiled.isEmpty()) {
                return
            }
            List<String> classes = compiled.toSorted()
            ClassElement[] originatingElements = classes.collect { context.getClassElement(it).orElseThrow() } as ClassElement[]
            ClassElement beanType = context.getRequiredClassElement(RegisteredRecord.name, context.elementAnnotationMetadataFactory)
            context.registerBean(beanType, originatingElements)
                    .qualifier('compilation')
                    .annotate(AnnotationValue.builder(Recorded)
                            .member('classes', classes as String[])
                            .member('phase', 'compilation')
                            .build())
            compiled.clear()
        }
    }
}
