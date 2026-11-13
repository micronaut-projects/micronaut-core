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
package io.micronaut.python.annotation.processing.test.factory.scopedproxy

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBinding
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * A Python factory that produces a type annotated with a scoped proxy scope must not inherit the client proxy of that
 * scope when the producer declares a scope or a qualifier of its own, whether the scope is written on the type
 * directly or comes from an annotation mapper. The produced types are Java classes: a Python class with around advice
 * is proxied by the Python runtime instead.
 */
class ScopedProxyTypeWithNamedProducerSpec extends AbstractPythonTypeElementSpec {

    void "a named producer of a type with a #description scoped proxy scope produces no proxy"() {
        given:
        ApplicationContext context = buildContext(source(producedType))

        when:
        BeanDefinition<?> produced = context.getBeanDefinition(producedType, Qualifiers.byName('second'))

        then:
        !produced.isProxy()
        !produced.hasStereotype(Around)
        produced.getAnnotationValuesByType(InterceptorBinding).isEmpty()
        context.getBean(producedType, Qualifiers.byName('second')).value() == 'conn'

        cleanup:
        context.close()

        where:
        description   | producedType
        'direct'      | DirectScopedConn
        'mapped'      | MappedScopedConn
        'meta-mapped' | MetaMappedScopedConn
    }

    void "a named producer keeps only the around advice of a type with a #description scoped proxy scope"() {
        given:
        ApplicationContext context = buildContext(source(producedType))

        when:
        BeanDefinition<?> produced = context.getBeanDefinition(producedType, Qualifiers.byName('second'))

        then:
        produced.getAnnotationValuesByType(InterceptorBinding).collect { it.stringValue().orElse(null) } == [ProducerTestLogged.name]

        cleanup:
        context.close()

        where:
        description   | producedType
        'direct'      | DirectScopedLoggedConn
        'meta-mapped' | MetaMappedScopedLoggedConn
    }

    private static String source(Class<?> producedType) {
        return """\\
from micronaut.context.annotation import Bean, Factory
from jakarta.inject import Named, Singleton
import java

Conn = java.type("${producedType.name}")

@Factory
class ConnFactory:
    @Bean
    @Singleton
    @Named("second")
    def second(self) -> Conn:
        return Conn()
"""
    }
}
