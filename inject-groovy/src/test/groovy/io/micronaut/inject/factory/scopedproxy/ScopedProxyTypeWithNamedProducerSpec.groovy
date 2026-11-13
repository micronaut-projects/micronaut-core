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
package io.micronaut.inject.factory.scopedproxy

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
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
class ScopedProxyTypeWithNamedProducerSpec extends AbstractBeanDefinitionSpec {

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
        String scope = "@io.micronaut.inject.factory.scopedproxy.$scopeAnnotation"
        return """
package scopedproxyproducer

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

$scope ${logged ? '@Logged' : ''}
class Conn {
    private final String value

    Conn() {
        this("class")
    }

    Conn(String value) {
        this.value = value
    }

    String value() {
        return value
    }
}

@Factory
class ConnFactory {
    @Singleton
    @Named("second")
    Conn second() {
        return new Conn("second")
    }
}

@Around
@Retention(RetentionPolicy.RUNTIME)
@interface Logged {
}
"""
    }
}
