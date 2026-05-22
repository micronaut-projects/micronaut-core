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

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.beanbuilder.ApplyAopToMe
import io.micronaut.python.annotation.processing.test.beanbuilder.Mutating
import io.micronaut.python.processing.visitor.PythonClassElement

class BeanElementBuilderSpec extends AbstractPythonTypeElementSpec {

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
        context.getBeanDefinitions(AssociatedBean).size() == 1

        cleanup:
        context?.close()
    }

    void "test AOP applied to method on type registered via builder"() {
        given:
        ApplyAopToMethodVisitor.ENABLED = true
        def context = buildContext('''
from micronaut.aop import InterceptorBean, MethodInvocationContext
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
ApplyAopToMe = java.type("io.micronaut.python.annotation.processing.test.beanbuilder.ApplyAopToMe")
Mutating = java.type("io.micronaut.python.annotation.processing.test.beanbuilder.Mutating")

@InterceptorBean(Mutating)
@Singleton
class MutatingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        member = context.stringValue("io.micronaut.python.annotation.processing.test.beanbuilder.Mutating").orElse(None)
        argument = context.getParameters().get(member)
        if argument is not None:
            argument.setValue("changed")
        return context.proceed()

@Singleton
class Test:
    def __init__(self, apply_aop_to_me: ApplyAopToMe):
        self.apply_aop_to_me = apply_aop_to_me

    def hello(self, name: str) -> str:
        return self.apply_aop_to_me.hello(name)

    def plain(self, name: str) -> str:
        return self.apply_aop_to_me.plain(name)
''')
        def test = getBean(context, "python.Test").asPolyglotValue()

        expect:
        test.invokeMember("hello", "john").asString() == "Hello changed"
        test.invokeMember("plain", "john").asString() == "Hello john"

        cleanup:
        ApplyAopToMethodVisitor.ENABLED = false
        context?.close()
    }

    void "test AOP applied to type registered via builder"() {
        given:
        ApplyAopToTypeVisitor.ENABLED = true
        def context = buildContext('''
from micronaut.aop import InterceptorBean, MethodInvocationContext
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
ApplyAopToMe = java.type("io.micronaut.python.annotation.processing.test.beanbuilder.ApplyAopToMe")
Mutating = java.type("io.micronaut.python.annotation.processing.test.beanbuilder.Mutating")

@InterceptorBean(Mutating)
@Singleton
class MutatingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        member = context.stringValue("io.micronaut.python.annotation.processing.test.beanbuilder.Mutating").orElse(None)
        argument = context.getParameters().get(member)
        if argument is not None:
            argument.setValue("changed")
        return context.proceed()

@Singleton
class Test:
    def __init__(self, apply_aop_to_me: ApplyAopToMe):
        self.apply_aop_to_me = apply_aop_to_me

    def hello(self, name: str) -> str:
        return self.apply_aop_to_me.hello(name)

    def plain(self, name: str) -> str:
        return self.apply_aop_to_me.plain(name)
''')
        def test = getBean(context, "python.Test").asPolyglotValue()

        expect:
        test.invokeMember("hello", "john").asString() == "Hello changed"
        test.invokeMember("plain", "john").asString() == "Hello changed"

        cleanup:
        ApplyAopToTypeVisitor.ENABLED = false
        context?.close()
    }

    static class AssociatedBean {
    }

    static class AssociatedBeanVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (isPythonSourceElement(element) && element.name == "python.BeanElementBuilderTrigger") {
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

    static class ApplyAopToMethodVisitor implements TypeElementVisitor<Object, Object> {
        static boolean ENABLED = false

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (ENABLED && isPythonSourceElement(element) && element.name == "python.Test") {
                def annotationValue = AnnotationValue.builder(Mutating.name)
                    .value("name")
                    .build()
                context.getClassElement(ApplyAopToMe)
                    .ifPresent { applyAopToMe ->
                        element.addAssociatedBean(applyAopToMe)
                            .inject()
                            .withMethods(ElementQuery.ALL_METHODS.named { it == "hello" }) { method ->
                                method.intercept(annotationValue)
                            }
                    }
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }

    static class ApplyAopToTypeVisitor implements TypeElementVisitor<Object, Object> {
        static boolean ENABLED = false

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (ENABLED && isPythonSourceElement(element) && element.name == "python.Test") {
                def annotationValue = AnnotationValue.builder(Mutating.name)
                    .value("name")
                    .build()
                context.getClassElement(ApplyAopToMe)
                    .ifPresent { applyAopToMe ->
                        element.addAssociatedBean(applyAopToMe)
                            .intercept(annotationValue)
                            .inject()
                    }
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }

    private static boolean isPythonSourceElement(ClassElement element) {
        element instanceof PythonClassElement
    }
}
