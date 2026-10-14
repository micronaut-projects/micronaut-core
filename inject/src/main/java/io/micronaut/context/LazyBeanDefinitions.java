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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One read of the bean definitions with lazy references: the references it gave, the switches it read, and what it
 * counted for the line a context logs once it has started.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
final class LazyBeanDefinitions {

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
    final AtomicBoolean relocationReported = new AtomicBoolean();
    private final Set<String> ignoredWriters;
    private final Set<Integer> ignoredRevisions;
    private final Map<String, String> repeatableContainers = new ConcurrentHashMap<>();
    private final Map<String, Map<CharSequence, Object>> qualifierDefaults = new ConcurrentHashMap<>();

    private LazyBeanDefinitions(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.verify = Boolean.getBoolean(DefaultBeanDefinitionsProvider.LAZY_VERIFY_PROPERTY);
        this.report = System.getProperty(DefaultBeanDefinitionsProvider.LAZY_VERIFY_REPORT_PROPERTY);
        Set<String> writers = new HashSet<>();
        Set<Integer> revisions = new HashSet<>();
        String ignored = System.getProperty(DefaultBeanDefinitionsProvider.LAZY_IGNORE_WRITERS_PROPERTY);
        if (ignored != null) {
            for (String entry : ignored.split(",")) {
                String trimmed = entry.trim();
                if (trimmed.length() > 1 && trimmed.charAt(0) == 'r' && Character.isDigit(trimmed.charAt(1))) {
                    try {
                        revisions.add(Integer.parseInt(trimmed.substring(1)));
                        continue;
                    } catch (NumberFormatException e) {
                        // not a revision: a writer
                    }
                }
                if (!trimmed.isEmpty()) {
                    writers.add(trimmed);
                }
            }
        }
        this.ignoredWriters = Set.copyOf(writers);
        this.ignoredRevisions = Set.copyOf(revisions);
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
        // what each definition registers once it is loaded, and what the metadata of every bean reads: the Class
        // overloads look a repeatable annotation up by its container, and a qualifier compared by its values fills
        // the members a bean leaves at their defaults
        if (!definitions.repeatableContainers.isEmpty()) {
            DefaultAnnotationMetadata.registerRepeatableAnnotations(definitions.repeatableContainers);
        }
        definitions.qualifierDefaults.forEach((annotation, defaults) -> {
            // a definition loaded already registered them, as it would again
            if (AnnotationMetadataSupport.getDefaultValuesOrNull(annotation) == null) {
                DefaultAnnotationMetadata.registerAnnotationDefaults(annotation, defaults);
            }
        });
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
            for (AnnotationValue<?> defaults : data.qualifierDefaults) {
                qualifierDefaults.putIfAbsent(defaults.getAnnotationName(), defaults.getValues());
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
            || ignoredRevisions.contains(data.revision)
            || (!data.writer.isEmpty() && ignoredWriters.contains(data.writer));
    }

    /**
     * What a context logs once it has started.
     *
     * @param references The references the context holds
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
        int read = described.get() + undescribed.get() + missing.get();
        if (read == 0 && references.size() > lazy) {
            // a static service loader answered, without reading an entry
            return "Lazy bean definitions: off, a static service loader provides the " + references.size() + " references";
        }
        return "Lazy bean definitions: " + instantiated + " of " + lazy + " held lazy references loaded; "
            + described.get() + " described, " + undescribed.get() + " without a usable descriptor, " + missing.get() + " without an entry";
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
