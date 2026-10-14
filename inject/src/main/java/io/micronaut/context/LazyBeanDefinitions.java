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

import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * One read of the bean definitions with lazy references: the references it gave, the switches it read, and what it
 * counted for the line a context logs once it has started.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
final class LazyBeanDefinitions {

    /**
     * The system property that switches lazy bean definition references on when it is {@code true}.
     */
    static final String PROPERTY = "micronaut.bean-definitions.lazy";

    /**
     * The system property that adds the verification of every descriptor against its definition when it is
     * {@code true}.
     */
    static final String VERIFY = PROPERTY + ".verify";

    /**
     * The file the verification appends what it finds to.
     */
    static final String REPORT = VERIFY + ".report";

    /**
     * The system property that turns the warm-up off when it is {@code false}.
     */
    static final String WARM_UP = PROPERTY + ".warm-up";

    /**
     * The system property that lists the writers ({@code 5.3.0}) and revisions ({@code r1}) whose descriptors are
     * not used, comma separated.
     */
    static final String IGNORE_WRITERS = PROPERTY + ".ignore-writers";

    /**
     * The revisions of the descriptor format found to describe definitions wrongly once released. Their descriptors are
     * not used, and those definitions are loaded as without descriptors.
     */
    private static final Set<Integer> DENIED_REVISIONS = Set.of();

    final ClassLoader classLoader;
    final boolean verify;
    @Nullable
    final String report;
    final AtomicInteger described = new AtomicInteger();
    final AtomicInteger undescribed = new AtomicInteger();
    final AtomicInteger missing = new AtomicInteger();
    final AtomicInteger loaded = new AtomicInteger();
    final AtomicBoolean relocationReported = new AtomicBoolean();
    private final Set<String> ignoredWriters;
    private final Map<String, String> repeatableContainers = new ConcurrentHashMap<>();

    private LazyBeanDefinitions(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.verify = Boolean.getBoolean(VERIFY);
        this.report = System.getProperty(REPORT);
        String ignored = System.getProperty(IGNORE_WRITERS);
        this.ignoredWriters = ignored == null ? Set.of() : Arrays.stream(ignored.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether a context reads its bean definitions lazily.
     *
     * @return Whether the switch is on, with or without the verification
     */
    static boolean isEnabled() {
        return Boolean.getBoolean(PROPERTY) || Boolean.getBoolean(VERIFY);
    }

    /**
     * Reads the bean definitions of a class loader, with a lazy reference for each entry whose descriptor this
     * runtime uses, and the reference the entry names for each other one.
     *
     * @param classLoader The class loader
     * @return The references, in the order of their entries
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static List<BeanDefinitionReference<?>> provide(ClassLoader classLoader) {
        LazyBeanDefinitions definitions = new LazyBeanDefinitions(classLoader);
        List<BeanDefinitionReference> found = MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(
            classLoader,
            BeanDefinitionReference.class,
            BeanDefinitionReference::isPresent,
            definitions::reference
        );
        if (!definitions.repeatableContainers.isEmpty()) {
            // what each definition registers once it is loaded, which the Class overloads of every metadata read
            DefaultAnnotationMetadata.registerRepeatableAnnotations(definitions.repeatableContainers);
        }
        References references = new References(found.size(), definitions);
        references.addAll((Collection) found);
        return references;
    }

    /**
     * The read that gave a list of references.
     *
     * @param references The references
     * @return The read, or {@code null} when the list was not given by {@link #provide(ClassLoader)}
     */
    @Nullable
    static LazyBeanDefinitions of(List<?> references) {
        return references instanceof References lazy ? lazy.definitions : null;
    }

    @Nullable
    private BeanDefinitionReference<?> reference(ClassLoader loader, String name) {
        Object found = BeanDefinitionDescriptorReader.find(loader, name);
        if (found instanceof BeanDefinitionDescriptorData data && !isDenied(data)) {
            described.incrementAndGet();
            if (!data.repeatableContainers.isEmpty()) {
                repeatableContainers.putAll(data.repeatableContainers);
            }
            return new LazyBeanDefinitionReference<>(data, loader, this);
        }
        if (found == BeanDefinitionDescriptorReader.MISSING) {
            missing.incrementAndGet();
        } else {
            undescribed.incrementAndGet();
        }
        return null;
    }

    private boolean isDenied(BeanDefinitionDescriptorData data) {
        return DENIED_REVISIONS.contains(data.revision)
            || ignoredWriters.contains("r" + data.revision)
            || (!data.writer.isEmpty() && ignoredWriters.contains(data.writer));
    }

    /**
     * What a context logs once it has started.
     *
     * @param references The references of the context
     * @return The line
     */
    String summary(Collection<? extends BeanDefinitionReference<?>> references) {
        int lazy = 0;
        int instantiated = 0;
        for (BeanDefinitionReference<?> reference : references) {
            if (reference instanceof LazyBeanDefinitionReference<?> lazyReference) {
                lazy++;
                if (lazyReference.isInstantiated()) {
                    instantiated++;
                }
            }
        }
        return "Lazy bean definitions: loaded " + instantiated + " of " + lazy + " described (" + (undescribed.get() + missing.get())
            + " eager: " + undescribed.get() + " without a usable descriptor, " + missing.get() + " without an entry)";
    }

    /**
     * The lazy references of a list.
     *
     * @param references The references
     * @return The lazy ones
     */
    static List<LazyBeanDefinitionReference<?>> lazy(Collection<? extends BeanDefinitionReference<?>> references) {
        List<LazyBeanDefinitionReference<?>> lazy = new ArrayList<>(references.size());
        for (BeanDefinitionReference<?> reference : references) {
            if (reference instanceof LazyBeanDefinitionReference<?> lazyReference) {
                lazy.add(lazyReference);
            }
        }
        return lazy;
    }

    /**
     * The references a read gave, which carry the read.
     */
    @SuppressWarnings("java:S2160") // compared as lists
    private static final class References extends ArrayList<BeanDefinitionReference<?>> {

        private final transient LazyBeanDefinitions definitions;

        References(int size, LazyBeanDefinitions definitions) {
            super(size);
            this.definitions = definitions;
        }
    }
}
