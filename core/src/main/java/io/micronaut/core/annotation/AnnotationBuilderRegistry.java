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
package io.micronaut.core.annotation;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.SupplierUtil;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The registry of the {@link AnnotationBuilder}s the service loader supplies, which the annotation processor
 * generates for the types listed in {@link RegisterAnnotations} and for the types the visitors register.
 *
 * <p>A builder is loaded when it is first asked for, not when the registry is created: the service entries carry
 * the name of the annotation type, so the registry loads the classes of the annotations that are looked up only.
 * Look a builder up once with {@link AnnotationBuilder#find(Class)} and reuse it to build many annotations without
 * a search; the registry is the implementation of that method.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AnnotationBuilderRegistry {

    /**
     * The infix between the name of the annotated type and the name of the annotation, and the suffix of the
     * name, in the names of the generated builders.
     */
    @Internal
    public static final String BUILDER_SUFFIX = "$AnnotationBuilder";

    private static final AnnotationBuilderRegistry SHARED = new AnnotationBuilderRegistry(AnnotationBuilderRegistry.class.getClassLoader());

    /**
     * The builder of each annotation type that its own class loader supplies and Micronaut's does not. A builder
     * found through the class loader of the type is visible to that class loader, so keeping it on the type pins
     * nothing the type does not pin already, and a type loaded again gets an entry of its own.
     */
    private static final ClassValue<Optional<AnnotationBuilder<?>>> OF_TYPE_LOADER = new ClassValue<>() {
        @Override
        protected Optional<AnnotationBuilder<?>> computeValue(Class<?> type) {
            ClassLoader typeLoader = type.getClassLoader();
            if (typeLoader == null || isSameOrAncestor(typeLoader, SHARED.classLoader)) {
                return Optional.empty();
            }
            return of(typeLoader).find(type.getName()).filter(builder -> builder.annotationType() == type);
        }
    };

    /**
     * The builders found by name among those of a class loader, held weakly: nothing kept here holds the class loader,
     * so a disposable one stays collectable. The builder found is kept alive by {@link #HELD} on its own class. What
     * is found is forgotten when the service entries are invalidated, see
     * {@link MicronautMetaServiceLoaderUtils#invalidate()}.
     */
    private static final Map<ClassLoader, Found> OF_LOADER = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The builder found of a builder class, held by that class: a builder a disposable class loader defines is
     * collected with it, which a strong reference from {@link #OF_LOADER} would prevent.
     */
    private static final ClassValue<AtomicReference<AnnotationBuilder<?>>> HELD = new ClassValue<>() {
        @Override
        protected AtomicReference<AnnotationBuilder<?>> computeValue(Class<?> type) {
            return new AtomicReference<>();
        }
    };

    private static final WeakReference<AnnotationBuilder<?>> NOT_FOUND = new WeakReference<>(null);

    private final ClassLoader classLoader;
    private final Supplier<Map<String, List<ServiceDefinition<AnnotationBuilder<?>>>>> definitions = SupplierUtil.memoized(this::scan);
    private final Map<String, Optional<AnnotationBuilder<?>>> builders = new ConcurrentHashMap<>();

    private AnnotationBuilderRegistry(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * @return The registry of the builders visible to the class loader of this library
     */
    public static AnnotationBuilderRegistry shared() {
        return SHARED;
    }

    /**
     * @param classLoader The class loader the builders are loaded from
     * @return A registry of the builders visible to the class loader
     */
    public static AnnotationBuilderRegistry of(ClassLoader classLoader) {
        return new AnnotationBuilderRegistry(classLoader);
    }

    /**
     * Finds the builder of an annotation type among the builders of Micronaut, then of the class loader of the
     * annotation type, then of the given class loader. A class loader is passed over where an earlier step has
     * searched it or a class loader below it already, since its builders are visible there. What the class loader of
     * the type supplies is kept on the type, and what the given class loader supplies is kept for that class loader
     * without keeping it from being collected, so the lookups that follow find the same builder without a search.
     *
     * @param annotationType The annotation type
     * @param classLoader    The class loader of the application, if known
     * @param <A>            The annotation type
     * @return The builder, empty when none is registered for this very type
     */
    @SuppressWarnings("unchecked")
    static <A extends Annotation> Optional<AnnotationBuilder<A>> find(Class<A> annotationType, @Nullable ClassLoader classLoader) {
        Optional<AnnotationBuilder<A>> builder = SHARED.find(annotationType);
        if (builder.isPresent()) {
            return builder;
        }
        Optional<AnnotationBuilder<?>> ofTypeLoader = OF_TYPE_LOADER.get(annotationType);
        if (ofTypeLoader.isPresent()) {
            return Optional.of((AnnotationBuilder<A>) ofTypeLoader.get());
        }
        ClassLoader typeLoader = annotationType.getClassLoader();
        if (classLoader != null && !isSameOrAncestor(classLoader, SHARED.classLoader)
            && (typeLoader == null || !isSameOrAncestor(classLoader, typeLoader))) {
            Optional<AnnotationBuilder<?>> ofLoader = find(annotationType.getName(), classLoader);
            if (ofLoader.isPresent() && ofLoader.get().annotationType() == annotationType) {
                return Optional.of((AnnotationBuilder<A>) ofLoader.get());
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the builder of an annotation type by name among the builders visible to a class loader, that class loader
     * alone. What is found is kept for the class loader, without keeping the class loader from being collected.
     *
     * @param annotationName The name of the annotation type
     * @param classLoader    The class loader
     * @return The builder, empty when none is registered
     */
    static Optional<AnnotationBuilder<?>> find(String annotationName, ClassLoader classLoader) {
        // read before the lookup: what a lookup that overlaps an invalidation finds goes to a record that is replaced
        long generation = MicronautMetaServiceLoaderUtils.generation();
        Map<String, WeakReference<AnnotationBuilder<?>>> found = OF_LOADER.compute(classLoader,
            (loader, existing) -> existing == null || existing.generation != generation ? new Found(generation, new ConcurrentHashMap<>()) : existing).builders;
        WeakReference<AnnotationBuilder<?>> reference = found.get(annotationName);
        if (reference == NOT_FOUND) {
            return Optional.empty();
        }
        AnnotationBuilder<?> builder = reference != null ? reference.get() : null;
        if (builder == null) {
            Optional<AnnotationBuilder<?>> loaded = of(classLoader).find(annotationName);
            if (loaded.isEmpty()) {
                found.put(annotationName, NOT_FOUND);
                return loaded;
            }
            builder = held(loaded.get());
            found.put(annotationName, new WeakReference<>(builder));
        }
        return Optional.of(builder);
    }

    /**
     * The one builder kept for a builder class: the first one found, which every lookup that follows returns.
     *
     * @param builder The builder found
     * @return The builder held by its class
     */
    private static AnnotationBuilder<?> held(AnnotationBuilder<?> builder) {
        AnnotationBuilder<?> first = HELD.get(builder.getClass()).compareAndExchange(null, builder);
        return first != null ? first : builder;
    }

    /**
     * Finds the builder of an annotation type. The builder can be kept and reused.
     *
     * @param annotationType The annotation type
     * @param <A>            The annotation type
     * @return The builder, empty when none is registered for this very type
     */
    @SuppressWarnings("unchecked")
    public <A extends Annotation> Optional<AnnotationBuilder<A>> find(Class<A> annotationType) {
        Optional<AnnotationBuilder<?>> builder = find(annotationType.getName());
        if (builder.isPresent() && builder.get().annotationType() == annotationType) {
            return Optional.of((AnnotationBuilder<A>) builder.get());
        }
        return Optional.empty();
    }

    /**
     * Finds the builder of an annotation type by name.
     *
     * @param annotationName The name of the annotation type
     * @return The builder, empty when none is registered
     */
    public Optional<AnnotationBuilder<?>> find(String annotationName) {
        return builders.computeIfAbsent(annotationName, this::load);
    }

    /**
     * Whether a builder is registered for the annotation type.
     *
     * @param annotationType The annotation type
     * @return Whether there is one
     */
    public boolean contains(Class<? extends Annotation> annotationType) {
        return find(annotationType).isPresent();
    }

    /**
     * Builds an annotation from the member values.
     *
     * @param annotationType    The annotation type
     * @param values            The member values by member name
     * @param conversionService The conversion service converting the values to the member types
     * @param <A>               The annotation type
     * @return The annotation
     * @throws IllegalArgumentException When no builder is registered for the type
     */
    public <A extends Annotation> A build(Class<A> annotationType, Map<? extends CharSequence, ?> values, ConversionService conversionService) {
        return require(annotationType).build(values, conversionService);
    }

    /**
     * Builds an annotation from the member values, converted by the {@link ConversionService#SHARED shared}
     * conversion service.
     *
     * @param annotationType The annotation type
     * @param values         The member values by member name
     * @param <A>            The annotation type
     * @return The annotation
     * @throws IllegalArgumentException When no builder is registered for the type
     */
    public <A extends Annotation> A build(Class<A> annotationType, Map<? extends CharSequence, ?> values) {
        return build(annotationType, values, ConversionService.SHARED);
    }

    /**
     * Builds an annotation from an annotation value.
     *
     * @param annotationType    The annotation type
     * @param annotationValue   The annotation value
     * @param conversionService The conversion service converting the values to the member types
     * @param <A>               The annotation type
     * @return The annotation
     * @throws IllegalArgumentException When no builder is registered for the type
     */
    public <A extends Annotation> A build(Class<A> annotationType, AnnotationValue<? extends Annotation> annotationValue, ConversionService conversionService) {
        return require(annotationType).build(annotationValue, conversionService);
    }

    /**
     * Builds an annotation from an annotation value, converted by the {@link ConversionService#SHARED shared}
     * conversion service.
     *
     * @param annotationType  The annotation type
     * @param annotationValue The annotation value
     * @param <A>             The annotation type
     * @return The annotation
     * @throws IllegalArgumentException When no builder is registered for the type
     */
    public <A extends Annotation> A build(Class<A> annotationType, AnnotationValue<? extends Annotation> annotationValue) {
        return build(annotationType, annotationValue, ConversionService.SHARED);
    }

    /**
     * Builds an annotation by the name of its type.
     *
     * @param annotationName    The annotation type name
     * @param values            The member values by member name
     * @param conversionService The conversion service converting the values to the member types
     * @return The annotation
     * @throws IllegalArgumentException When no builder is registered for the name
     */
    public Annotation build(String annotationName, Map<? extends CharSequence, ?> values, ConversionService conversionService) {
        return find(annotationName)
            .orElseThrow(() -> noBuilder(annotationName))
            .build(values, conversionService);
    }

    /**
     * Builds an annotation from an annotation value, by the name of its type.
     *
     * @param annotationValue   The annotation value
     * @param conversionService The conversion service converting the values to the member types
     * @return The annotation
     * @throws IllegalArgumentException When no builder is registered for the name of the annotation value
     */
    public Annotation build(AnnotationValue<? extends Annotation> annotationValue, ConversionService conversionService) {
        String annotationName = annotationValue.getAnnotationName();
        return find(annotationName)
            .orElseThrow(() -> noBuilder(annotationName))
            .build(annotationValue, conversionService);
    }

    /**
     * Whether a class loader is another one or one of its parents, whose builders the other one sees as well.
     *
     * @param candidate   The class loader
     * @param classLoader The class loader that may delegate to it
     * @return Whether the candidate is the class loader or one of its parents
     */
    private static boolean isSameOrAncestor(ClassLoader candidate, @Nullable ClassLoader classLoader) {
        for (ClassLoader current = classLoader; current != null; current = current.getParent()) {
            if (current == candidate) {
                return true;
            }
        }
        return false;
    }

    private <A extends Annotation> AnnotationBuilder<A> require(Class<A> annotationType) {
        return find(annotationType).orElseThrow(() -> noBuilder(annotationType.getName()));
    }

    private static IllegalArgumentException noBuilder(String annotationName) {
        return new IllegalArgumentException("No annotation builder is registered for [" + annotationName + "]");
    }

    private Optional<AnnotationBuilder<?>> load(String annotationName) {
        List<ServiceDefinition<AnnotationBuilder<?>>> candidates = definitions.get().get(mangle(annotationName));
        if (candidates != null) {
            for (ServiceDefinition<AnnotationBuilder<?>> candidate : candidates) {
                if (candidate.isPresent()) {
                    AnnotationBuilder<?> builder = candidate.load();
                    if (builder.annotationType().getName().equals(annotationName)) {
                        return Optional.of(builder);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Map<String, List<ServiceDefinition<AnnotationBuilder<?>>>> scan() {
        Map<String, List<ServiceDefinition<AnnotationBuilder<?>>>> result = new HashMap<>();
        @SuppressWarnings("unchecked")
        Class<AnnotationBuilder<?>> serviceType = (Class<AnnotationBuilder<?>>) (Class<?>) AnnotationBuilder.class;
        for (ServiceDefinition<AnnotationBuilder<?>> definition : SoftServiceLoader.load(serviceType, classLoader)) {
            String name = definition.getName();
            if (name.endsWith(BUILDER_SUFFIX)) {
                String withoutSuffix = name.substring(0, name.length() - BUILDER_SUFFIX.length());
                int start = withoutSuffix.lastIndexOf('$') + 1;
                result.computeIfAbsent(withoutSuffix.substring(start), key -> new ArrayList<>()).add(definition);
            }
        }
        return result;
    }

    /**
     * The form an annotation type name takes in the name of its builder: the builder is placed in the package of
     * the annotated type, so the name of the annotation is flattened to a single identifier, without a dollar
     * sign so that it can be told from the name of the annotated type.
     *
     * @param annotationName The annotation type name
     * @return The flattened name
     */
    @Internal
    public static String mangle(String annotationName) {
        return annotationName.replace('.', '_').replace('$', '_');
    }

    /**
     * The builders found among those of a class loader in one generation of the service entries.
     *
     * @param generation The generation of the service entries
     * @param builders   The builders found by annotation name, or {@link #NOT_FOUND}
     */
    private record Found(long generation, Map<String, WeakReference<AnnotationBuilder<?>>> builders) {
    }
}
