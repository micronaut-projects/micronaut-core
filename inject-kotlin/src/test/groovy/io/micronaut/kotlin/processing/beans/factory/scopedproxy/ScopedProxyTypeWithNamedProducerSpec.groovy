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
package io.micronaut.kotlin.processing.beans.factory.scopedproxy

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBinding
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * A factory that produces a type annotated with a scoped proxy scope must not inherit the client proxy of that
 * scope when the producer declares a scope or a qualifier of its own, whether the scope is written on the type
 * directly or comes from an annotation mapper.
 */
class ScopedProxyTypeWithNamedProducerSpec extends AbstractKotlinCompilerSpec {

    void "a named producer of a type with a #description scoped proxy scope produces no proxy"() {
        given:
        ApplicationContext context = buildContext(source(scopeAnnotation, false))
        Class<?> type = context.classLoader.loadClass('scopedproxyproducer.Conn')

        when:
        BeanDefinition<?> produced = context.getBeanDefinition(type, Qualifiers.byName('second'))
        BeanDefinition<?> classBean = context.getBeanDefinitions(type).find { !it.stringValue(AnnotationUtil.NAMED).isPresent() }

        then:
        !produced.isProxy()
        !produced.hasStereotype(Around)
        produced.getAnnotationValuesByType(InterceptorBinding).isEmpty()
        classBean.isProxy()

        cleanup:
        context.close()

        where:
        description   | scopeAnnotation
        'direct'      | 'ProducerTestScope'
        'mapped'      | 'MappedProducerTestScope'
        'meta-mapped' | 'MetaMappedProducerTestScope'
    }

    void "a named producer keeps only the around advice of a type with a #description scoped proxy scope"() {
        given:
        ApplicationContext context = buildContext(source(scopeAnnotation, true))
        Class<?> type = context.classLoader.loadClass('scopedproxyproducer.Conn')

        when:
        BeanDefinition<?> produced = context.getBeanDefinition(type, Qualifiers.byName('second'))

        then:
        produced.getAnnotationValuesByType(InterceptorBinding).collect { it.stringValue().orElse(null) } == ['scopedproxyproducer.Logged']

        cleanup:
        context.close()

        where:
        description   | scopeAnnotation
        'direct'      | 'ProducerTestScope'
        'mapped'      | 'MappedProducerTestScope'
        'meta-mapped' | 'MetaMappedProducerTestScope'
    }

    private static String source(String scopeAnnotation, boolean logged) {
        String scope = "@io.micronaut.kotlin.processing.beans.factory.scopedproxy.$scopeAnnotation"
        return """
package scopedproxyproducer

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
import jakarta.inject.Singleton

$scope ${logged ? '@Logged' : ''}
open class Conn(private val value: String) {
    constructor() : this("class")

    open fun value(): String = value
}

@Factory
class ConnFactory {
    @Singleton
    @Named("second")
    fun second(): Conn = Conn("second")
}

@Around
@Retention(AnnotationRetention.RUNTIME)
annotation class Logged
"""
    }
}
