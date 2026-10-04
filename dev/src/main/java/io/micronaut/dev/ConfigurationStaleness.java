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
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.context.RequiresCondition;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.TrueCondition;
import io.micronaut.context.conditions.MatchesCustomCondition;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * What decides, before a refresh, whether each bean and each bean configuration of the running generation is
     * present: their {@code @Requires}, and what each custom condition among them answers now, so that
     * {@link #afterRefresh} can tell whether the refresh flipped one.
     *
     * @param current The running context
     * @return The requirements, empty without a running context
     */
    static Requirements beforeRefresh(@Nullable ApplicationContext current) {
        if (current == null || !current.isRunning()) {
            return Requirements.NONE;
        }
        List<Conditional> components = conditionals(current);
        return new Requirements(components, customOutcomes(current, components));
    }

    /**
     * What a refresh cannot update: a singleton that received a changed property through {@code @Value}
     * or {@code @Property} rather than a configuration bean, and a bean or bean configuration whose presence the
     * change may have flipped, since a refresh neither adds a bean nor removes one. Any of them makes the batch
     * restart.
     *
     * <p>A requirement that reads the configuration flips with it: {@code property}, whatever it compares the
     * value with ({@code value}, {@code notEquals}, {@code pattern}, {@code defaultValue}), and
     * {@code missingProperty}, when the change touches the key or a key under it; one with an evaluated expression,
     * which may read any key; and a custom {@code condition}, which is evaluated again and compared with what it
     * answered before the refresh. The others cannot flip with a configuration edit: {@code env} and
     * {@code notEnv} read the environment names fixed at startup, {@code configuration} only asks whether the
     * bean configuration exists, whose own requirements are checked here like a bean's, and {@code beans} and
     * {@code missingBeans} follow the requirements of those beans, checked here too.</p>
     *
     * @param current The running context, refreshed
     * @param change  The keys the refresh changed
     * @param before  The requirements read before the refresh
     * @return Why a restart is needed, or null when the refresh covered the change
     */
    @Nullable
    static String afterRefresh(@Nullable ApplicationContext current, ConfigurationChange change, Requirements before) {
        if (current == null || change.all()) {
            return current == null ? null : "every property may have changed";
        }
        // the singletons, and through the graph what they hold: a prototype a singleton received and keeps
        // is as stale as the singleton would be
        Set<BeanDefinition<?>> definitions = new LinkedHashSet<>();
        Optional<io.micronaut.context.BeanDependencyGraph> graph = current instanceof ConfigurableBeanContext configurable ? configurable.findDependencyGraph() : Optional.empty();
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
        for (Conditional component : before.components()) {
            for (AnnotationValue<Requires> requires : component.requirements()) {
                String stale = staleRequirement(requires, change);
                if (stale != null) {
                    return component.name() + " " + stale;
                }
            }
        }
        List<Outcome> after = customOutcomes(current, before.components());
        for (int i = 0; i < after.size() && i < before.outcomes().size(); i++) {
            Outcome outcome = after.get(i);
            if (!Objects.equals(outcome.result(), before.outcomes().get(i).result())) {
                return outcome.component() + " has a custom condition " + outcome.condition() + " that the change flipped";
            }
        }
        return null;
    }

    /**
     * Whether a requirement reads a changed key, so that the bean it guards may have appeared or gone.
     *
     * @return What the requirement reads, or null when the change cannot flip it
     */
    @Nullable
    private static String staleRequirement(AnnotationValue<Requires> requires, ConfigurationChange change) {
        if (requires.hasEvaluatedExpressions()) {
            return "has a requirement with an evaluated expression, which may read a changed property";
        }
        // a property is present when the key or one under it is, as MatchesPropertyCondition and
        // MatchesMissingPropertyCondition ask, and touches answers just that
        String property = requires.stringValue(RequiresCondition.MEMBER_PROPERTY).orElse(null);
        if (property != null && !property.isEmpty() && change.touches(property)) {
            return "requires a changed property";
        }
        String missing = requires.stringValue(RequiresCondition.MEMBER_MISSING_PROPERTY).orElse(null);
        if (missing != null && !missing.isEmpty() && change.touches(missing)) {
            return "requires a changed property to be missing";
        }
        return null;
    }

    /**
     * Every bean reference the generation's class loader holds, the disabled ones too, since a running context
     * forgets a reference it found disabled and an edit may enable it, with those registered at runtime, and every
     * bean configuration, whose package-level requirements enable or disable the beans within it: each one that has
     * a requirement.
     */
    private static List<Conditional> conditionals(ApplicationContext current) {
        Map<String, Conditional> byName = new LinkedHashMap<>();
        ClassLoader classLoader = current.getClassLoader();
        List<BeanDefinitionReference<?>> references = new ArrayList<>();
        try {
            references.addAll(new DefaultBeanDefinitionsProvider().provide(classLoader));
        } catch (RuntimeException | LinkageError e) {
            // the references the context holds still count
        }
        references.addAll(current.getBeanDefinitionReferences());
        for (BeanDefinitionReference<?> reference : references) {
            add(byName, reference.getBeanDefinitionName(), reference);
        }
        try {
            for (BeanConfiguration configuration : MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(classLoader, BeanConfiguration.class, null)) {
                add(byName, "the bean configuration " + configuration.getName(), configuration);
            }
        } catch (RuntimeException | LinkageError e) {
            // no bean configuration to read
        }
        return List.copyOf(byName.values());
    }

    private static void add(Map<String, Conditional> byName, String name, AnnotationMetadataProvider component) {
        if (byName.containsKey(name)) {
            return;
        }
        List<AnnotationValue<Requires>> requirements;
        try {
            // with those of the stereotypes and the repeated @Requires, as RequiresCondition reads them
            requirements = component.getAnnotationMetadata().getAnnotationValuesByType(Requires.class);
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        if (!requirements.isEmpty()) {
            byName.put(name, new Conditional(name, component, requirements));
        }
    }

    /**
     * What each custom condition answers against the context's configuration now. A condition that throws
     * answers with the exception's type, so that one that starts or stops throwing counts as flipped.
     */
    private static List<Outcome> customOutcomes(ApplicationContext current, List<Conditional> components) {
        List<Outcome> outcomes = new ArrayList<>();
        for (Conditional component : components) {
            for (AnnotationValue<Requires> requires : component.requirements()) {
                AnnotationClassValue<?> condition = requires.annotationClassValue(RequiresCondition.MEMBER_CONDITION).orElse(null);
                if (condition == null || condition.getName().equals(TrueCondition.class.getName())) {
                    continue;
                }
                Object result;
                try {
                    result = new MatchesCustomCondition(condition).matches(new ProbeConditionContext(current, component.component()));
                } catch (RuntimeException | LinkageError e) {
                    result = e.getClass().getName();
                }
                outcomes.add(new Outcome(component.name(), condition.getName(), result));
            }
        }
        return outcomes;
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

    /**
     * The requirements of the running generation before a refresh.
     *
     * @param components Each bean reference and bean configuration with a requirement
     * @param outcomes   What each custom condition among them answered, in order
     */
    record Requirements(List<Conditional> components, List<Outcome> outcomes) {
        static final Requirements NONE = new Requirements(List.of(), List.of());
    }

    /**
     * A bean reference or a bean configuration with its requirements.
     *
     * @param name         How a restart names it
     * @param component    The reference or the configuration
     * @param requirements Its {@code @Requires}
     */
    record Conditional(String name, AnnotationMetadataProvider component, List<AnnotationValue<Requires>> requirements) {
    }

    /**
     * What a custom condition answered.
     *
     * @param component The bean or configuration it guards
     * @param condition The condition's class
     * @param result    True or false, or the type of what it threw
     */
    record Outcome(String component, String condition, Object result) {
    }
}
