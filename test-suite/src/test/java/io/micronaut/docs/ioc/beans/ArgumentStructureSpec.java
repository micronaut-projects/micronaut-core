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
package io.micronaut.docs.ioc.beans;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.type.WildcardArgument;
import junit.framework.TestCase;

import java.util.List;

public class ArgumentStructureSpec extends TestCase {

    public void testArgumentStructure() {
        // tag::structure[]
        BeanIntrospection<Catalog> introspection = BeanIntrospection.getIntrospection(Catalog.class);
        Argument<?> prices = introspection.getRequiredProperty("prices", List.class).asArgument(); // List<? extends Number>
        Argument<?> items = introspection.getRequiredProperty("items", List.class).asArgument(); // List<T>
        Argument<?> samples = introspection.getRequiredProperty("samples", Object[].class).asArgument(); // T[]

        Argument<?> wildcard = prices.getTypeParameters()[0];
        assertTrue(wildcard.isWildcard()); // <1>
        assertEquals(Number.class, ((WildcardArgument<?>) wildcard).getUpperBounds().get(0).getType());

        Argument<?> variable = items.getTypeParameters()[0];
        assertTrue(variable.isUnresolvedTypeVariable()); // <2>
        assertEquals("T", ((GenericPlaceholder<?>) variable).getVariableName());

        assertTrue(samples.componentType().equalsStructure(variable)); // <3>
        assertTrue(variable.arrayType().equalsStructure(samples));

        Argument<?> listOfNumber = Argument.listOf(Number.class);
        assertTrue(prices.equalsType(listOfNumber)); // <4>
        assertFalse(prices.equalsStructure(listOfNumber));
        // end::structure[]
    }
}
