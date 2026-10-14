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
import io.micronaut.context.BeanDependencyGraph;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import io.micronaut.runtime.context.scope.refresh.RefreshResult;
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher;

import java.util.ArrayList;
import java.util.List;
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
final class ConfigurationStaleness {

    // the runtime's logger: what is logged here is logged as the runtime's
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);

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
        Optional<BeanDependencyGraph> graph = current instanceof ConfigurableBeanContext configurable ? configurable.findDependencyGraph() : Optional.empty();
        for (BeanRegistration<?> registration : current.getActiveBeanRegistrations(Qualifiers.any())) {
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
        for (BeanDefinitionReference<?> reference : current.getBeanDefinitionReferences()) {
            for (AnnotationValue<Requires> requires : reference.getAnnotationMetadata().getAnnotationValuesByType(Requires.class)) {
                String property = requires.stringValue("property").orElse(null);
                if (property != null && change.touches(property)) {
                    return reference.getBeanDefinitionName() + " requires a changed property";
                }
            }
        }
        return null;
    }

    private static boolean injectsChangedProperty(BeanDefinition<?> definition, ConfigurationChange change) {
        for (Argument<?> argument : definition.getConstructor().getArguments()) {
            if (change.mentions(argument.getAnnotationMetadata())) {
                return true;
            }
        }
        for (FieldInjectionPoint<?, ?> field : definition.getInjectedFields()) {
            if (change.mentions(field.getAnnotationMetadata())) {
                return true;
            }
        }
        for (MethodInjectionPoint<?, ?> method : definition.getInjectedMethods()) {
            for (Argument<?> argument : method.getArguments()) {
                if (change.mentions(argument.getAnnotationMetadata())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Refreshes the configuration of the running context.
     *
     * @return The result, or null when the context is not running or has no refresher, which restarts instead
     */
    @Nullable
    static RefreshResult refreshConfiguration(@Nullable ApplicationContext current) {
        if (current == null || !current.isRunning()) {
            return null;
        }
        try {
            return current.findBean(ConfigurationRefresher.class).map(ConfigurationRefresher::refresh).orElse(null);
        } catch (RuntimeException e) {
            LOG.warn("The configuration could not be refreshed in place ({}): restarting instead", e.getMessage());
            return null;
        }
    }

    /**
     * The definitions a context has of the given classes.
     *
     * @param applicationContext The context
     * @param classNames The classes, by name
     * @return Their definitions
     */
    static List<BeanDefinition<?>> definitionsNamed(ApplicationContext applicationContext, Set<String> classNames) {
        List<BeanDefinition<?>> definitions = new ArrayList<>();
        if (classNames.isEmpty()) {
            return definitions;
        }
        for (BeanDefinition<?> definition : applicationContext.getAllBeanDefinitions()) {
            if (classNames.contains(definition.getBeanType().getName())) {
                definitions.add(definition);
            }
        }
        return definitions;
    }
}
