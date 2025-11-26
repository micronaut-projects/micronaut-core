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
package io.micronaut.python.annotation.processing.test.inject.factory.beanmethod

import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.type.TypeInformation
import io.micronaut.inject.BeanDefinition
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import jakarta.inject.Singleton
import spock.lang.PendingFeature

class FactoryBeanMethodSpec extends AbstractPythonTypeElementSpec {
    void "test a factory bean with attribute"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype
from typing import Annotated

class Bar1:
    pass

@Factory
class TestFactory:
    bar : Annotated[Bar1, Bean, Prototype] = Bar1()


''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('python.Bar1'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        def bar1 = getBean(context, 'python.Bar1')

        then:
        bar1BeanDefinition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED) == '@i.m.c.a.Prototype p.Bar1 p.TestFactory.bar()'
        bar1 != null
        bar1BeanDefinition.getScope().get() == Prototype.class

        cleanup:
        context?.close()
    }

    void "test a factory bean with method"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype

class Bar1:
    pass

@Factory
class TestFactory:

    @Bean
    @Prototype
    def bar(self) -> Bar1:
        return Bar1()


''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('python.Bar1'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        def bar1 = getBean(context, 'python.Bar1')

        then:
        bar1BeanDefinition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED) == '@i.m.c.a.Prototype p.Bar1 p.TestFactory.bar()'
        bar1 != null
        bar1BeanDefinition.getScope().get() == Prototype.class

        cleanup:
        context.close()
    }

    void "test a factory bean with method and no return type"() {
        when:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype

class Bar1:
    pass

@Factory
class TestFactory:

    @Bean
    @Prototype
    def bar(self):
        return Bar1()


''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Factory methods declared with @Bean must specify a return type")
        e.message.contains("def bar(self)")
    }

    @PendingFeature(reason = "support static methods")
    void "test a factory bean with static method"() {
        given:
        def context = buildContext('''\
from micronaut.context.annotation import Factory, Bean, Prototype
from jakarta.inject import Singleton

class Bar1:
    pass

@Factory
class TestFactory:

    @Singleton
    @staticmethod
    def bar() -> Bar1:
        return Bar1()
''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('python.Bar1'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        def bar1 = getBean(context, 'python.Bar1')

        then:
        bar1BeanDefinition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED) == '@i.m.c.a.Prototype python.Bar1 python.TestFactory.bar()'
        bar1 != null
        bar1BeanDefinition.getScope().get() == Prototype.class

        cleanup:
        context.close()
    }
}
