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
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The refresher of an application context.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@NullMarked
final class DefaultConfigurationRefresher implements ConfigurationRefresher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConfigurationRefresher.class);

    private final ApplicationContext context;
    private final Environment environment;
    private final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /**
     * The properties as resolved at the last refresh, or at creation: the side a refresh compares the
     * sources against, which is what tells a removed key from one that never existed.
     */
    private Map<String, Object> lastResolved;

    DefaultConfigurationRefresher(ApplicationContext context) {
        this.context = context;
        this.environment = context.getEnvironment();
        this.lastResolved = resolvedProperties();
    }

    @Override
    public synchronized RefreshResult refresh() {
        // the read of the sources and the phases under one lock: two refreshes cannot overtake each other
        Map<String, Object> before = lastResolved;
        environment.refresh();
        Map<String, Object> after = resolvedProperties();
        lastResolved = after;
        return apply(diff(before, after), true);
    }

    @Override
    public synchronized RefreshResult refreshAll() {
        environment.refresh();
        lastResolved = resolvedProperties();
        return apply(ConfigurationChange.ofAll(), true);
    }

    /**
     * Every property the sources define, resolved: the keys of every source, each with the value the
     * environment gives it once the sources are ordered and the placeholders expanded.
     */
    private Map<String, Object> resolvedProperties() {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (PropertySource source : environment.getPropertySources()) {
            for (String key : source) {
                if (!resolved.containsKey(key)) {
                    resolved.put(key, environment.getProperty(key, Object.class).orElse(null));
                }
            }
        }
        return resolved;
    }

    /**
     * The change between two resolutions: a key is changed when its value differs, added when it had
     * none before, removed when it has none now; previous holds the values that existed before and
     * current the ones that exist now.
     */
    private static ConfigurationChange diff(Map<String, Object> before, Map<String, Object> after) {
        Set<String> changed = new java.util.LinkedHashSet<>();
        Map<String, Object> previous = new LinkedHashMap<>();
        Map<String, Object> current = new LinkedHashMap<>();
        Set<String> keys = new java.util.LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            Object was = before.get(key);
            Object is = after.get(key);
            if (java.util.Objects.equals(was, is)) {
                continue;
            }
            changed.add(key);
            if (was != null) {
                previous.put(key, was);
            }
            if (is != null) {
                current.put(key, is);
            }
        }
        return new ConfigurationChange(false, changed, previous, current);
    }

    @Override
    public synchronized RefreshResult refresh(ConfigurationChange change) {
        // the caller updated the environment and knows the change: what the environment resolves now is the
        // baseline of the next refresh, or the same change would be found and applied again
        lastResolved = resolvedProperties();
        return apply(change, true);
    }

    /**
     * Applies the change a {@link RefreshEvent} something else published carries: the phases run, and
     * the event that triggered them is not published again.
     *
     * @param event The event
     * @return The result
     */
    synchronized RefreshResult applyEvent(RefreshEvent event) {
        Map<String, Object> source = event.getSource();
        ConfigurationChange change = source == RefreshEvent.ALL_KEYS ? ConfigurationChange.ofAll() : toChange(source);
        // the publisher refreshed the environment: what it resolves now is the next refresh's starting point
        lastResolved = resolvedProperties();
        return apply(change, false);
    }

    /**
     * @return Whether a refresh is running on the current thread, which the scope uses to leave the
     *         legacy event the refresh publishes to the listeners that expect it
     */
    boolean isApplying() {
        return applying.get();
    }

    /**
     * The change for the keys a legacy event reports: the values it carries are the previous ones, and
     * the environment, refreshed by the publisher, gives the current ones; a key with no value before is
     * an addition.
     */
    private ConfigurationChange toChange(Map<String, Object> reported) {
        Map<String, Object> previous = new LinkedHashMap<>();
        Map<String, Object> current = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : reported.entrySet()) {
            Object now = environment.getProperty(entry.getKey(), Object.class).orElse(null);
            if (entry.getValue() != null && !java.util.Objects.equals(entry.getValue(), now)) {
                previous.put(entry.getKey(), entry.getValue());
            }
            if (now != null) {
                current.put(entry.getKey(), now);
            }
        }
        return new ConfigurationChange(false, reported.keySet(), previous, current);
    }

    private synchronized RefreshResult apply(ConfigurationChange change, boolean publishLegacyEvent) {
        if (!change.all() && change.changed().isEmpty()) {
            // nothing changed: no phase runs and no event is published, as before the refresher
            return new RefreshResult(change, List.of(), List.of(), 0, List.of());
        }
        applying.set(Boolean.TRUE);
        try {
            List<BeanDefinition<?>> rebound = new ArrayList<>();
            List<BeanDefinition<?>> recreated = new ArrayList<>();
            rebind(change, rebound, recreated);
            int disposed = context.findBean(RefreshScope.class).map(scope -> scope.disposeAffected(change)).orElse(0);
            List<ConfigurationWatcher.Outcome> outcomes = context instanceof DefaultBeanContext defaultBeanContext
                ? defaultBeanContext.notifyConfigurationChange(change)
                : List.of();
            RefreshResult result = new RefreshResult(change, rebound, recreated, disposed, outcomes);
            context.publishEvent(new ConfigurationRefreshedEvent(this, result));
            if (publishLegacyEvent) {
                // the listeners written before the refresher keep working: the scope leaves this one alone
                context.publishEvent(change.all() ? new RefreshEvent() : new RefreshEvent(legacyChanges(change)));
            }
            return result;
        } finally {
            applying.set(Boolean.FALSE);
        }
    }

    /**
     * The map a legacy event carries: every changed key, with its previous value where it had one and
     * its current value otherwise, since the listeners select on the keys.
     */
    private static Map<String, Object> legacyChanges(ConfigurationChange change) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String key : change.changed()) {
            changes.put(key, change.previous().containsKey(key) ? change.previous().get(key) : change.current().get(key));
        }
        return changes;
    }

    /**
     * Phase one: the configuration beans the change touches are bound again, in place where their
     * values are injected through setters and fields, by a new instance where the constructor binds
     * them or a key they held went away.
     */
    private void rebind(ConfigurationChange change, List<BeanDefinition<?>> rebound, List<BeanDefinition<?>> recreated) {
        if (context instanceof DefaultBeanContext defaultBeanContext) {
            // an @EachProperty entry added or removed is a definition added or removed: the candidates are computed again
            defaultBeanContext.invalidateConfigurationCandidates();
        }
        for (BeanRegistration<?> registration : new ArrayList<>(context.getActiveBeanRegistrations(Qualifiers.byStereotype(ConfigurationReader.class)))) {
            BeanDefinition<?> definition = registration.getBeanDefinition();
            String prefix = prefixOf(registration);
            if (!change.all() && (prefix == null || !change.touches(prefix))) {
                continue;
            }
            Object bean = registration.getBean();
            if (bean != null && !definition.getBeanType().isInstance(bean)) {
                // a BeanCreatedEventListener replaced the configuration bean with an instance of another type, which
                // cannot be bound: the bean is skipped and the other configuration beans are still refreshed
                LOG.warn("Configuration bean [{}] was replaced by an instance of [{}] and can not be refreshed",
                    definition.getBeanType().getName(), bean.getClass().getName());
                continue;
            }
            if (prefix != null && definition.isIterable() && !change.all() && environment.getProperties(prefix).isEmpty()) {
                // an  entry whose keys all went: the bean goes with them
                context.destroyBean(registration);
                recreated.add(definition);
                continue;
            }
            // a removed key is reset to its default by a new instance only where the graph recreates the beans that
            // received the old one: without it, a holder that cached the old instance, as the router caches a filter,
            // would keep a configuration that no refresh reaches any more, so the bean is rebound in place as before
            boolean recreate = bindsThroughConstructor(definition)
                || (prefix != null && lostKeys(change, prefix) && context.findDependencyGraph().isPresent());
            if (recreate && context instanceof DefaultBeanContext defaultBeanContext && bean != null) {
                if (context.findDependencyGraph().isEmpty()) {
                    // no graph outside development mode: the beans that received the instance are found by the
                    // type of their injection points, and destroyed first so that they are created again on top
                    destroyDependentsByType(defaultBeanContext, definition);
                }
                if (defaultBeanContext.recreateBean(bean)) {
                    recreated.add(definition);
                    continue;
                }
                context.refreshBean(registration);
                rebound.add(definition);
            } else {
                context.refreshBean(registration);
                rebound.add(definition);
            }
        }
    }

    /**
     * Destroys the singletons that received the bean: through the graph when one is recorded, by the
     * types of their injection points otherwise.
     */
    private void destroyDependents(DefaultBeanContext beanContext, BeanDefinition<?> definition) {
        Optional<io.micronaut.context.BeanDependencyGraph> graph = context.findDependencyGraph();
        if (graph.isEmpty()) {
            destroyDependentsByType(beanContext, definition);
            return;
        }
        List<BeanDefinition<?>> dependents = new ArrayList<>(graph.get().transitiveDependentsOf(definition));
        java.util.Collections.reverse(dependents);
        for (BeanDefinition<?> dependent : dependents) {
            for (BeanRegistration<?> registration : beanContext.getActiveBeanRegistrations(Qualifiers.any())) {
                if (registration.getBeanDefinition().equals(dependent) && dependent.isSingleton()) {
                    beanContext.destroyBean(registration);
                }
            }
        }
    }

    /**
     * Destroys the singletons that received the configuration bean through a constructor, a field or
     * a method, judged by the type of the injection point, and their own dependents in turn.
     */
    private void destroyDependentsByType(DefaultBeanContext beanContext, BeanDefinition<?> definition) {
        Set<Class<?>> types = new java.util.HashSet<>();
        types.add(definition.getBeanType());
        boolean found = true;
        List<BeanRegistration<?>> toDestroy = new ArrayList<>();
        while (found) {
            found = false;
            for (BeanRegistration<?> registration : beanContext.getActiveBeanRegistrations(Qualifiers.any())) {
                BeanDefinition<?> candidate = registration.getBeanDefinition();
                if (candidate == definition || toDestroy.contains(registration) || !candidate.isSingleton()) {
                    continue;
                }
                if (injects(candidate, types)) {
                    toDestroy.add(registration);
                    types.add(candidate.getBeanType());
                    found = true;
                }
            }
        }
        for (int i = toDestroy.size() - 1; i >= 0; i--) {
            beanContext.destroyBean(toDestroy.get(i));
        }
    }

    private static boolean injects(BeanDefinition<?> definition, Set<Class<?>> types) {
        for (io.micronaut.core.type.Argument<?> argument : definition.getConstructor().getArguments()) {
            if (isOneOf(argument, types)) {
                return true;
            }
        }
        for (io.micronaut.inject.FieldInjectionPoint<?, ?> field : definition.getInjectedFields()) {
            if (isOneOf(field.asArgument(), types)) {
                return true;
            }
        }
        for (io.micronaut.inject.MethodInjectionPoint<?, ?> method : definition.getInjectedMethods()) {
            for (io.micronaut.core.type.Argument<?> argument : method.getArguments()) {
                if (isOneOf(argument, types)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOneOf(io.micronaut.core.type.Argument<?> argument, Set<Class<?>> types) {
        if (isOneOf(argument.getType(), types)) {
            return true;
        }
        Class<?> component = argument.getType().getComponentType();
        if (component != null && isOneOf(component, types)) {
            return true;
        }
        // only a wrapper the context resolves beans into holds the bean: a collection, an optional, a provider;
        // any other generic parameter, a publisher's or a class token's, is not the bean itself
        if (argument.isContainerType() || argument.isOptional() || argument.isProvider()) {
            for (io.micronaut.core.type.Argument<?> parameter : argument.getTypeParameters()) {
                if (isOneOf(parameter, types)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOneOf(Class<?> injected, Set<Class<?>> types) {
        for (Class<?> type : types) {
            if (injected.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean bindsThroughConstructor(BeanDefinition<?> definition) {
        if (definition.getBeanType().isRecord()) {
            return true;
        }
        return definition.getConstructor().getAnnotationMetadata().hasAnnotation(ConfigurationInject.class);
    }

    /**
     * Whether a key under the prefix was removed: the value a field or setter holds cannot be reset to
     * its default in place, only by a new instance.
     */
    private static boolean lostKeys(ConfigurationChange change, String prefix) {
        if (change.all()) {
            return false;
        }
        for (String key : change.changed()) {
            // a key with no current value went away; the change lists it with a null current value
            if (change.previous().containsKey(key) && change.current().get(key) == null && ConfigurationChange.ofKeys(Set.of(key)).touches(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The prefix a configuration bean is bound under: the declared one, with the name of an
     * {@code @EachProperty} entry in place of its wildcard.
     */
    @Nullable
    private static String prefixOf(BeanRegistration<?> registration) {
        BeanDefinition<?> definition = registration.getBeanDefinition();
        Optional<String> declared = definition.stringValue(ConfigurationReader.class, "prefix");
        if (declared.isEmpty()) {
            return null;
        }
        String prefix = declared.get();
        // the entry of an @EachProperty bean is its declared qualifier, whatever the bean implements
        String name = definition.getDeclaredQualifier() instanceof io.micronaut.core.naming.Named named ? named.getName() : null;
        if (name == null && registration.getBean() instanceof io.micronaut.core.naming.Named named) {
            name = named.getName();
        }
        if (prefix.endsWith(".*")) {
            prefix = prefix.substring(0, prefix.length() - 2);
            return name != null ? prefix + "." + name : prefix;
        }
        if (prefix.endsWith("[*]")) {
            prefix = prefix.substring(0, prefix.length() - 3);
            return name != null ? prefix + "[" + name + "]" : prefix;
        }
        return prefix;
    }
}
