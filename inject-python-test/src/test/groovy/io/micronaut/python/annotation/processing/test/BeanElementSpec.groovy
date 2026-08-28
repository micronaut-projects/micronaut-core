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

import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.ast.beans.BeanElement
import io.micronaut.inject.visitor.BeanElementVisitor
import io.micronaut.python.annotation.processing.test.beanelement.PythonTestBeanElementVisitor

class BeanElementSpec extends AbstractPythonTypeElementSpec {

    void setup() {
        testVisitor().reset()
        PythonTestBeanElementVisitor.enabled = true
    }

    void cleanup() {
        PythonTestBeanElementVisitor.enabled = false
        testVisitor().reset()
    }

    void "test produce another bean from a bean element visitor"() {
        given:
        PythonTestBeanElementVisitor.produceAssociatedBean = true
        def context = buildContext('''
from typing import Annotated
from jakarta.inject import Inject, Named
from micronaut.context.annotation import Prototype
import java

ConversionService = java.type("io.micronaut.core.convert.ConversionService")
Environment = java.type("io.micronaut.context.env.Environment")
Runnable = java.type("java.lang.Runnable")

@Prototype
@Named("blah")
class Test(Runnable):
    conversion_service: Annotated[ConversionService, Inject] = None

    @Inject
    def set_environment(self, environment: Environment):
        pass

    def run(self):
        pass

@Prototype
class Excluded:
    pass
''')

        expect:
        getBean(context, "python.Test")
        context.getBean(String) == "test"
        !context.containsBean(context.classLoader.loadClass("python.Excluded"))

        cleanup:
        context?.close()
    }

    void "test visit bean element for simple bean"() {
        given:
        buildBeanDefinition("python", "Test", '''
from typing import Annotated
from jakarta.inject import Inject, Named
from micronaut.context.annotation import Prototype
import java

ConversionService = java.type("io.micronaut.core.convert.ConversionService")
Environment = java.type("io.micronaut.context.env.Environment")
Runnable = java.type("java.lang.Runnable")

@Prototype
@Named("blah")
class Test(Runnable):
    conversion_service: Annotated[ConversionService, Inject] = None

    @Inject
    def set_environment(self, environment: Environment):
        pass

    def run(self):
        pass
''')

        expect:
        def visitor = testVisitor()
        visitor.initialized
        visitor.terminated

        and:
        BeanElement beanElement = visitor.theBeanElement
        beanElement != null
        beanElement.scope.get() == Prototype.name
        beanElement.qualifiers.size() == 1
        visitor.injectionPointNames == ["setConversion_service", "set_environment"] as Set
        beanElement.declaringClass.name == "python.Test"
        beanElement.producingElement.name == "python.Test"
        visitor.beanTypeNames == ["python.Test", "java.lang.Runnable"] as Set
    }

    void "test visit bean element for factory bean"() {
        given:
        buildBeanDefinition("python", "Test", '''
from typing import Annotated
from jakarta.inject import Inject
from micronaut.context.annotation import Bean, Factory, Prototype
import java

ConversionService = java.type("io.micronaut.core.convert.ConversionService")
Environment = java.type("io.micronaut.context.env.Environment")
Runnable = java.type("java.lang.Runnable")

class Test:
    pass

@Prototype
@Factory
class TestFactory(Runnable):
    conversion_service: Annotated[ConversionService, Inject] = None

    @Inject
    def set_environment(self, environment: Environment):
        pass

    @Bean
    def test(self) -> Test:
        return Test()

    def run(self):
        pass
''')

        expect:
        BeanElement beanElement = testVisitor().theBeanElement
        beanElement != null
        beanElement.scope.get() == Prototype.name
        beanElement.qualifiers.size() == 0
        beanElement.injectionPoints.size() == 0
        beanElement.declaringClass.name == "python.TestFactory"
        beanElement.producingElement.name == "test"
        testVisitor().beanTypeNames == ["python.Test"] as Set
    }

    private static PythonTestBeanElementVisitor testVisitor() {
        def visitor = BeanElementVisitor.VISITORS.find {
            it instanceof PythonTestBeanElementVisitor
        }
        assert visitor instanceof PythonTestBeanElementVisitor
        return (PythonTestBeanElementVisitor) visitor
    }
}
