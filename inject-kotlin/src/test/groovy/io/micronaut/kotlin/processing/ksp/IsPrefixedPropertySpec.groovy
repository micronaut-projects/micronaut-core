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
package io.micronaut.kotlin.processing.ksp

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.ast.ClassElement

/**
 * The getter of a Kotlin property whose name starts with {@code is} has the name of the property, and its setter
 * replaces the {@code is} with {@code set}, whatever the type of the property (KSP #2275). A name such as
 * {@code isbn}, where {@code is} is not followed by an upper case letter, keeps the usual {@code get} and {@code set}.
 */
class IsPrefixedPropertySpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Flags {
    var isActive: Boolean = false
    var isCount: Int = 0
    var isLabel: String = "label"
    val isReady: Boolean? = true
    var isbn: String = "isbn"
    var island: Boolean = false
}
'''

    void "the accessors of is-prefixed properties are the ones Kotlin compiles"() {
        when:
        Map<String, List<String>> accessors = buildClassElementMapped('test.Flags', SOURCE) { ClassElement ce ->
            ce.beanProperties.collectEntries {
                [(it.name): [it.type.name, it.readMethod.map { m -> m.name }.orElse(null), it.writeMethod.map { m -> m.name }.orElse(null)]]
            }
        }

        then:
        accessors == [
                isActive: ['boolean', 'isActive', 'setActive'],
                isCount : ['int', 'isCount', 'setCount'],
                isLabel : ['java.lang.String', 'isLabel', 'setLabel'],
                isReady : ['java.lang.Boolean', 'isReady', null],
                isbn    : ['java.lang.String', 'getIsbn', 'setIsbn'],
                island  : ['boolean', 'getIsland', 'setIsland']
        ]
    }

    void "an introspection reads and writes is-prefixed properties"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Flags', SOURCE)
        def flags = introspection.instantiate()

        when:
        introspection.getRequiredProperty('isActive', Boolean).set(flags, true)
        introspection.getRequiredProperty('isCount', Integer).set(flags, 3)
        introspection.getRequiredProperty('isLabel', String).set(flags, 'changed')
        introspection.getRequiredProperty('isbn', String).set(flags, '978')
        introspection.getRequiredProperty('island', Boolean).set(flags, true)

        then:
        introspection.propertyNames as Set == ['isActive', 'isCount', 'isLabel', 'isReady', 'isbn', 'island'] as Set
        introspection.getRequiredProperty('isActive', Boolean).get(flags) == true
        flags.isActive()
        introspection.getRequiredProperty('isCount', Integer).get(flags) == 3
        flags.isCount() == 3
        introspection.getRequiredProperty('isLabel', String).get(flags) == 'changed'
        introspection.getRequiredProperty('isReady', Boolean).get(flags) == true
        introspection.getRequiredProperty('isReady', Boolean).isReadOnly()
        introspection.getRequiredProperty('isbn', String).get(flags) == '978'
        flags.getIsbn() == '978'
        introspection.getRequiredProperty('island', Boolean).get(flags) == true
    }
}
