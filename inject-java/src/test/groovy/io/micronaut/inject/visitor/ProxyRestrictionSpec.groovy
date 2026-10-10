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
package io.micronaut.inject.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.ast.ProxyRestriction
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.processing.ProxyableTypeValidator
import spock.lang.Unroll

/**
 * The reason a type cannot be proxied is answered without a failure being raised.
 */
class ProxyRestrictionSpec extends AbstractTypeElementSpec {

    @Unroll
    void "#description: #restriction"() {
        expect:
        buildClassElement(source) { ClassElement element ->
            assert element.name == 'test.MyBean'
            element.findProxyRestriction().orElse(null)
        } == restriction

        where:
        description               | source                                                                                                | restriction
        'a class'                 | 'package test; class MyBean {}'                                                                       | null
        'an interface'            | 'package test; interface MyBean {}'                                                                   | null
        'a non-sealed class'      | 'package test; non-sealed class MyBean extends Base {} sealed class Base permits MyBean {}'           | null
        'a final class'           | 'package test; final class MyBean {}'                                                                 | ProxyRestriction.FINAL
        'a record'                | 'package test; record MyBean(String name) {}'                                                         | ProxyRestriction.FINAL
        'a sealed class'          | 'package test; sealed class MyBean permits MyBean.Only { static final class Only extends MyBean {} }' | ProxyRestriction.SEALED
        'a sealed interface'      | 'package test; sealed interface MyBean permits MyBean.Only { final class Only implements MyBean {} }' | ProxyRestriction.SEALED
        'an enum'                 | 'package test; enum MyBean { A }'                                                                     | ProxyRestriction.ENUM
        'an enum with a body'     | 'package test; enum MyBean { A { void run() {} }; void run() {} }'                                    | ProxyRestriction.ENUM
    }

    void "an array and a primitive cannot be proxied"() {
        expect:
        buildClassElement('package test; class MyBean {}') { ClassElement element ->
            element.toArray().findProxyRestriction().orElse(null)
        } == ProxyRestriction.ARRAY
        PrimitiveElement.INT.findProxyRestriction().orElse(null) == ProxyRestriction.PRIMITIVE
    }

    @Unroll
    void "an array of primitives is an array: #description"() {
        expect:
        type.findProxyRestriction().orElse(null) == ProxyRestriction.ARRAY

        where:
        description     | type
        'int[]'         | PrimitiveElement.INT.toArray()
        'int[][]'       | PrimitiveElement.INT.toArray().toArray()
        'boolean[][][]' | PrimitiveElement.BOOLEAN.withArrayDimensions(3)
    }

    void "an array of primitives declared in the source is an array"() {
        expect:
        buildClassElement('package test; class MyBean { int[] values; long[][] grid; MyBean[][] beans; }') { ClassElement element ->
            ['values', 'grid', 'beans'].collect { String name ->
                element.findField(name).get().type.findProxyRestriction().orElse(null)
            }
        } == [ProxyRestriction.ARRAY, ProxyRestriction.ARRAY, ProxyRestriction.ARRAY]
    }

    void "the proxy validator rejects an array of primitives as an array"() {
        when:
        ProxyableTypeValidator.validateProxyable(PrimitiveElement.INT.toArray(), PrimitiveElement.INT)

        then:
        def e = thrown(ProcessingException)
        e.message.startsWith('Cannot apply AOP advice to array type')
    }
}
