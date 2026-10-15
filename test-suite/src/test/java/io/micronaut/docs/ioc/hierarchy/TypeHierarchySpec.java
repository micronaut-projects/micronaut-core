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
package io.micronaut.docs.ioc.hierarchy;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeHierarchies;
import io.micronaut.core.type.TypeHierarchy;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeHierarchySpec {

    @Test
    void readsTheRecordedHierarchy() {
        // tag::usage[]
        TypeHierarchy hierarchy = TypeHierarchies.find(Payload.class).orElseThrow(); // <1>

        List<Argument<?>> variables = hierarchy.getTypeVariables(); // <2>
        Argument<?> superclass = hierarchy.getGenericSuperclass().orElseThrow(); // <3>
        List<Argument<?>> closure = hierarchy.getTypeClosure(Argument.of(Payload.class, Integer.class)); // <4>
        // end::usage[]

        assertEquals("T", variables.get(0).getName());
        assertEquals(Number.class, variables.get(0).getType());
        assertEquals(ArrayList.class, superclass.getType());
        assertTrue(superclass.getTypeParameters()[0].isTypeVariable());
        assertEquals(List.of(Payload.class, ArrayList.class, AbstractList.class), closure.subList(0, 3).stream().map(Argument::getType).toList());
        assertEquals(Integer.class, closure.get(1).getTypeParameters()[0].getType());
        assertEquals(Supplier.class, closure.get(closure.size() - 2).getType());
        assertEquals(Object.class, closure.get(closure.size() - 1).getType());

        TypeHierarchy arrayList = TypeHierarchies.find(ArrayList.class).orElseThrow();
        assertEquals("E", arrayList.getTypeVariables().get(0).getName());
    }
}
