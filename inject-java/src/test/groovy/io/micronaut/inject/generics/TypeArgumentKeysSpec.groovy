/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

/**
 * {@code BeanDefinition#getTypeArgumentKeys()} names the types a definition can answer
 * {@code getTypeArguments} for, so that a consumer that needs every super type a bean carries arguments into can
 * read what the compiler recorded instead of walking the hierarchy reflectively.
 */
class TypeArgumentKeysSpec extends AbstractTypeElementSpec {

    void "test the keys name the whole recorded hierarchy and answer the same arguments as the lookups"() {
        given:
        def definition = buildBeanDefinition('keys.BothRepo', '''
package keys;

import jakarta.inject.Singleton;

interface Repo<T> {}

interface Marker {}

abstract class Base<X> implements Marker {}

@Singleton
class BothRepo extends Base<Long> implements Repo<String> {}
''')

        expect: 'the parameterized interface, the parameterized super class and the bean type itself are all named'
        definition.typeArgumentKeys.toSet() == ['keys.BothRepo', 'keys.Base', 'keys.Marker', 'keys.Repo'].toSet()

        and: 'every key answers the lookup, a super type carrying no arguments of its own with an empty list'
        definition.getTypeArguments('keys.Repo')*.type == [String]
        definition.getTypeArguments('keys.Base')*.type == [Long]
        definition.getTypeArguments('keys.Marker').isEmpty()
        definition.getTypeArguments('keys.BothRepo').isEmpty()

        and: 'the name lookup and the class lookup agree for every key'
        definition.typeArgumentKeys.every {
            def type = definition.beanType.classLoader.loadClass(it)
            definition.getTypeArguments(type) == definition.getTypeArguments(it)
        }
    }

    void "test a bean that records no type argument anywhere answers no keys"() {
        given:
        def definition = buildBeanDefinition('keys.Plain', '''
package keys;

import jakarta.inject.Singleton;

interface Marker {}

@Singleton
class Plain implements Marker {}
''')

        expect: 'nothing was recorded, so nothing is named - not even the bean type'
        definition.typeArgumentKeys.isEmpty()
        definition.typeArguments.isEmpty()
    }

    void "test a proxy definition answers for the hierarchy of the type it proxies"() {
        given:
        def context = buildContext('''
package keys;

import jakarta.inject.Singleton;
import io.micronaut.aop.simple.Mutating;

interface Repo<T> {}

@Singleton
class ProxiedRepo implements Repo<Integer> {
    @Mutating("name")
    public String hello(String name) {
        return name;
    }
}
''')
        def proxied = context.classLoader.loadClass('keys.ProxiedRepo')
        def definition = context.getBeanDefinitions(proxied).find { it.isProxy() }

        expect: 'the keys are the target type and its super types, not the generated proxy class'
        definition != null
        definition.typeArgumentKeys.toSet() == ['keys.ProxiedRepo', 'keys.Repo'].toSet()
        definition.getTypeArguments('keys.Repo')*.type == [Integer]

        cleanup:
        context.close()
    }
}
