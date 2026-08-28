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

import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class AnnotationMetadataSpec extends AbstractPythonTypeElementSpec {

    void "test mutated metadata from a visitor is available on beans"() {
        when:
        def definition = buildBeanDefinition("python", "TestListener", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.bind.annotation import Bindable

@Bindable
def SomeAnn(someValue: str = "OK"):
    def decorator(func):
        return func
    return decorator

@Singleton
class TestListener:

    @SomeAnn()
    @Executable
    def receive(self, v: str) -> None:
        pass
''')

        then:
        noExceptionThrown()
        def method = definition.findMethod("receive", String).get()
        method.hasAnnotation("my.custom.Annotation")
    }

    static class MutatingVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (element.hasAnnotation("python.SomeAnn")) {
                element.annotate("my.custom.Annotation")
                element.annotationMetadata
                    .findAnnotation("python.SomeAnn")
                    .get()
                    .getRequiredValue("someValue", String)
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
