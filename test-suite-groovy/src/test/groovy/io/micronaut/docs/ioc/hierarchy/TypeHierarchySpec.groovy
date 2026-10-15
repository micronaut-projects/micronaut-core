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
package io.micronaut.docs.ioc.hierarchy

import io.micronaut.core.type.Argument
import io.micronaut.core.type.TypeHierarchies
import spock.lang.Specification

import java.util.function.Supplier

class TypeHierarchySpec extends Specification {

    void "reads the recorded hierarchy"() {
        given:
        // tag::usage[]
        def hierarchy = TypeHierarchies.find(Payload).orElseThrow() // <1>

        def variables = hierarchy.typeVariables // <2>
        def superclass = hierarchy.genericSuperclass.orElseThrow() // <3>
        def closure = hierarchy.getTypeClosure(Argument.of(Payload, Integer)) // <4>
        // end::usage[]

        expect:
        variables*.name == ['T']
        variables[0].type == Number
        superclass.type == ArrayList
        superclass.typeParameters[0].isTypeVariable()
        closure*.type.take(3) == [Payload, ArrayList, AbstractList]
        closure[1].typeParameters[0].type == Integer
        closure[-2].type == Supplier
        closure[-1].type == Object
        TypeHierarchies.find(ArrayList).orElseThrow().typeVariables*.name == ['E']
    }
}
