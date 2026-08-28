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

import io.micronaut.context.BeanContext
import io.micronaut.context.BeanContextConfiguration
import io.micronaut.context.BeanDefinitionRegistry
import io.micronaut.context.BeanDefinitionsProvider
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.event.ApplicationEventPublisherFactory
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.PrimaryQualifier
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Shared

import java.util.function.Supplier

class RuntimeBeanDefinitionSpec extends AbstractPythonTypeElementSpec {

    @Shared
    BeanContext sharedContext = BeanContext.build()

    void "test runtime bean definition registered with bean context"() {
        given:
        def foo = new Foo()
        def context = emptyBeanContext()
        context.registerBeanDefinition(RuntimeBeanDefinition.of(foo))
        context.start()

        expect:
        context.getBeanDefinition(Foo)
        context.getBean(Foo).is(foo)

        cleanup:
        context?.close()
    }

    void "test simple runtime bean definition"() {
        given:
        RuntimeBeanDefinition<Foo> bean = RuntimeBeanDefinition.of(new Foo())

        expect:
        bean.beanType == Foo
        bean.typeParameters.length == 0
        bean.typeArguments.size() == 0
        bean.annotationMetadata == AnnotationMetadata.EMPTY_METADATA
        bean.load().is(bean)
        bean.load(sharedContext).is(bean)
        bean.isEnabled(sharedContext)
        bean.declaredQualifier == null
        bean.isPresent()
        bean.singleton
    }

    void "test simple runtime bean definition with qualifier"() {
        given:
        RuntimeBeanDefinition<?> bean = RuntimeBeanDefinition
            .builder(Argument.of(Supplier, String), { -> { -> "Foo" } as Supplier<String> } as Supplier<Supplier<String>>)
            .qualifier(Qualifiers.byName("foo"))
            .scope(Prototype)
            .build()

        expect:
        bean.beanType == Supplier
        bean.scopeName.isPresent()
        bean.scope.isPresent()
        bean.scope.get() == Prototype
        bean.typeArguments.size() == 1
        bean.typeParameters.size() == 1
        bean.annotationMetadata == AnnotationMetadata.EMPTY_METADATA
        bean.load().is(bean)
        bean.load(sharedContext).is(bean)
        bean.isEnabled(sharedContext)
        bean.declaredQualifier == Qualifiers.byName("foo")
        bean.beanDefinitionName
        bean.isPresent()
        !bean.singleton
    }

    void "test from supplier runtime bean definition with qualifier"() {
        given:
        RuntimeBeanDefinition<Foo> bean = RuntimeBeanDefinition.builder(Foo, { -> new Foo() } as Supplier<Foo>)
            .qualifier(Qualifiers.byName("foo"))
            .exposedTypes(IFoo)
            .build()

        expect:
        bean.beanType == Foo
        bean.exposedTypes.size() == 1
        bean.exposedTypes.contains(IFoo)
        bean.annotationMetadata == AnnotationMetadata.EMPTY_METADATA
        bean.load().is(bean)
        bean.load(null).is(bean)
        bean.isEnabled(sharedContext)
        bean.declaredQualifier == Qualifiers.byName("foo")
        bean.beanDefinitionName
        bean.isPresent()
        !bean.scope.isPresent()
        !bean.singleton
    }

