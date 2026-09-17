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

import io.micronaut.context.annotation.Primary
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.beans.BeanElementBuilder
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.beanbuilder.ProducesChild
import io.micronaut.python.processing.element.PythonClassElement

/**
 * A visitor that imports a Python "module" class the way the Guice module import visitor does: the module
 * is registered as an associated bean created with its primary constructor, and its producer methods
 * become child beans with {@link BeanElementBuilder#produceBeans}.
 */
class ProduceBeansSpec extends AbstractPythonTypeElementSpec {

    void "test producer methods of a Python module without __init__ yield child beans"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.beanbuilder import ProducesChild

class CheckoutProcessor:

    def describe(self) -> str:
        return "checkout"

class ProducerModule:

    @ProducesChild
    def provide_processor(self) -> CheckoutProcessor:
        return CheckoutProcessor()

    def _helper(self) -> str:
        return "not a producer"

@Singleton
class ProducerApplication:
    pass
''')

        expect:
        ProducerModuleVisitor.IMPORTED == ["python.ProducerModule"]
        context.getBeanDefinitions(context.classLoader.loadClass("python.ProducerModule")).size() == 1
        context.getBeanDefinitions(context.classLoader.loadClass("python.CheckoutProcessor")).size() == 1

        when:
        def processor = getBean(context, "python.CheckoutProcessor")

        then:
        processor.asPolyglotValue().invokeMember("describe").asString() == "checkout"

        cleanup:
        ProducerModuleVisitor.IMPORTED.clear()
        context?.close()
    }

    void "test producer methods of a Python module with __init__ yield child beans"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.beanbuilder import ProducesChild

@Singleton
class Prefix:

    def value(self) -> str:
        return "prefixed"

class CheckoutProcessor:

    def __init__(self, name: str):
        self.name = name

    def describe(self) -> str:
        return self.name

@Singleton
class Suffix:

    def value(self) -> str:
        return "checkout"

class ProducerModule:

    def __init__(self, prefix: Prefix):
        self.prefix = prefix

    @ProducesChild
    def provide_processor(self, suffix: Suffix) -> CheckoutProcessor:
        return CheckoutProcessor(self.prefix.value() + "-" + suffix.value())

@Singleton
class ProducerApplication:
    pass
''')

        expect:
        ProducerModuleVisitor.IMPORTED == ["python.ProducerModule"]
        context.getBeanDefinitions(context.classLoader.loadClass("python.CheckoutProcessor")).size() == 1

        when:
        def processor = getBean(context, "python.CheckoutProcessor")

        then:
        processor.asPolyglotValue().invokeMember("describe").asString() == "prefixed-checkout"

        cleanup:
        ProducerModuleVisitor.IMPORTED.clear()
        context?.close()
    }

    void "test producer methods of a module imported when the visitor finishes yield child beans"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.beanbuilder import ProducesChild

class CheckoutProcessor:

    def describe(self) -> str:
        return "checkout"

class LateProduct:

    def describe(self) -> str:
        return "late"

class ProducerModule:

    @ProducesChild
    def provide_processor(self) -> CheckoutProcessor:
        return CheckoutProcessor()

class LateProducerModule:

    @ProducesChild
    def provide_product(self) -> LateProduct:
        return LateProduct()

@Singleton
class ProducerApplication:
    pass
''')

        expect:
        ProducerModuleVisitor.IMPORTED == ["python.ProducerModule", "python.LateProducerModule"]
        context.getBeanDefinitions(context.classLoader.loadClass("python.LateProducerModule")).size() == 1
        context.getBeanDefinitions(context.classLoader.loadClass("python.LateProduct")).size() == 1

        when:
        def product = getBean(context, "python.LateProduct")

        then:
        product.asPolyglotValue().invokeMember("describe").asString() == "late"

        cleanup:
        ProducerModuleVisitor.IMPORTED.clear()
        context?.close()
    }

    static class ProducerModuleVisitor implements TypeElementVisitor<Object, Object> {
        static final List<String> IMPORTED = []

        private ClassElement application

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (!(element instanceof PythonClassElement) || element.name != "python.ProducerApplication") {
                return
            }
            ClassElement module = context.getClassElement("python.ProducerModule").orElse(null)
            if (module == null) {
                throw new ProcessingException(element, "Module [python.ProducerModule] not found")
            }
            application = element
            importModule(element, module)
        }

        @Override
        void finish(VisitorContext context) {
            // a module imported when the visitor finishes, after every class was visited
            if (application != null) {
                context.getClassElement("python.LateProducerModule").ifPresent { importModule(application, it) }
                application = null
            }
        }

        private static void importModule(ClassElement element, ClassElement module) {
            MethodElement primaryConstructor = module.getPrimaryConstructor().orElse(null)
            if (primaryConstructor == null) {
                throw new ProcessingException(element, "Cannot import module [" + module.name + "], since it has no accessible constructor")
            }
            IMPORTED.add(module.name)
            BeanElementBuilder builder = element.addAssociatedBean(module)
            builder.createWith(primaryConstructor)
            ElementQuery<MethodElement> producerMethods = ElementQuery.ALL_METHODS
                .annotated { it.hasAnnotation(ProducesChild) }
                .onlyDeclared()
                .onlyConcrete()
            for (MethodElement producerMethod : module.getEnclosedElements(producerMethods)) {
                if (!producerMethod.isPublic()) {
                    throw new ProcessingException(producerMethod, "Producer methods must be public")
                }
            }
            builder.produceBeans(producerMethods) { BeanElementBuilder childBuilder ->
                MethodElement producerMethod = (MethodElement) childBuilder.producingElement
                childBuilder.typed(producerMethod.genericReturnType)
                childBuilder.annotate(Primary)
            }
            builder.typed(module)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
