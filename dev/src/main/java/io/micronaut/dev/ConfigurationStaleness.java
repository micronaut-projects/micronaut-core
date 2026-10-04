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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Whether a refresh of the configuration in place covered a change, or the running generation still holds a value
 * the change made stale, which only a restart replaces.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class ConfigurationStaleness {

    private ConfigurationStaleness() {
    }

    /**
     * What a refresh cannot update: a singleton that received a changed property through {@code @Value}
     * or {@code @Property} rather than a configuration bean, and a definition whose {@code @Requires} names a
     * changed property, whose presence the change may have flipped. Either one makes the batch restart.
     *
     * @return Why a restart is needed, or null when the refresh covered the change
     */
    @Nullable
    static String afterRefresh(@Nullable ApplicationContext current, ConfigurationChange change) {
        if (current == null || change.all()) {
            return current == null ? null : "every property may have changed";
        }
        // the singletons, and through the graph what they hold: a prototype a singleton received and keeps
        // is as stale as the singleton would be
        Set<BeanDefinition<?>> definitions = new LinkedHashSet<>();
        Optional<io.micronaut.context.BeanDependencyGraph> graph = current.findDependencyGraph();
        for (BeanRegistration<?> registration : current.getActiveBeanRegistrations(io.micronaut.inject.qualifiers.Qualifiers.any())) {
            BeanDefinition<?> definition = registration.getBeanDefinition();
            definitions.add(definition);
            graph.ifPresent(g -> definitions.addAll(g.transitiveDependenciesOf(definition)));
        }
        for (BeanDefinition<?> definition : definitions) {
            if (definition.isConfigurationProperties()) {
                continue;
            }
            if (injectsChangedProperty(definition, change)) {
                return definition.getBeanType().getName() + " injects a changed property directly";
            }
        }
        for (io.micronaut.inject.BeanDefinitionReference<?> reference : current.getBeanDefinitionReferences()) {
            for (io.micronaut.core.annotation.AnnotationValue<io.micronaut.context.annotation.Requires> requires : reference.getAnnotationMetadata().getAnnotationValuesByType(io.micronaut.context.annotation.Requires.class)) {
                String property = requires.stringValue("property").orElse(null);
                if (property != null && change.touches(property)) {
                    return reference.getBeanDefinitionName() + " requires a changed property";
                }
            }
        }
        return null;
    }

    private static boolean injectsChangedProperty(BeanDefinition<?> definition, ConfigurationChange change) {
        for (io.micronaut.core.type.Argument<?> argument : definition.getConstructor().getArguments()) {
            if (mentionsChangedProperty(argument.getAnnotationMetadata(), change)) {
                return true;
            }
        }
        for (io.micronaut.inject.FieldInjectionPoint<?, ?> field : definition.getInjectedFields()) {
            if (mentionsChangedProperty(field.getAnnotationMetadata(), change)) {
                return true;
            }
        }
        for (io.micronaut.inject.MethodInjectionPoint<?, ?> method : definition.getInjectedMethods()) {
            for (io.micronaut.core.type.Argument<?> argument : method.getArguments()) {
                if (mentionsChangedProperty(argument.getAnnotationMetadata(), change)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether an injection point names a changed property: {@code @Property(name = "key")}, or a
     * {@code @Value} expression with a {@code ${key}} or {@code ${key:default}} placeholder.
     */
    private static boolean mentionsChangedProperty(io.micronaut.core.annotation.AnnotationMetadata metadata, ConfigurationChange change) {
        // the raw values: a string value read through the metadata has its placeholders resolved already
        Object property = metadata.getValues(io.micronaut.context.annotation.Property.class.getName()).get("name");
        if (property != null && change.touches(property.toString())) {
            return true;
        }
        Object raw = metadata.getValues(io.micronaut.context.annotation.Value.class.getName()).get("value");
        if (raw == null) {
            return false;
        }
        String expression = raw.toString();
        if (expression.contains("#{") && expression.contains("env")) {
            // an evaluated expression reading the environment: which keys it reads is not known, so any change counts
            return true;
        }
        int start = expression.indexOf("${");
        while (start >= 0) {
            int end = expression.indexOf('}', start);
            if (end < 0) {
                break;
            }
            String placeholder = expression.substring(start + 2, end);
            int colon = placeholder.indexOf(':');
            String key = (colon >= 0 ? placeholder.substring(0, colon) : placeholder).trim();
            if (!key.isEmpty() && change.touches(key)) {
                return true;
            }
            start = expression.indexOf("${", end);
        }
        return false;
    }
}
