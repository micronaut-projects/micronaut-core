/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.beans;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanContextConfiguration;
import io.micronaut.context.BeanDefinitionsProvider;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DisabledBean;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.QualifiedBeanType;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The default implementation of {@link BeanDefinitionService}.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class DefaultBeanDefinitionService implements BeanDefinitionService {

    private static final List<Class<?>> KNOWN_INDEX_TYPES = List.of(
        ResourceLoader.class,
        TypeConverter.class,
        TypeConverterRegistrar.class,
        ApplicationEventListener.class,
        BeanCreatedEventListener.class,
        BeanInitializedEventListener.class,
        BeanPreDestroyEventListener.class,
        BeanDestroyedEventListener.class,
        ConversionService.class
    );

    private final String[] eagerInitStereotypes;
    private final boolean eagerInitStereotypesPresent;
    private final boolean eagerInitSingletons;

    // The collection should be modified only when new bean definition is added
    // That shouldn't happen that often, so we can use CopyOnWriteArrayList

    private final CopyOnWriteArrayList<BeanDefinitionProducer> disabledBeans = new CopyOnWriteArrayList<>();

    private static final Function<Class<?>, List<BeanDefinitionProducer>> COMPUTE_INDEXES_FN = new Function<Class<?>, List<BeanDefinitionProducer>>() {
        // Keep an anonymous class for performance
        @Override
        public List<BeanDefinitionProducer> apply(Class<?> indexedType) {
            return new ArrayList<>(20);
        }
    };

    private final BeanDefinitionsProvider beanDefinitionReferencesProvider;
    @Nullable
    private final Predicate<QualifiedBeanType<?>> beansPredicate;

    private final ClassLoader classLoader;
    private Beans beans;

    private final List<BeanDefinitionProducer> additionalBeanDefinitions = new ArrayList<>();
    private final List<BeanConfiguration> additionalBeanConfigurations = new ArrayList<>();

    /**
     * Creates a new bean context with the given configuration.
     *
     * @param contextConfiguration The context configuration
     */
    public DefaultBeanDefinitionService(@NonNull BeanContextConfiguration contextConfiguration) {
        Set<Class<? extends Annotation>> eagerInitAnnotated = contextConfiguration.getEagerInitAnnotated();
        List<String> configuredEagerSingletonAnnotations = new ArrayList<>(eagerInitAnnotated.size());
        for (Class<? extends Annotation> ann : eagerInitAnnotated) {
            configuredEagerSingletonAnnotations.add(ann.getName());
        }
        this.eagerInitStereotypes = configuredEagerSingletonAnnotations.toArray(StringUtils.EMPTY_STRING_ARRAY);
        this.eagerInitStereotypesPresent = !configuredEagerSingletonAnnotations.isEmpty();
        this.eagerInitSingletons = eagerInitStereotypesPresent && (configuredEagerSingletonAnnotations.contains(AnnotationUtil.SINGLETON) || configuredEagerSingletonAnnotations.contains(Singleton.class.getName()));
        this.beansPredicate = contextConfiguration.beansPredicate();
        this.beanDefinitionReferencesProvider = contextConfiguration.getBeanDefinitionsProvider();
        this.classLoader = contextConfiguration.getClassLoader();
        if (beans == null) {
            beans = createBeans(resolveBeanDefinitionReferences(), List.of());
        }
    }

    /**
     * Configures the context reading all bean definitions.
     */
    @Internal
    public void initialize(BeanContext beanContext) {
        readBeanDefinitionReferences(beanContext);
        for (Class<?> indexType : KNOWN_INDEX_TYPES) {
            resolveTypeIndex(indexType);
        }
    }

    @Override
    public void registerConfiguration(BeanConfiguration configuration) {
        additionalBeanConfigurations.add(configuration);
    }

    @Override
    @NonNull
    public void addBeanDefinition(@NonNull RuntimeBeanDefinition<?> definition) {
        Objects.requireNonNull(definition, "Bean definition cannot be null");
        BeanDefinitionProducer producer = new BeanDefinitionProducer(definition);
        if (beans == null) {
            additionalBeanDefinitions.add(producer);
        } else {
            addBeanDefinition(producer);
        }
    }

    private void addBeanDefinition(BeanDefinitionProducer producer) {
        beans.all.add(producer);
        BeanDefinitionReference<?> reference = producer.reference;
        Class<?> beanType = producer.reference.getBeanType();
        boolean beanTypeIndexAdded = false;
        for (Class<?> exposedType : reference.getExposedTypes()) {
            resolveTypeIndex(exposedType).add(producer);
            if (beanType.equals(exposedType)) {
                beanTypeIndexAdded = true;
            }
        }
        if (!beanTypeIndexAdded) {
            resolveTypeIndex(beanType).add(producer);
        }
    }

    @Override
    public <B> Iterable<BeanDefinition<B>> getBeanDefinitions(BeanContext beanContext,
                                                              Argument<B> beanType,
                                                              Predicate<BeanDefinitionReference<B>> refPredicate,
                                                              Predicate<BeanDefinition<B>> defPredicate) {
        return getBeanDefinitions(resolveProducersForBeanType(beanType), beanContext, null, beanType, refPredicate, defPredicate);
    }

    @Override
    public List<BeanDefinitionReference<Object>> getBeanReferences() {
        if (beans == null) {
            return List.of();
        }
        List<BeanDefinitionProducer> all = beans.all;
        List<BeanDefinitionReference<Object>> references = new ArrayList<>(all.size());
        for (BeanDefinitionProducer producer : all) {
            BeanDefinitionReference<?> reference = producer.reference;
            if (reference != null) {
                references.add((BeanDefinitionReference<Object>) reference);
            }
        }
        return references;
    }

    @Override
    public <B> Iterable<BeanDefinition<B>> getBeanDefinitions(BeanResolutionContext beanResolutionContext, Argument<B> beanType, Predicate<BeanDefinitionReference<B>> refPredicate, Predicate<BeanDefinition<B>> defPredicate) {
        return getBeanDefinitions(resolveProducersForBeanType(beanType), null, beanResolutionContext, beanType, refPredicate, defPredicate);
    }

    private <B> Iterable<BeanDefinition<B>> getBeanDefinitions(Collection<BeanDefinitionProducer> producers,
                                                               BeanContext beanContext,
                                                               Argument<B> beanType,
                                                               Predicate<BeanDefinitionReference<B>> refPredicate,
                                                               Predicate<BeanDefinition<B>> defPredicate) {
        return getBeanDefinitions(producers, beanContext, null, beanType, refPredicate, defPredicate);
    }

    private <B> Iterable<BeanDefinition<B>> getBeanDefinitions(Collection<BeanDefinitionProducer> producers,
                                                               @Nullable
                                                               BeanContext beanContext,
                                                               @Nullable
                                                               BeanResolutionContext beanResolutionContext,
                                                               Argument<B> beanType,
                                                               Predicate<BeanDefinitionReference<B>> refPredicate,
                                                               Predicate<BeanDefinition<B>> defPredicate) {
        return new Iterable<>() {

            @Override
            public Iterator<BeanDefinition<B>> iterator() {
                Iterator<BeanDefinitionProducer> iterator = producers.iterator();
                return new Iterator<>() {

                    private BeanDefinition<B> next;

                    private void advance() {
                        while (next == null && iterator.hasNext()) {
                            next = iterator.next().getDefinitionIfEnabled(
                                beanResolutionContext != null ? beanResolutionContext.getContext() : beanContext,
                                beanResolutionContext,
                                beanType,
                                refPredicate,
                                defPredicate
                            );
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        advance();
                        return next != null;
                    }

                    @Override
                    public BeanDefinition<B> next() {
                        advance();
                        if (next == null) {
                            throw new NoSuchElementException();
                        }
                        BeanDefinition<B> value = next;
                        next = null;
                        return value;
                    }
                };
            }
        };
    }

    @Override
    public Iterable<BeanDefinitionReference<Object>> getBeanReferences(BeanContext beanContext) {
        return new Iterable<>() {

            @Override
            public Iterator<BeanDefinitionReference<Object>> iterator() {
                Iterator<BeanDefinitionProducer> iterator = beans.all.iterator();
                return new Iterator<>() {

                    private BeanDefinitionReference<Object> next;

                    private void advance() {
                        while (next == null && iterator.hasNext()) {
                            next = iterator.next().getReferenceIfEnabled(beanContext);
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        advance();
                        return next != null;
                    }

                    @Override
                    public BeanDefinitionReference<Object> next() {
                        advance();
                        if (next == null) {
                            throw new NoSuchElementException();
                        }
                        BeanDefinitionReference<Object> value = next;
                        next = null;
                        return value;
                    }
                };
            }
        };
    }

    private List<BeanDefinitionProducer> resolveProducersForBeanType(Argument<?> beanType) {
        Class<?> type = beanType.getType();
        if (type == Object.class) {
            return beans.all;
        }
        List<BeanDefinitionProducer> producers = beans.beanIndex.get(type);
        if (producers == null) {
            return List.of();
        }
        return producers;
    }

    @Override
    public void reset() {
        beans = null;
        disabledBeans.clear();
        additionalBeanDefinitions.clear();
        additionalBeanConfigurations.clear();
    }

    /**
     * The definition to remove.
     *
     * @param definition The definition to remove
     */
    @Internal
    public void removeBeanDefinition(RuntimeBeanDefinition<?> definition) {
        Class<?> beanType = definition.getBeanType();
        for (Class<?> indexedType : beans.beanIndex.keySet()) {
            if (indexedType == beanType || indexedType.isAssignableFrom(beanType)) {
                resolveTypeIndex(indexedType).forEach(p -> p.disableIfMatch(definition));
                break;
            }
        }
        beans.all.forEach(p -> p.disableIfMatch(definition));
    }

    @Override
    public void trackDisabled(QualifiedBeanType<?> beanType, List<String> reasons) {
        try {
            @SuppressWarnings("unchecked")
            Argument<Object> argument = (Argument<Object>) beanType.getGenericBeanType();
            @SuppressWarnings("unchecked")
            Qualifier<Object> declaredQualifier = (Qualifier<Object>) beanType.getDeclaredQualifier();
            this.disabledBeans.add(new BeanDefinitionProducer(new DisabledBean<>(
                argument,
                declaredQualifier,
                reasons
            )));
        } catch (Exception | NoClassDefFoundError e) {
            // it is theoretically possible that resolving the generic type results in an error
            // in this case just ignore this as the maps built here are purely to aid error diagnosis
        }
    }

    @Override
    public Iterable<BeanDefinition<Object>> getEagerInitBeans(BeanContext beanContext) {
        return getBeanDefinitions(beans.eagerInitBeans, beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public Iterable<BeanDefinition<Object>> getProcessedBeans(BeanContext beanContext) {
        return getBeanDefinitions(beans.processedBeans, beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public Iterable<BeanDefinition<Object>> getParallelBeans(BeanContext beanContext) {
        return getBeanDefinitions(beans.parallelBeans, beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public Iterable<BeanDefinition<Object>> getTargetProxyBeans(BeanContext beanContext) {
        return getBeanDefinitions(beans.proxyTargetBeans, beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public List<DisabledBean<?>> getDisabledBeans(BeanContext beanContext) {
        return disabledBeans.stream()
            .map(producer -> (DisabledBean<?>) producer.reference)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @NonNull
    private Collection<BeanDefinitionProducer> resolveTypeIndex(Class<?> indexedType) {
        return beans.beanIndex.computeIfAbsent(indexedType, COMPUTE_INDEXES_FN);
    }

    @Override
    public boolean exists(Class<?> beanType) {
        if (beans == null) {
            for (BeanDefinitionReference<?> beanDefinitionReference : resolveBeanDefinitionReferences()) {
                if (beanType.isAssignableFrom(beanDefinitionReference.getBeanType())) {
                    return true;
                }
            }
            return false;
        }
        List<BeanDefinitionProducer> refs = beans.beanIndex.get(beanType);
        return refs != null && !refs.isEmpty();
    }

    @NonNull
    private void readBeanDefinitionReferences(BeanContext beanContext) {
        if (beans == null) {
            beans = createBeans(resolveBeanDefinitionReferences(), additionalBeanConfigurations);
            additionalBeanConfigurations.clear();
        }
        if (!additionalBeanDefinitions.isEmpty()) {
            for (BeanDefinitionProducer additionalBeanDefinition : additionalBeanDefinitions) {
                BeanDefinitionReference<?> reference = additionalBeanDefinition.reference;
                if (reference == null) {
                    continue;
                }
                addBeanDefinition(additionalBeanDefinition);
                for (Map.Entry<BeanConfiguration, List<BeanDefinitionProducer>> e : beans.byConfiguration) {
                    BeanConfiguration configuration = e.getKey();
                    if (!configuration.isEnabled(beanContext) && configuration.isWithin(reference)) {
                        additionalBeanDefinition.disable();
                    }
                }
                for (BeanConfiguration additionalBeanConfiguration : additionalBeanConfigurations) {
                    if (!additionalBeanConfiguration.isEnabled(beanContext) && additionalBeanConfiguration.isWithin(reference)) {
                        additionalBeanDefinition.disable();
                    }
                }
            }
        }

        if (!beans.byConfiguration.isEmpty()) {
            for (Map.Entry<BeanConfiguration, List<BeanDefinitionProducer>> e : beans.byConfiguration) {
                List<BeanDefinitionProducer> producers = e.getValue();
                BeanConfiguration configuration = e.getKey();
                if (!producers.isEmpty() && !configuration.isEnabled(beanContext)) {
                    for (BeanDefinitionProducer producer : producers) {
                        producer.disable();
                    }
                }
            }
        }
        for (BeanConfiguration additionalBeanConfiguration : additionalBeanConfigurations) {
            if (!additionalBeanConfiguration.isEnabled(beanContext)) {
                for (BeanDefinitionProducer producer : beans.all) {
                    BeanDefinitionReference<?> reference = producer.reference;
                    if (reference != null && additionalBeanConfiguration.isWithin(reference)) {
                        producer.disable();
                    }
                }
            }
        }
        if (eagerInitSingletons || eagerInitStereotypesPresent) {
            for (BeanDefinitionProducer beanDefinitionProducer : beans.all) {
                BeanDefinitionReference<?> reference = beanDefinitionProducer.reference;
                if (reference != null && isEagerInit(reference)) {
                    beans.eagerInitBeans.add(beanDefinitionProducer);
                }
            }
        }
    }


    private boolean isEagerInit(BeanDefinitionReference<?> beanDefinitionReference) {
        return eagerInitSingletons && beanDefinitionReference.isSingleton() ||
            eagerInitStereotypesPresent && beanDefinitionReference.getAnnotationMetadata().hasDeclaredStereotype(eagerInitStereotypes);
    }

    private static Beans createBeans(List<BeanDefinitionReference<?>> beanDefinitionReferences, List<BeanConfiguration> beanConfigurations) {
        List<BeanDefinitionProducer> eagerInitBeans = new ArrayList<>(20);
        List<BeanDefinitionProducer> processedBeans = new ArrayList<>(10);
        List<BeanDefinitionProducer> parallelBeans = new ArrayList<>(10);

        Map<Class<?>, List<BeanDefinitionProducer>> indexByType = CollectionUtils.newHashMap(beanDefinitionReferences.size());
        List<BeanDefinitionProducer> all = new ArrayList<>(beanDefinitionReferences.size());
        List<BeanDefinitionProducer> proxyTargetBeans = new ArrayList<>(beanDefinitionReferences.size());
        for (BeanDefinitionReference<?> beanDefinitionReference : beanDefinitionReferences) {
            all.add(new BeanDefinitionProducer(beanDefinitionReference));
        }

        List<Map.Entry<BeanConfiguration, List<BeanDefinitionProducer>>> byConfiguration = new ArrayList<>(beanConfigurations.size());
        for (BeanConfiguration beanConfiguration : beanConfigurations) {
            byConfiguration.add(Map.entry(beanConfiguration, new ArrayList<>()));
        }

        for (BeanDefinitionProducer beanDefinitionProducer : all) {
            BeanDefinitionReference<?> beanDefinitionReference = beanDefinitionProducer.reference;
            for (Map.Entry<BeanConfiguration, List<BeanDefinitionProducer>> e : byConfiguration) {
                BeanConfiguration configuration = e.getKey();
                if (configuration.isWithin(beanDefinitionReference)) {
                    e.getValue().add(beanDefinitionProducer);
                }
            }
            if (beanDefinitionReference.isProxiedBean()) {
                beanDefinitionProducer.disable();
                indexDisabledBean(indexByType, beanDefinitionReference);
                // retain only if proxy target otherwise the target is never used
                if (beanDefinitionReference.isProxyTarget()) {
                    proxyTargetBeans.add(new BeanDefinitionProducer(beanDefinitionReference));
                }
                continue;
            }

            if (beanDefinitionReference.isContextScope()) {
                eagerInitBeans.add(beanDefinitionProducer);
            } else if (beanDefinitionReference.isParallel()) {
                parallelBeans.add(beanDefinitionProducer);
            }
            if (beanDefinitionReference.requiresMethodProcessing()) {
                processedBeans.add(beanDefinitionProducer);
            }

            indexBean(indexByType, beanDefinitionProducer);
        }

        return new Beans(
            eagerInitBeans,
            processedBeans,
            parallelBeans,
            new CopyOnWriteArrayList<>(all),
            proxyTargetBeans,
            new ConcurrentHashMap<>(indexByType),
            byConfiguration
        );
    }

    private static void indexBean(Map<Class<?>, List<BeanDefinitionProducer>> indexByType, BeanDefinitionProducer beanDefinitionProducer) {
        BeanDefinitionReference<?> reference = beanDefinitionProducer.reference;
        Set<Class<?>> exposedTypes = reference.getExposedTypes();
        if (exposedTypes.isEmpty()) {
            // The reference must be compiled prior to v5, use reflection to find exposed types till it's recompiled
            exposedTypes = ReflectionUtils.getAllClassesInHierarchy(reference.getBeanType());
        }
        for (Class<?> indexedType : exposedTypes) {
            indexByType.computeIfAbsent(indexedType, COMPUTE_INDEXES_FN).add(beanDefinitionProducer);
        }
    }

    private static void indexDisabledBean(Map<Class<?>, List<BeanDefinitionProducer>> indexByType, BeanDefinitionReference<?> reference) {
        // For disabled beans we want to initialize the index collection, otherwise MISS will cause N search
        for (Class<?> indexedType : reference.getExposedTypes()) {
            indexByType.computeIfAbsent(indexedType, COMPUTE_INDEXES_FN);
        }
    }

    @NonNull
    private List<BeanDefinitionReference<?>> resolveBeanDefinitionReferences() {
        List<BeanDefinitionReference<?>> refs = beanDefinitionReferencesProvider.provide(classLoader);
        if (beansPredicate != null) {
            List<BeanDefinitionReference<?>> list = new ArrayList<>(refs.size());
            for (BeanDefinitionReference<?> ref : refs) {
                if (beansPredicate.test(ref)) {
                    list.add(ref);
                }
            }
            refs = list;
        }
        return refs;
    }

    private record Beans(
        List<BeanDefinitionProducer> eagerInitBeans,
        List<BeanDefinitionProducer> processedBeans,
        List<BeanDefinitionProducer> parallelBeans,
        List<BeanDefinitionProducer> all,
        List<BeanDefinitionProducer> proxyTargetBeans,
        Map<Class<?>, List<BeanDefinitionProducer>> beanIndex,
        List<Map.Entry<BeanConfiguration, List<BeanDefinitionProducer>>> byConfiguration
    ) {
    }

    /**
     * The class adds the caching of the enabled decision + the definition instance.
     * NOTE: The class can be accessed in multiple threads, we do allow for the fields to be possibly initialized concurrently - multiple times.
     *
     * @since 4.0.0
     */
    @Internal
    private static final class BeanDefinitionProducer {
        private static final AtomicReferenceFieldUpdater<BeanDefinitionProducer, Object> DEFINITION_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(BeanDefinitionProducer.class, Object.class, "definition");
        private static final Object DEFINITION_DISABLED_SENTINEL = "";

        /**
         * Initially the reference, may be set to {@code null} by {@link #disable()} or if
         * {@link #getReferenceIfEnabled} determines the ref is disabled.
         */
        @Nullable
        @SuppressWarnings("java:S3077")
        private volatile BeanDefinitionReference<?> reference;
        /**
         * Initially {@code null}. If the {@link #reference} is enabled, and the definition from it
         * is also enabled, this is set to the {@link BeanDefinition}. If the definition is
         * disabled (either through conditions or explicitly by {@link #disable()}), this is set to
         * {@link #DEFINITION_DISABLED_SENTINEL}.
         */
        @Nullable
        @SuppressWarnings("java:S3077")
        private volatile Object definition;

        BeanDefinitionProducer(@NonNull BeanDefinitionReference<?> reference) {
            this.reference = reference;
        }

        private static boolean isReferenceEnabled(BeanDefinitionReference<?> ref, BeanContext context, BeanResolutionContext resolutionContext) {
            if (ref == null) {
                return false;
            }
            if (ref instanceof io.micronaut.context.AbstractInitializableBeanDefinitionAndReference<?> referenceAndDefinition) {
                return referenceAndDefinition.isEnabled(context, resolutionContext, true);
            }
            return ref.isEnabled(context);
        }

        static boolean isDefinitionEnabled(@NonNull BeanContext context,
                                           @Nullable BeanResolutionContext resolutionContext,
                                           @Nullable BeanDefinition<?> def) {
            if (def == null) {
                return false;
            }
            if (def instanceof io.micronaut.context.AbstractInitializableBeanDefinitionAndReference<?> definitionAndReference) {
                return definitionAndReference.isEnabled(context, resolutionContext, false);
            }
            return def.isEnabled(context, resolutionContext);
        }

        @Nullable
        <T> BeanDefinitionReference<T> getReferenceIfEnabled(BeanContext context) {
            return getReferenceIfEnabled(context, null);
        }

        @Nullable
        <T> BeanDefinitionReference<T> getReferenceIfEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            BeanDefinitionReference ref = reference;
            if (ref == null) {
                return null;
            }
            if (isReferenceEnabled(ref, context, resolutionContext)) {
                return ref;
            } else {
                this.reference = null;
                return null;
            }
        }

        @Nullable
        <T> BeanDefinition<T> getDefinitionIfEnabled(BeanContext context,
                                                     @Nullable BeanResolutionContext resolutionContext,
                                                     @Nullable Argument<T> beanType,
                                                     @Nullable Predicate<BeanDefinitionReference<T>> refPredicate,
                                                     @Nullable Predicate<BeanDefinition<T>> defPredicate) {
            Object defObject = this.definition;
            if (defObject != DEFINITION_DISABLED_SENTINEL && defObject != null) {
                if (refPredicate != null) {
                    BeanDefinitionReference<T> ref = (BeanDefinitionReference<T>) reference;
                    if (ref == null || !refPredicate.test(ref)) {
                        return null;
                    }
                }
                BeanDefinition<T> def = (BeanDefinition<T>) defObject;
                if (beanType != null && !(beanType.getType().equals(Object.class) || def.isCandidateBean(beanType))) {
                    return null;
                }
                if (defPredicate != null && !defPredicate.test(def)) {
                    return null;
                }
                return def;
            }
            BeanDefinitionReference<T> ref = getReferenceIfEnabled(context, resolutionContext);
            if (ref == null) {
                // shortcut for future calls
                this.definition = DEFINITION_DISABLED_SENTINEL;
                return null;
            }
            if (beanType != null && !(beanType.getType().equals(Object.class) || ref.isCandidateBean(beanType))) {
                return null;
            }
            if (refPredicate != null && !refPredicate.test(ref)) {
                return null;
            }
            BeanDefinition<T> def = ref.load(context);
            if (def == null) {
                return null;
            }
            if (defPredicate != null && !defPredicate.test(def)) {
                return null;
            }
            if (isDefinitionEnabled(context, resolutionContext, def)) {
                if (DEFINITION_UPDATER.compareAndSet(this, null, def)) {
                    return def;
                } else {
                    defObject = this.definition;
                    return defObject != DEFINITION_DISABLED_SENTINEL && defObject != null ? (BeanDefinition<T>) defObject : null;
                }
            } else {
                this.definition = DEFINITION_DISABLED_SENTINEL;
                return null;
            }
        }

        void disableIfMatch(BeanDefinitionReference<?> toDisable) {
            if (Objects.equals(toDisable, this.reference)) {
                disable();
            }
        }

        void disable() {
            this.reference = null;
            this.definition = DEFINITION_DISABLED_SENTINEL;
        }

        @Override
        public String toString() {
            Object def = definition;
            if (def != null) {
                return def.toString();
            }
            BeanDefinitionReference ref = reference;
            if (ref == null) {
                return "BeanDefinitionProducer{disabled}";
            }
            return ref.toString();
        }
    }
}
