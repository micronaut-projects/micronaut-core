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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Supplier

class TypeHierarchySpec {

    @Test
    fun readsTheRecordedHierarchy() {
        // tag::usage[]
        val hierarchy = TypeHierarchies.find(Payload::class.java).orElseThrow() // <1>

        val variables = hierarchy.typeVariables // <2>
        val superclass = hierarchy.genericSuperclass.orElseThrow() // <3>
        val closure = hierarchy.getTypeClosure(Argument.of(Payload::class.java, Int::class.javaObjectType)) // <4>
        // end::usage[]

        assertEquals("T", variables[0].name)
        assertEquals(Number::class.java, variables[0].type)
        assertEquals(ArrayList::class.java, superclass.type)
        assertTrue(superclass.typeParameters[0].isTypeVariable)
        assertEquals(listOf(Payload::class.java, ArrayList::class.java, java.util.AbstractList::class.java), closure.take(3).map { it.type })
        assertEquals(Int::class.javaObjectType, closure[1].typeParameters[0].type)
        assertEquals(Supplier::class.java, closure[closure.size - 2].type)
        assertEquals(Any::class.java, closure[closure.size - 1].type)
        assertEquals("E", TypeHierarchies.find(ArrayList::class.java).orElseThrow().typeVariables[0].name)
    }
}
