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
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.SupplierUtil;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The registry of the {@link AnnotationBuilder}s the service loader supplies, which the annotation processor
 * generates for the types listed in {@link RegisterAnnotations}.
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
}