    void "test dynamic bean definition registration from Python context beans"() {
        given:
        RegistrarSupport.reset()
        def context = buildContext('''
from typing import Annotated
import java

from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Context, Executable
from micronaut.core.annotation import Order

BeanDefinitionRegistry = java.type("io.micronaut.context.BeanDefinitionRegistry")
Bar = java.type("io.micronaut.python.annotation.processing.test.RuntimeBeanDefinitionSpec$Bar")
Bazz = java.type("io.micronaut.python.annotation.processing.test.RuntimeBeanDefinitionSpec$Bazz")
RegistrarSupport = java.type("io.micronaut.python.annotation.processing.test.RuntimeBeanDefinitionSpec$RegistrarSupport")

@Singleton
class RuntimeFoo:
    def __init__(
        self,
        bar: Bar,
        another: Annotated[Bar, Named("another")],
        bazz: Annotated[Bazz, Named("test2")]
    ):
        self.bar = bar
        self.another = another
        self.bazz = bazz

    @Executable
    def get_bar_name(self) -> str:
        return self.bar.getName()

    @Executable
    def get_another_name(self) -> str:
        return self.another.getName()

    @Executable
    def get_bazz_num(self) -> int:
        return self.bazz.getNum()

@Context
@Order(-10)
class RegistrarA:
    def __init__(self, registry: BeanDefinitionRegistry):
        RegistrarSupport.registerA(registry)

@Context
@Order(-15)
class RegistrarB:
    def __init__(self, registry: BeanDefinitionRegistry):
        RegistrarSupport.registerB(registry)

@Context
class RegistrarC:
    def __init__(self, registry: BeanDefinitionRegistry):
        RegistrarSupport.registerC(registry)
''')

        when:
        def foo = getBean(context, "python.RuntimeFoo")

        then:
        foo.get_bazz_num() == 2
        foo.get_bar_name() == "primary"
        foo.get_another_name() == "another"
        RegistrarSupport.registrarAExecuted
        RegistrarSupport.registrarBExecuted
        RegistrarSupport.registrarCExecuted

        cleanup:
        context?.close()
        RegistrarSupport.reset()
    }

    static class Foo implements IFoo {
    }

    interface IFoo {
    }

    static class Bar {
        final String name

        Bar(String name) {
            this.name = name
        }
    }

    static class Stuff {
    }

    interface Bazz {
    }

    static class BazzImpl implements Bazz {
        final int num

        BazzImpl(int num) {
            this.num = num
        }
    }

    static class RegistrarSupport {
        static boolean registrarAExecuted = false
        static boolean registrarBExecuted = false
        static boolean registrarCExecuted = false

        static void reset() {
            registrarAExecuted = false
            registrarBExecuted = false
            registrarCExecuted = false
        }

        static void registerA(BeanDefinitionRegistry registry) {
            registrarAExecuted = true
            if (!registrarBExecuted) {
                throw new IllegalStateException("RegistrarB should have been executed first")
            }
            if (registrarCExecuted) {
                throw new IllegalStateException("RegistrarC should not have been executed yet")
            }
            registry.registerBeanDefinition(
                RuntimeBeanDefinition.builder(Bar, { -> new Bar("primary") } as Supplier<Bar>)
                    .qualifier(PrimaryQualifier.instance())
                    .build()
            )
        }

        static void registerB(BeanDefinitionRegistry registry) {
            registrarBExecuted = true
            if (registrarCExecuted) {
                throw new IllegalStateException("RegistrarC should not have been executed yet")
            }
            if (registrarAExecuted) {
                throw new IllegalStateException("RegistrarA should not have been executed yet")
            }
            registry.registerBeanDefinition(
                RuntimeBeanDefinition.builder(Bar, { -> new Bar("another") } as Supplier<Bar>)
                    .qualifier(Qualifiers.byName("another"))
                    .build()
            )
        }

        static void registerC(BeanDefinitionRegistry registry) {
            if (!registrarBExecuted) {
                throw new IllegalStateException("RegistrarB should have been executed first")
            }
            if (!registrarAExecuted) {
                throw new IllegalStateException("RegistrarA should have been executed first")
            }
            registrarCExecuted = true
            registry.registerBeanDefinition(RuntimeBeanDefinition.of(new Stuff()))
            registry.registerBeanDefinition(
                RuntimeBeanDefinition.builder(Bazz, { -> new BazzImpl(1) } as Supplier<Bazz>)
                    .named("test")
                    .build()
            )
            registry.registerBeanDefinition(
                RuntimeBeanDefinition.builder(Bazz, { -> new BazzImpl(2) } as Supplier<Bazz>)
                    .named("test2")
                    .build()
            )
        }
    }

    private static BeanContext emptyBeanContext() {
        new DefaultBeanContext(new BeanContextConfiguration() {
            @Override
            BeanDefinitionsProvider getBeanDefinitionsProvider() {
                return { ClassLoader ignored -> [new ApplicationEventPublisherFactory<>()] } as BeanDefinitionsProvider
            }
        })
    }
}
