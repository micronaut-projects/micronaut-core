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
package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection

class IntrospectionIncludeInjectedSpec extends AbstractTypeElementSpec {

    void "injection points are not bean properties by default"() {
        when:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Provider', source(''))

        then:
        introspection.propertyNames as List == ['plain']
    }

    void "includeInjected keeps injection points as bean properties"() {
        when:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Provider', source('includeInjected = true, '))

        then:
        introspection.propertyNames as Set == ['plain', 'field', 'setter', 'value'] as Set

        when:
        def instance = introspection.instantiate()
        introspection.getRequiredProperty('field', Object).set(instance, 'a')
        introspection.getRequiredProperty('setter', Object).set(instance, 'b')

        then:
        instance.field == 'a'
        instance.setterValue() == 'b'
    }

    void "includeInjected on BeanProperties applies to the class element"() {
        when:
        def names = buildClassElement('''
package test;

import io.micronaut.context.annotation.BeanProperties;
import jakarta.inject.Inject;

@BeanProperties(includeInjected = true)
class Bean {
    private String setter;

    @Inject
    public void setSetter(String setter) {
        this.setter = setter;
    }

    public String getSetter() {
        return setter;
    }
}
''') { ce -> ce.beanProperties*.name }

        then:
        names == ['setter']
    }

    private static String source(String member) {
        """
package test;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Introspected;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

@Introspected(${member}accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD})
public class Provider {
    @Inject
    public String field;

    @Value("\${foo:bar}")
    public String value;

    public String plain;

    private String setter;

    @Inject
    public void setSetter(String setter) {
        this.setter = setter;
    }

    public String setterValue() {
        return setter;
    }

    @PostConstruct
    public void getInit() {
    }
}
"""
    }
}
