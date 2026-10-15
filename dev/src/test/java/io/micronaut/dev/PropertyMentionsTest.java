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
package io.micronaut.dev;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.expressions.AbstractEvaluatedExpression;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyMentionsTest {

    private static final ConfigurationChange CHANGE = ConfigurationChange.ofKeys(Set.of("app.greeting"));

    @Test
    void aPropertyInjectionPointNamesItsKey() {
        assertTrue(mentions(metadata(Property.class, "name", "app.greeting")));
        assertFalse(mentions(metadata(Property.class, "name", "app.other")));
        assertFalse(mentions(new MutableAnnotationMetadata()));
    }

    @Test
    void aValueNamesTheKeysOfItsPlaceholders() {
        assertTrue(mentions(value("${app.greeting}")));
        assertTrue(mentions(value("Hello ${app.name:you}, ${ app.greeting : hi }")));
        assertFalse(mentions(value("${app.name:you} and ${app.other}")));
        assertFalse(mentions(value("${} ${app.greeting")));
        assertFalse(mentions(value("plain")));
    }

    @Test
    void aValueNamesTheKeysOfNestedAndChainedPlaceholders() {
        assertTrue(mentions(value("${app.other:${app.greeting}}")));
        assertTrue(mentions(value("${app.other:${app.greeting:hi}}")));
        assertTrue(mentions(value("${app.other:app.greeting:hi}")));
        assertFalse(mentions(value("${app.other:${app.name}}")));
    }

    @Test
    void anExpressionReadingTheEnvironmentOrABeanNamesEveryKey() {
        assertTrue(mentions(expression("#{ env['other'] }")));
        assertTrue(mentions(expression("#{ ctx[SomeConfig].x }")));
        assertFalse(mentions(expression("#{ 1 + 1 }")));
    }

    @Test
    void anExpressionOfABeanAsTheContextHoldsItIsReadAsWritten() {
        // at runtime the metadata holds the expression class generated for the bean, not the compiler's reference
        assertTrue(mentions(generated("#{ env['other'] }")));
        assertTrue(mentions(generated("#{ ctx[SomeConfig].x }")));
        assertFalse(mentions(generated("#{ 1 + 1 }")));
    }

    private static boolean mentions(MutableAnnotationMetadata metadata) {
        return PropertyMentions.mentions(CHANGE, metadata);
    }

    private static MutableAnnotationMetadata value(String expression) {
        return metadata(Value.class, "value", expression);
    }

    private static MutableAnnotationMetadata expression(String expression) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Value.class.getName(), Map.of("value",
            new EvaluatedExpressionReference(expression, Value.class.getName(), "value", "Expr0")));
        return metadata;
    }

    private static MutableAnnotationMetadata generated(String expression) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Value.class.getName(), Map.of("value", new AbstractEvaluatedExpression(expression) { }));
        return metadata;
    }

    private static MutableAnnotationMetadata metadata(Class<?> annotation, String member, String value) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(annotation.getName(), Map.of(member, value));
        return metadata;
    }
}
