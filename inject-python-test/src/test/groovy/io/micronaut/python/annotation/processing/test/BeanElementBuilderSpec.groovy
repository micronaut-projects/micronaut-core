/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.PendingFeature

class BeanElementBuilderSpec extends AbstractPythonTypeElementSpec {

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0029")
    void "test associated bean can be defined from type element visitor"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton

@Singleton
class BeanElementBuilderTrigger:
    pass
''')

        expect:
        context.containsBean(AssociatedBean)

        cleanup:
        context?.close()
    }

    static class AssociatedBean {
    }

    static class AssociatedBeanVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == "python.BeanElementBuilderTrigger") {
                context.getClassElement(AssociatedBean)
                    .ifPresent { associatedBean ->
                        element.addAssociatedBean(associatedBean)
                    }
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
