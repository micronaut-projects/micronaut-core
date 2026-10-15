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
package io.micronaut.context;

import io.micronaut.context.condition.Condition;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Checks, with {@code micronaut.bean-definitions.lazy.verify=true}, that what a {@link LazyBeanDefinitionReference}
 * answered from its descriptor is what the loaded definition answers.
 *
 * <p>When a definition is loaded, its descriptor is compared with it, field by field, and every answer the reference
 * gave from the descriptor before is asked of the definition again. An answer the descriptor gives once the definition
 * is loaded is compared at once, and a reference predicate of a lookup is asked of the definition too, which also
 * finds a bean the descriptor wrongly leaves out. Qualifiers and annotation values are compared by their content. A
 * difference fails the lookup that met it, and every later lookup that reaches the reference, with an
 * {@link IllegalStateException}, and is also logged, appended to the file
 * {@code micronaut.bean-definitions.lazy.verify.report} names, and printed when the JVM exits, so that a test that
 * catches the exception does not hide it. When a context stops, every definition it has not loaded is loaded and
 * compared, and every answer is asked again, without failing anything.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
final class LazyBeanDefinitionVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(LazyBeanDefinitionVerifier.class);
    private static final Queue<String> FOUND = new ConcurrentLinkedQueue<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean HOOKED = new AtomicBoolean();

    private LazyBeanDefinitionVerifier() {
    }

    /**
     * Keeps an answer of a reference, once for each question. When the definition is loaded already, the answer is
     * compared with what the definition answers at once, and a difference fails the caller.
     *
     * @param lazy     The reference
     * @param what     The question
     * @param answered The answer
     * @param replay   Asks the loaded reference the same question
     */
    static void record(LazyBeanDefinitionReference<?> lazy, String what, @Nullable Object answered, Function<BeanDefinitionReference<?>, @Nullable Object> replay) {
        Map<String, Answer> answers = Objects.requireNonNull(lazy.answers);
        if (answers.putIfAbsent(what, new Answer(answered, replay)) != null) {
            return;
        }
        BeanDefinitionReference<?> loaded = lazy.loadedInstance();
        if (loaded == null || isFailedInitialization(loaded)) {
            // compared when the definition is loaded
            return;
        }
        String difference = replay(what, answered, replay, loaded);
        if (difference != null) {
            String message = message(lazy, List.of(difference));
            report(lazy.definitions, message);
            lazy.differs(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Compares what a reference predicate of a lookup answered for the descriptor with what it answers for the
     * definition, which eager mode asks: a descriptor that wrongly leaves a bean out is found this way only.
     *
     * @param lazy      The reference
     * @param loaded    The definition, loaded for the comparison
     * @param described What the predicate answered for the reference
     * @param answers   What the predicate answers for the definition
     */
    static void comparePredicate(LazyBeanDefinitionReference<?> lazy, BeanDefinitionReference<?> loaded, boolean described, boolean answers) {
        if (described != answers && !isFailedInitialization(loaded)) {
            String message = message(lazy, List.of("a reference predicate of a lookup: the reference answered " + described + ", the definition answers " + answers));
            report(lazy.definitions, message);
            lazy.differs(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Compares a descriptor with the definition it describes, which was just loaded.
     *
     * @param lazy   The lazy reference
     * @param loaded The reference its entry names, loaded
     * @return The differences found, or {@code null} if there are none
     */
    static @Nullable String verify(LazyBeanDefinitionReference<?> lazy, BeanDefinitionReference<?> loaded) {
        if (isFailedInitialization(loaded)) {
            // a definition whose static initializer failed answers with empty metadata, and fails when it is loaded
            return null;
        }
        List<String> differences = new ArrayList<>();
        try {
            differences(lazy, loaded, differences);
            replayAnswers(lazy, loaded, differences);
        } catch (RuntimeException | LinkageError e) {
            differences.add("comparing failed: " + e);
        }
        if (differences.isEmpty()) {
            return null;
        }
        String message = message(lazy, differences);
        report(lazy.definitions, message);
        return message;
    }

    /**
     * Loads every lazy reference that was not loaded and compares its descriptor with its definition, then asks every
     * definition again every answer its reference gave, without failing: what a context does when it stops, so that a
     * descriptor no lookup needed is compared too, and an answer that changed with what was loaded since is found. A
     * definition that cannot be loaded is no difference.
     *
     * @param references The references a context was given
     */
    @SuppressWarnings("java:S1181") // a definition that fails to load is not what this checks
    static void verifyUnloaded(Collection<? extends BeanDefinitionReference<?>> references) {
        List<LazyBeanDefinitionReference<?>> lazyReferences = LazyBeanDefinitions.lazy(references);
        for (LazyBeanDefinitionReference<?> lazy : lazyReferences) {
            if (lazy.isInstantiated()) {
                continue;
            }
            try {
                lazy.instantiateForVerification();
            } catch (Throwable e) {
                // kept by the reference: not a difference of the descriptor
            }
        }
        for (LazyBeanDefinitionReference<?> lazy : lazyReferences) {
            BeanDefinitionReference<?> loaded = lazy.loadedInstance();
            if (loaded == null || isFailedInitialization(loaded) || lazy.data.is(BeanDefinitionDescriptorFormat.FLAG_PROPERTY_EXPRESSIONS)) {
                // a definition with property expressions answers them resolved once it is configured, as eager mode
                // does: what it answered before, as the descriptor did, was compared when it was loaded
                continue;
            }
            List<String> differences = new ArrayList<>();
            try {
                replayAnswers(lazy, loaded, differences);
            } catch (RuntimeException | LinkageError e) {
                differences.add("comparing failed: " + e);
            }
            if (!differences.isEmpty()) {
                report(lazy.definitions, message(lazy, differences));
            }
        }
    }

    private static void replayAnswers(LazyBeanDefinitionReference<?> lazy, BeanDefinitionReference<?> loaded, List<String> differences) {
        Map<String, Answer> answers = lazy.answers;
        if (answers != null) {
            for (Map.Entry<String, Answer> answer : answers.entrySet()) {
                String difference = replay(answer.getKey(), answer.getValue().answered(), answer.getValue().replay(), loaded);
                if (difference != null) {
                    differences.add(difference);
                }
            }
        }
    }

    private static @Nullable String replay(String what, @Nullable Object answered, Function<BeanDefinitionReference<?>, @Nullable Object> replay, BeanDefinitionReference<?> loaded) {
        Object described = normalize(answered);
        Object now;
        try {
            now = normalize(replay.apply(loaded));
        } catch (RuntimeException | LinkageError e) {
            now = "failed: " + e;
        }
        return Objects.equals(described, now) ? null : what + ": the reference answered " + described + ", the definition answers " + now;
    }

    private static String message(LazyBeanDefinitionReference<?> lazy, List<String> differences) {
        return "The descriptor of the bean definition [" + lazy.getBeanDefinitionName() + "] does not agree with the definition:\n  "
            + String.join("\n  ", differences);
    }

    private static boolean isFailedInitialization(BeanDefinitionReference<?> loaded) {
        return loaded instanceof AbstractInitializableBeanDefinitionAndReference<?> definition && definition.isFailedInitializationForVerification();
    }

    /**
     * What a lazy reference loaded at the stop of a context is compared for: the descriptor only.
     */
    private static void differences(LazyBeanDefinitionReference<?> lazy, BeanDefinitionReference<?> loaded, List<String> differences) {
        BeanDefinitionDescriptorData data = lazy.data;
        // the flags of the descriptor only, which the definition answers through what they select
        check(differences, "flags", data.flags & ~(BeanDefinitionDescriptorFormat.FLAG_DECLARED_INDEXES | BeanDefinitionDescriptorFormat.FLAG_PROPERTY_EXPRESSIONS), flags(loaded));
        check(differences, "definitionName", data.definitionName, loaded.getBeanDefinitionName());
        check(differences, "beanType", data.beanTypeName, loaded.getBeanType().getName());
        check(differences, "order", data.order, loaded.getOrder());
        List<String> exposed = sorted(data.exposedTypeNames);
        if (loads(lazy, exposed)) {
            check(differences, "exposedTypes", exposed, sorted(loaded.getExposedTypes().stream().map(Class::getName).toArray(String[]::new)));
        }
        List<String> indexes = Arrays.asList(data.indexNames);
        List<String> answeredIndexes = Arrays.stream(loaded.getIndexes()).map(Class::getName).toList();
        if (data.is(BeanDefinitionDescriptorFormat.FLAG_DECLARED_INDEXES)) {
            check(differences, "indexes", loads(lazy, indexes) ? indexes : List.of(), answeredIndexes);
        } else {
            check(differences, "indexes", indexes.stream().filter(name -> lazy.loadClass(name) != null).toList(), answeredIndexes);
        }

        AnnotationMetadata metadata = loaded.getAnnotationMetadata();
        Set<String> names = new TreeSet<>(Arrays.asList(data.annotationNames));
        names.addAll(metadata.getAnnotationNames());
        names.addAll(metadata.getDeclaredAnnotationNames());
        names.addAll(metadata.getStereotypeAnnotationNames());
        names.addAll(metadata.getDeclaredStereotypeAnnotationNames());
        for (String name : names) {
            int answered = (metadata.hasDeclaredAnnotation(name) ? BeanDefinitionDescriptorFormat.MEMBERSHIP_DECLARED_ANNOTATION : 0)
                | (metadata.hasAnnotation(name) ? BeanDefinitionDescriptorFormat.MEMBERSHIP_ANNOTATION : 0)
                | (metadata.hasDeclaredStereotype(name) ? BeanDefinitionDescriptorFormat.MEMBERSHIP_DECLARED_STEREOTYPE : 0)
                | (metadata.hasStereotype(name) ? BeanDefinitionDescriptorFormat.MEMBERSHIP_STEREOTYPE : 0);
            check(differences, "annotation " + name, data.membership(name), answered);
        }
        for (Map.Entry<String, String> repeatable : data.repeatableContainers.entrySet()) {
            check(differences, "container of " + repeatable.getKey(), repeatable.getValue(), AnnotationMetadataSupport.getRepeatableAnnotation(repeatable.getKey()));
            // what the metadata answers for it, which a definition with property expressions answers through a metadata
            // that knows no repeatable annotation by name
            check(differences, "findRepeatableAnnotation(" + repeatable.getKey() + ")",
                ((DescriptorAnnotationMetadata) lazy.getAnnotationMetadata()).repeatableAnswer(repeatable.getKey()), metadata.findRepeatableAnnotation(repeatable.getKey()));
        }

        AnnotationMetadata declared = loaded.getTargetAnnotationMetadata();
        if (declared instanceof AnnotationMetadataHierarchy hierarchy) {
            declared = hierarchy.getDeclaredMetadata();
        }
        check(differences, "qualifiers", Arrays.stream(data.qualifiers).map(LazyBeanDefinitionVerifier::shape).toList(),
            AnnotationUtil.findQualifierAnnotations(declared).stream().map(LazyBeanDefinitionVerifier::shape).toList());
        check(differences, "nonBindingMembers", Arrays.asList(data.nonBindingMembers), Arrays.asList(AnnotationUtil.resolveNonBindingMembers(declared)));
        // the defaults of the qualifier annotations, which the definition registered when it was loaded
        Map<String, Object> described = new TreeMap<>();
        for (AnnotationValue<?> defaults : data.qualifierDefaults) {
            described.put(defaults.getAnnotationName(), defaultsShape(defaults.getValues()));
        }
        Map<String, Object> registered = new TreeMap<>();
        for (AnnotationValue<?> qualifier : AnnotationUtil.findQualifierAnnotations(declared)) {
            String name = qualifier.getAnnotationName();
            Map<CharSequence, Object> defaults = AnnotationMetadataSupport.getDefaultValuesOrNull(name);
            if (defaults != null && !defaults.isEmpty() && !AnnotationMetadataSupport.getCoreAnnotationDefaults().containsKey(name)) {
                registered.put(name, defaultsShape(defaults));
            }
        }
        check(differences, "qualifierDefaults", described, registered);
        check(differences, "declaredQualifier", normalize(lazy.getDeclaredQualifier()), normalize(loaded.getDeclaredQualifier()));

        if (loaded instanceof AbstractInitializableBeanDefinitionAndReference<?> definition) {
            Condition[] preLoadConditions = definition.preLoadConditionsForVerification();
            check(differences, "preLoadConditions", Arrays.asList(lazy.resolvedConditions()), preLoadConditions == null ? List.of() : Arrays.asList(preLoadConditions));
        }
    }

    /**
     * Default values in a form that compares by content: a class by its name, loaded or not, as the descriptor names it
     * and the definition holds it.
     */
    private static Map<String, @Nullable Object> defaultsShape(Map<CharSequence, Object> defaults) {
        Map<String, @Nullable Object> shaped = new TreeMap<>();
        for (Map.Entry<CharSequence, Object> member : defaults.entrySet()) {
            shaped.put(member.getKey().toString(), defaultShape(member.getValue()));
        }
        return shaped;
    }

    @Nullable
    private static Object defaultShape(@Nullable Object value) {
        if (value instanceof AnnotationClassValue<?> classValue) {
            return "class " + classValue.getName();
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value != null && value.getClass().isArray()) {
            List<@Nullable Object> elements = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                elements.add(defaultShape(Array.get(value, i)));
            }
            return elements;
        }
        return value;
    }

    private static int flags(BeanDefinitionReference<?> reference) {
        Condition[] postLoadConditions = reference instanceof AbstractInitializableBeanDefinitionAndReference<?> definition
            ? definition.postLoadConditionsForVerification()
            : null;
        return (reference.isContextScope() ? BeanDefinitionDescriptorFormat.FLAG_CONTEXT_SCOPE : 0)
            | (reference.isParallel() ? BeanDefinitionDescriptorFormat.FLAG_PARALLEL : 0)
            | (reference.isProxiedBean() ? BeanDefinitionDescriptorFormat.FLAG_PROXIED_BEAN : 0)
            | (reference.isProxyTarget() ? BeanDefinitionDescriptorFormat.FLAG_PROXY_TARGET : 0)
            | (reference.isSingleton() ? BeanDefinitionDescriptorFormat.FLAG_SINGLETON : 0)
            | (reference.isPrimary() ? BeanDefinitionDescriptorFormat.FLAG_PRIMARY : 0)
            | (reference.isConfigurationProperties() ? BeanDefinitionDescriptorFormat.FLAG_CONFIGURATION_PROPERTIES : 0)
            | (reference.isContainerType() ? BeanDefinitionDescriptorFormat.FLAG_CONTAINER_TYPE : 0)
            | (reference.requiresMethodProcessing() ? BeanDefinitionDescriptorFormat.FLAG_REQUIRES_METHOD_PROCESSING : 0)
            | (postLoadConditions != null && postLoadConditions.length > 0 ? BeanDefinitionDescriptorFormat.FLAG_POST_LOAD_CONDITIONS : 0);
    }

    private static boolean loads(LazyBeanDefinitionReference<?> lazy, List<String> names) {
        for (String name : names) {
            if (lazy.loadClass(name) == null) {
                return false;
            }
        }
        return true;
    }

    private static List<String> sorted(String[] names) {
        return Arrays.stream(names).sorted().toList();
    }

    private static void check(List<String> differences, String what, @Nullable Object described, @Nullable Object answered) {
        if (!Objects.equals(described, answered)) {
            differences.add(what + ": the descriptor says " + described + ", the definition " + answered);
        }
    }

    /**
     * An answer in a form that compares by content: an optional by what it holds, a qualifier by its shape,
     * annotation values and arrays by their elements, maps by their keys in order.
     */
    @Nullable
    private static Object normalize(@Nullable Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.map(LazyBeanDefinitionVerifier::normalize).orElse(null);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                values.put(String.valueOf(entry.getKey()), shape(entry.getValue()));
            }
            return values;
        }
        return shape(value);
    }

    /**
     * A qualifier by its class and the values of its fields, of its superclasses too, each in the form that compares
     * by content: two qualifiers built from the same members in another order have the same shape. A field this
     * class cannot read makes the shape what the qualifier prints.
     */
    private static Object qualifierShape(Qualifier<?> qualifier) {
        Map<String, Object> fields = new TreeMap<>();
        for (Class<?> type = qualifier.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (!field.trySetAccessible()) {
                    return qualifier.getClass().getName() + ":" + qualifier;
                }
                try {
                    Object value = field.get(qualifier);
                    // the metadata a qualifier was built from is compared through what the qualifier took from it
                    fields.put(type.getSimpleName() + "." + field.getName(), value instanceof AnnotationMetadata ? "metadata" : shape(value));
                } catch (IllegalAccessException e) {
                    return qualifier.getClass().getName() + ":" + qualifier;
                }
            }
        }
        return List.of("qualifier", qualifier.getClass().getName(), fields);
    }

    /**
     * A value of an annotation member with the type it has, so that two values are only the same if a qualifier
     * comparing them would find them the same: an array of strings is not an array of objects. The members of an
     * annotation are compared in the order of their names.
     */
    @Nullable
    private static Object shape(@Nullable Object value) {
        if (value instanceof Qualifier<?> qualifier) {
            return qualifierShape(qualifier);
        }
        if (value instanceof Qualifier<?>[] qualifiers) {
            // the qualifiers of a composite all apply, in whatever order
            List<Object> shapes = new ArrayList<>();
            for (Qualifier<?> qualifier : qualifiers) {
                shapes.add(shape(qualifier));
            }
            shapes.sort(Comparator.comparing(String::valueOf));
            return List.of("qualifiers", shapes);
        }
        if (value instanceof Class<?> type) {
            return List.of("type", type.getName());
        }
        if (value instanceof AnnotationValue<?> annotation) {
            Map<String, Object> values = new TreeMap<>();
            for (Map.Entry<CharSequence, Object> member : annotation.getValues().entrySet()) {
                values.put(member.getKey().toString(), shape(member.getValue()));
            }
            return List.of("annotation", annotation.getAnnotationName(), values);
        }
        if (value instanceof AnnotationClassValue<?> classValue) {
            return List.of("class", classValue.getName(), classValue.isInstantiated());
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> elements = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                elements.add(shape(Array.get(value, i)));
            }
            return List.of("array", value.getClass().getComponentType().getName(), elements);
        }
        return value;
    }

    private static void report(LazyBeanDefinitions definitions, String message) {
        if (!REPORTED.add(message)) {
            // met again, by the stop of a context or by another lookup
            return;
        }
        FOUND.add(message);
        LOG.error(message);
        String report = definitions.report;
        if (report != null) {
            synchronized (FOUND) {
                try {
                    Files.writeString(Path.of(report), message + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        if (HOOKED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.err.println("Lazy bean definitions: " + FOUND.size() + " descriptor(s) disagree with their definitions:");
                FOUND.forEach(System.err::println);
            }, "lazy-bean-definitions-verify"));
        }
    }

    /**
     * An answer a reference gave from its descriptor.
     *
     * @param answered The answer
     * @param replay   Asks the loaded reference the same question
     */
    record Answer(@Nullable Object answered, Function<BeanDefinitionReference<?>, @Nullable Object> replay) {
    }
}
