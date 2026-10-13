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
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangedPropertyMentionTest {

    private static final ConfigurationChange CHANGE = ConfigurationChange.ofKeys(Set.of("app.greeting"));

    @Test
    void aPropertyInjectionPointNamesItsKey() {
        assertTrue(ConfigurationStaleness.mentionsChangedProperty(metadata(Property.class, "name", "app.greeting"), CHANGE));
        assertFalse(ConfigurationStaleness.mentionsChangedProperty(metadata(Property.class, "name", "app.other"), CHANGE));
        assertFalse(ConfigurationStaleness.mentionsChangedProperty(new MutableAnnotationMetadata(), CHANGE));
    }

    @Test
    void aValueNamesTheKeysOfItsPlaceholders() {
        assertTrue(ConfigurationStaleness.mentionsChangedProperty(value("${app.greeting}"), CHANGE));
        assertTrue(ConfigurationStaleness.mentionsChangedProperty(value("Hello ${app.name:you}, ${ app.greeting : hi }"), CHANGE));
        assertFalse(ConfigurationStaleness.mentionsChangedProperty(value("${app.name:you} and ${app.other}"), CHANGE));
        assertFalse(ConfigurationStaleness.mentionsChangedProperty(value("${} ${app.greeting"), CHANGE));
        assertFalse(ConfigurationStaleness.mentionsChangedProperty(value("plain"), CHANGE));
        // an expression reading the environment may read any key
        assertTrue(ConfigurationStaleness.mentionsChangedProperty(value("#{ env['other'] }"), CHANGE));
        assertFalse(ConfigurationStaleness.mentionsChangedProperty(value("#{ 1 + 1 }"), CHANGE));
    }

    @Test
    void noRunningContextHasNoRefresh() {
        assertNull(ConfigurationStaleness.refreshConfiguration(null));
    }

    private static MutableAnnotationMetadata value(String expression) {
        return metadata(Value.class, "value", expression);
    }

    private static MutableAnnotationMetadata metadata(Class<?> annotation, String member, String value) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(annotation.getName(), Map.of(member, value));
        return metadata;
    }
}
