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
package io.micronaut.kotlin.processing.beans

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.beans.BeanIntrospection

class IntrospectionIncludeInjectedSpec extends AbstractKotlinCompilerSpec {

    void "an injected setter method is not a bean property by default"() {
        when:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Provider', source(''))

        then:
        !introspection.propertyNames.contains('setter')
    }

    void "includeInjected keeps injection points as bean properties"() {
        when:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Provider', source('(includeInjected = true)'))

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

    private static String source(String member) {
        """
package test

import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Introspected
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject

@Introspected${member}
class Provider {
    @set:Inject
    var field: String? = null

    @set:Value("bar")
    var value: String? = null

    var plain: String? = null

    private var setter: String? = null

    @Inject
    fun setSetter(setter: String) {
        this.setter = setter
    }

    fun setterValue() = setter

    @PostConstruct
    fun getInit() {
    }
}
"""
    }
}
