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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Checks, with {@code micronaut.bean-definitions.lazy.verify=true}, that what a {@link LazyBeanDefinitionReference}
 * answered from its descriptor is what the loaded definition answers.
 *
 * <p>When a definition is loaded, its descriptor is compared with it, field by field, and every answer the reference
 * gave from the descriptor before is asked of the definition again. Qualifiers and annotation values are compared by
 * their content. A difference fails the lookup that loaded the definition with an {@link IllegalStateException},
 * and is also logged, appended to the file {@code micronaut.bean-definitions.lazy.verify.report} names, and printed
 * when the JVM exits, so that a test that catches the exception does not hide it. When a context stops, every
 * definition it has not loaded is loaded and compared, without failing anything.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
final class LazyBeanDefinitionVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(LazyBeanDefinitionVerifier.class);
    private static final Queue<String> FOUND = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean HOOKED = new AtomicBoolean();

    private LazyBeanDefinitionVerifier() {
    }

    /**
     * Keeps an answer, once for each question.
     *
     * @param answers  The answers of a reference
     * @param what     The question
     * @param answered The answer
     * @param replay   Asks the loaded reference the same question
     */
    static void record(Map<String, Answer> answers, String what, @Nullable Object answered, Function<BeanDefinitionReference<?>, @Nullable Object> replay) {
        answers.putIfAbsent(what, new Answer(answered, replay));
    }

    /**
     * Compares a descriptor with the definition it describes, which was just loaded.
     *
     * @param lazy   The lazy reference
     * @param loaded The reference its entry names, loaded
     * @param fail   Whether a difference fails the caller
     */
    static void verify(LazyBeanDefinitionReference<?> lazy, BeanDefinitionReference<?> loaded, boolean fail) {
        if (loaded instanceof AbstractInitializableBeanDefinitionAndReference<?> definition && definition.isFailedInitializationForVerification()) {
            // a definition whose static initializer failed answers with empty metadata, and fails when it is loaded
            return;
        }
        List<String> differences = new ArrayList<>();
        try {
            differences(lazy, loaded, differences);
            Map<String, Answer> answers = lazy.answers;
            if (answers != null) {
                for (Map.Entry<String, Answer> answer : answers.entrySet()) {
                    Object answered = normalize(answer.getValue().answered());
                    Object now = normalize(answer.getValue().replay().apply(loaded));
                    if (!Objects.equals(answered, now)) {
                        differences.add(answer.getKey() + ": the reference answered " + answered + ", the definition answers " + now);
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            differences.add("comparing failed: " + e);
        }
        if (!differences.isEmpty()) {
            String message = "The descriptor of the bean definition [" + lazy.getBeanDefinitionName() + "] does not agree with the definition:\n  "
                + String.join("\n  ", differences);
            report(lazy.definitions, message);
            if (fail) {
                throw new IllegalStateException(message);
            }
        }
    }

    /**
     * Loads every lazy reference that was not loaded and compares its descriptor with its definition, without
     * failing: what a context does when it stops, so that a descriptor no lookup needed is compared too. A
     * definition that cannot be loaded is no difference.
     *
     * @param references The references of a context
     */
    @SuppressWarnings("java:S1181") // a definition that fails to load is not what this checks
    static void verifyUnloaded(Collection<? extends BeanDefinitionReference<?>> references) {
        for (LazyBeanDefinitionReference<?> lazy : LazyBeanDefinitions.lazy(references)) {
            if (lazy.isInstantiated()) {
                continue;
            }
            try {
                lazy.instantiateForVerification();
            } catch (Throwable e) {
                // kept by the reference: not a difference of the descriptor
            }
        }
    }

    /**
     * What a lazy reference loaded at the stop of a context is compared for: the descriptor only.
     */
    private static void differences(LazyBeanDefinitionReference<?> lazy, BeanDefinitionReference<?> loaded, List<String> differences) {
        BeanDefinitionDescriptorData data = lazy.data;
        check(differences, "flags", data.flags & ~BeanDefinitionDescriptorData.FLAG_DECLARED_INDEXES, flags(loaded));
        check(differences, "definitionName", data.definitionName, loaded.getBeanDefinitionName());
        check(differences, "beanType", data.beanTypeName, loaded.getBeanType().getName());
        check(differences, "order", data.order, loaded.getOrder());
        List<String> exposed = sorted(data.exposedTypeNames);
        if (loads(lazy, exposed)) {
            check(differences, "exposedTypes", exposed, sorted(loaded.getExposedTypes().stream().map(Class::getName).toArray(String[]::new)));
        }
        List<String> indexes = Arrays.asList(data.indexNames);
        List<String> answeredIndexes = Arrays.stream(loaded.getIndexes()).map(Class::getName).toList();
        if (data.is(BeanDefinitionDescriptorData.FLAG_DECLARED_INDEXES)) {
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
            int answered = (metadata.hasDeclaredAnnotation(name) ? BeanDefinitionDescriptorData.MEMBERSHIP_DECLARED_ANNOTATION : 0)
                | (metadata.hasAnnotation(name) ? BeanDefinitionDescriptorData.MEMBERSHIP_ANNOTATION : 0)
                | (metadata.hasDeclaredStereotype(name) ? BeanDefinitionDescriptorData.MEMBERSHIP_DECLARED_STEREOTYPE : 0)
                | (metadata.hasStereotype(name) ? BeanDefinitionDescriptorData.MEMBERSHIP_STEREOTYPE : 0);
            check(differences, "annotation " + name, data.membership(name), answered);
        }
        for (Map.Entry<String, String> repeatable : data.repeatableContainers.entrySet()) {
            check(differences, "container of " + repeatable.getKey(), repeatable.getValue(), AnnotationMetadataSupport.getRepeatableAnnotation(repeatable.getKey()));
        }

        AnnotationMetadata declared = loaded.getTargetAnnotationMetadata();
        if (declared instanceof AnnotationMetadataHierarchy hierarchy) {
            declared = hierarchy.getDeclaredMetadata();
        }
        check(differences, "qualifiers", Arrays.stream(data.qualifiers).map(LazyBeanDefinitionVerifier::shape).toList(),
            AnnotationUtil.findQualifierAnnotations(declared).stream().map(LazyBeanDefinitionVerifier::shape).toList());
        check(differences, "nonBindingMembers", Arrays.asList(data.nonBindingMembers), Arrays.asList(AnnotationUtil.resolveNonBindingMembers(declared)));
        check(differences, "declaredQualifier", normalize(lazy.getDeclaredQualifier()), normalize(loaded.getDeclaredQualifier()));

        if (loaded instanceof AbstractInitializableBeanDefinitionAndReference<?> definition) {
            Condition[] preLoadConditions = definition.preLoadConditionsForVerification();
            check(differences, "preLoadConditions", Arrays.asList(lazy.resolvedPreLoadConditions()), preLoadConditions == null ? List.of() : Arrays.asList(preLoadConditions));
        }
    }

    private static int flags(BeanDefinitionReference<?> reference) {
        Condition[] postLoadConditions = reference instanceof AbstractInitializableBeanDefinitionAndReference<?> definition
            ? definition.postLoadConditionsForVerification()
            : null;
        return (reference.isContextScope() ? BeanDefinitionDescriptorData.FLAG_CONTEXT_SCOPE : 0)
            | (reference.isParallel() ? BeanDefinitionDescriptorData.FLAG_PARALLEL : 0)
            | (reference.isProxiedBean() ? BeanDefinitionDescriptorData.FLAG_PROXIED_BEAN : 0)
            | (reference.isProxyTarget() ? BeanDefinitionDescriptorData.FLAG_PROXY_TARGET : 0)
            | (reference.isSingleton() ? BeanDefinitionDescriptorData.FLAG_SINGLETON : 0)
            | (reference.isPrimary() ? BeanDefinitionDescriptorData.FLAG_PRIMARY : 0)
            | (reference.isConfigurationProperties() ? BeanDefinitionDescriptorData.FLAG_CONFIGURATION_PROPERTIES : 0)
            | (reference.isContainerType() ? BeanDefinitionDescriptorData.FLAG_CONTAINER_TYPE : 0)
            | (reference.requiresMethodProcessing() ? BeanDefinitionDescriptorData.FLAG_REQUIRES_METHOD_PROCESSING : 0)
            | (postLoadConditions != null && postLoadConditions.length > 0 ? BeanDefinitionDescriptorData.FLAG_POST_LOAD_CONDITIONS : 0);
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
     * An answer in a form that compares by content: an optional by what it holds, a qualifier by what it prints,
     * annotation values and arrays by their elements.
     */
    @Nullable
    private static Object normalize(@Nullable Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.map(LazyBeanDefinitionVerifier::normalize).orElse(null);
        }
        if (value instanceof Qualifier<?> qualifier) {
            return qualifier.getClass().getName() + ":" + qualifier;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                values.put(String.valueOf(entry.getKey()), shape(entry.getValue()));
            }
            return values;
        }
        return shape(value);
    }

    /**
     * A value of an annotation member with the type it has, so that two values are only the same if a qualifier
     * comparing them would find them the same: an array of strings is not an array of objects.
     */
    @Nullable
    private static Object shape(@Nullable Object value) {
        if (value instanceof AnnotationValue<?> annotation) {
            Map<String, Object> values = new LinkedHashMap<>();
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
