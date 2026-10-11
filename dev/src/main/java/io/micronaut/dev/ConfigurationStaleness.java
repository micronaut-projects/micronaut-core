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
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.TrueCondition;
import io.micronaut.context.conditions.MatchesConditionUtils;
import io.micronaut.context.conditions.MatchesCustomCondition;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
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
        List<BeanDefinitionReference<?>> references = references(current);
        List<Conditional> components = conditionals(current, references);
        return new Requirements(references, components, customOutcomes(current, components));
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
     * which may read any key; {@code bean} (with {@code beanProperty}), {@code beans} and {@code missingBeans} of a
     * type bound to a touched prefix, a configuration bean or an {@code @EachBean} of one, whose entries or values
     * the change may have added, removed or changed; and a custom {@code condition}, which is evaluated again and
     * compared with what it answered before the refresh, for a bean the context has decided already. The others cannot flip with a configuration edit:
     * {@code env} and {@code notEnv} read the environment names fixed at startup, {@code configuration} only asks
     * whether the bean configuration exists, whose own requirements are checked here like a bean's, and the beans
     * that {@code beans} and {@code missingBeans} name have their own requirements checked here too.</p>
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
                String stale = staleRequirement(before.references(), requires, change);
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
    private static String staleRequirement(List<BeanDefinitionReference<?>> references, AnnotationValue<Requires> requires, ConfigurationChange change) {
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
        // bean and beanProperty, beans, missingBeans: whether a bean of the type exists, or what its property holds, follows
        // the configuration when the type is bound to it, directly or as an @EachBean of what is
        for (String member : List.of(RequiresCondition.MEMBER_BEAN, RequiresCondition.MEMBER_BEANS, RequiresCondition.MEMBER_MISSING_BEANS)) {
            for (AnnotationClassValue<?> type : requires.annotationClassValues(member)) {
                Class<?> beanType = type.getType().orElse(null);
                if (beanType != null && boundToChange(references, beanType, change, new HashSet<>())) {
                    return "requires " + (member.equals(RequiresCondition.MEMBER_MISSING_BEANS) ? "the absence of " : "") + "a bean of " + beanType.getName() + ", bound to a changed property";
                }
            }
        }
        return null;
    }

    /**
     * Whether a bean of the type is bound to a changed key: a configuration bean under a touched prefix, whose entries
     * or properties the change may have added, removed or changed, or an {@code @EachBean} of one.
     */
    private static boolean boundToChange(List<BeanDefinitionReference<?>> references, Class<?> type, ConfigurationChange change, Set<Class<?>> visited) {
        if (!visited.add(type)) {
            // an @EachBean chain that comes back to a type already followed
            return false;
        }
        // the references rather than the definitions: an @EachProperty with no entry yet has no definition
        for (BeanDefinitionReference<?> reference : references) {
            AnnotationMetadata metadata;
            try {
                if (!type.isAssignableFrom(reference.getBeanType())) {
                    continue;
                }
                metadata = reference.getAnnotationMetadata();
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            String prefix = metadata.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse(null);
            if (prefix != null) {
                // the prefix of an @EachProperty up to its first wildcard holds every entry, of a nested one too
                int wildcard = prefix.indexOf('*');
                if (wildcard >= 0) {
                    prefix = prefix.substring(0, wildcard);
                    while (prefix.endsWith(".") || prefix.endsWith("[")) {
                        prefix = prefix.substring(0, prefix.length() - 1);
                    }
                }
                if (prefix.isEmpty() || change.touches(prefix)) {
                    return true;
                }
            }
            Class<?> each = metadata.classValue(EachBean.class).orElse(null);
            if (each != null && boundToChange(references, each, change, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every bean reference the generation's class loader holds, the disabled ones too, since a running context
     * forgets a reference it found disabled and an edit may enable it, with those registered at runtime.
     */
    private static List<BeanDefinitionReference<?>> references(ApplicationContext current) {
        List<BeanDefinitionReference<?>> references = new ArrayList<>();
        try {
            references.addAll(new DefaultBeanDefinitionsProvider().provide(current.getClassLoader()));
        } catch (RuntimeException | LinkageError e) {
            // the references the context holds still count
        }
        references.addAll(current.getBeanDefinitionReferences());
        return references;
    }

    /**
     * Each bean reference with a requirement, and each bean configuration with one, whose package-level requirements
     * enable or disable the beans within it.
     */
    private static List<Conditional> conditionals(ApplicationContext current, List<BeanDefinitionReference<?>> references) {
        Map<String, Conditional> byName = new LinkedHashMap<>();
        ClassLoader classLoader = current.getClassLoader();
        Set<String> resolved = resolved(current, references);
        for (BeanDefinitionReference<?> reference : references) {
            String name = reference.getBeanDefinitionName();
            add(current, byName, name, reference, resolved.contains(name));
        }
        try {
            for (BeanConfiguration configuration : MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(classLoader, BeanConfiguration.class, null)) {
                add(current, byName, "the bean configuration " + configuration.getName(), configuration, true);
            }
        } catch (RuntimeException | LinkageError e) {
            // no bean configuration to read
        }
        return List.copyOf(byName.values());
    }

    /**
     * The beans whose conditions the running context has decided: those it holds an instance of, with what they
     * depend on, and those it forgot after finding them disabled. Any other reference is evaluated, lazily, against
     * the refreshed configuration when it is first asked for, so its custom conditions are not run here: they may
     * look beans up or do other work the application never asked for.
     */
    private static Set<String> resolved(ApplicationContext current, List<BeanDefinitionReference<?>> references) {
        Set<String> held = new HashSet<>();
        for (BeanDefinitionReference<?> reference : current.getBeanDefinitionReferences()) {
            held.add(reference.getBeanDefinitionName());
        }
        // a bean whose definition failed its conditions keeps its reference, and is tracked by type instead
        Set<String> disabledTypes = new HashSet<>();
        for (io.micronaut.context.DisabledBean<?> disabled : current.getDisabledBeans()) {
            disabledTypes.add(disabled.getBeanType().getName());
        }
        Set<String> resolved = new HashSet<>();
        for (BeanDefinitionReference<?> reference : references) {
            try {
                if (!held.contains(reference.getBeanDefinitionName()) || disabledTypes.contains(reference.getBeanType().getName())) {
                    resolved.add(reference.getBeanDefinitionName());
                }
            } catch (RuntimeException | LinkageError e) {
                // a reference whose type cannot load is never resolved
            }
        }
        Optional<io.micronaut.context.BeanDependencyGraph> graph = current instanceof ConfigurableBeanContext configurable ? configurable.findDependencyGraph() : Optional.empty();
        for (BeanRegistration<?> registration : current.getActiveBeanRegistrations(io.micronaut.inject.qualifiers.Qualifiers.any())) {
            BeanDefinition<?> definition = registration.getBeanDefinition();
            resolved.add(definition.getClass().getName());
            graph.ifPresent(g -> g.transitiveDependenciesOf(definition).forEach(d -> resolved.add(d.getClass().getName())));
        }
        return resolved;
    }

    private static void add(ApplicationContext current, Map<String, Conditional> byName, String name, AnnotationMetadataProvider component,
                            boolean probed) {
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
        if (requirements.isEmpty()) {
            return;
        }
        AnnotationMetadataProvider evaluated = component;
        if (probed && component instanceof BeanDefinitionReference<?> reference
            && requirements.stream().anyMatch(requires -> requires.contains(RequiresCondition.MEMBER_CONDITION))) {
            // a custom condition is evaluated against the bean definition, as core does once the reference is loaded,
            // and loaded through the running context its metadata resolves placeholders against the environment
            try {
                evaluated = reference.load(current);
            } catch (RuntimeException | LinkageError e) {
                // the reference answers its metadata still
            }
        }
        byName.put(name, new Conditional(name, evaluated, requirements, probed));
    }

    /**
     * What each custom condition answers against the context's configuration now. A condition that throws
     * answers with the exception's type, so that one that starts or stops throwing counts as flipped.
     */
    private static List<Outcome> customOutcomes(ApplicationContext current, List<Conditional> components) {
        List<Outcome> outcomes = new ArrayList<>();
        for (Conditional component : components) {
            if (!component.probed()) {
                continue;
            }
            // a bean another requirement disables, such as an unmet property, stays disabled whatever its custom
            // conditions answer, and the context short-circuits them, so they are not run here either: that
            // requirement flips only with a key the static check reads, which restarts first
            Boolean unmet = null;
            for (AnnotationValue<Requires> requires : component.requirements()) {
                AnnotationClassValue<?> condition = requires.annotationClassValue(RequiresCondition.MEMBER_CONDITION).orElse(null);
                if (condition == null || condition.getName().equals(TrueCondition.class.getName())) {
                    continue;
                }
                if (unmet == null) {
                    unmet = !preConditionsHold(current, component);
                }
                Object result;
                try {
                    result = unmet ? "unmet" : new MatchesCustomCondition(condition).matches(new ProbeConditionContext(current, component.component()));
                } catch (RuntimeException | LinkageError e) {
                    result = e.getClass().getName();
                }
                outcomes.add(new Outcome(component.name(), condition.getName(), result));
            }
        }
        return outcomes;
    }

    /**
     * Whether every requirement core decides before loading a bean holds: the properties, the environment, the
     * classes and the like, none of which runs application code.
     */
    private static boolean preConditionsHold(ApplicationContext current, Conditional component) {
        ProbeConditionContext context = new ProbeConditionContext(current, component.component());
        for (AnnotationValue<Requires> requires : component.requirements()) {
            if (requires.hasEvaluatedExpressions()) {
                continue;
            }
            List<Condition> pre = new ArrayList<>();
            try {
                MatchesConditionUtils.createConditions(requires, pre, new ArrayList<>());
                for (Condition condition : pre) {
                    if (!condition.matches(context)) {
                        return false;
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                // undecided: the custom conditions are run
            }
        }
        return true;
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
    private static boolean mentionsChangedProperty(AnnotationMetadata metadata, ConfigurationChange change) {
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
     * @param references Every bean reference, the disabled ones too
     * @param components Each bean reference and bean configuration with a requirement
     * @param outcomes   What each custom condition among them answered, in order
     */
    record Requirements(List<BeanDefinitionReference<?>> references, List<Conditional> components, List<Outcome> outcomes) {
        static final Requirements NONE = new Requirements(List.of(), List.of(), List.of());
    }

    /**
     * A bean reference or a bean configuration with its requirements.
     *
     * @param name         How a restart names it
     * @param component    The reference or the configuration
     * @param requirements Its {@code @Requires}
     * @param probed       Whether its custom conditions are evaluated: the running context decided it already
     */
    record Conditional(String name, AnnotationMetadataProvider component, List<AnnotationValue<Requires>> requirements, boolean probed) {
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
