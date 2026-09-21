/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.expressions;

import io.micronaut.context.exceptions.ExpressionEvaluationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultExpressionEvaluationContextTest {

    @Test
    void getArgumentReturnsTheValueAtAValidIndex() {
        DefaultExpressionEvaluationContext context = new DefaultExpressionEvaluationContext(
            null, new Object[]{"first", "second"}, null, null);

        assertEquals("first", context.getArgument(0));
        assertEquals("second", context.getArgument(1));
    }

    @Test
    void getArgumentThrowsWhenIndexEqualsLength() {
        DefaultExpressionEvaluationContext context = new DefaultExpressionEvaluationContext(
            null, new Object[]{"first", "second"}, null, null);

        assertThrows(ExpressionEvaluationException.class, () -> context.getArgument(2));
    }

    @Test
    void getArgumentThrowsWhenIndexIsGreaterThanLength() {
        DefaultExpressionEvaluationContext context = new DefaultExpressionEvaluationContext(
            null, new Object[]{"first", "second"}, null, null);

        assertThrows(ExpressionEvaluationException.class, () -> context.getArgument(3));
    }

    @Test
    void getArgumentThrowsWhenIndexIsNegative() {
        DefaultExpressionEvaluationContext context = new DefaultExpressionEvaluationContext(
            null, new Object[]{"first", "second"}, null, null);

        assertThrows(ExpressionEvaluationException.class, () -> context.getArgument(-1));
    }

    @Test
    void getArgumentThrowsWhenArgumentsAreNull() {
        DefaultExpressionEvaluationContext context = new DefaultExpressionEvaluationContext(
            null, null, null, null);

        assertThrows(ExpressionEvaluationException.class, () -> context.getArgument(0));
    }

    @Test
    void getArgumentThrowsWhenArgumentsAreEmpty() {
        DefaultExpressionEvaluationContext context = new DefaultExpressionEvaluationContext(
            null, new Object[0], null, null);

        assertThrows(ExpressionEvaluationException.class, () -> context.getArgument(0));
    }
}
