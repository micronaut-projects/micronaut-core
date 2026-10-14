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
import io.micronaut.context.BeanResolutionCustomizer;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.context.DisabledBean;
import io.micronaut.context.LazyBeanDefinitionReference;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.QualifiedBeanType;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
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

    private static final String WARM_UP_ALL = "all";

    private final String[] eagerInitStereotypes;
    private final boolean eagerInitStereotypesPresent;
    private final boolean eagerInitSingletons;

    /**
     * Whether the context reads its definitions lazily: a definition whose entry carries a descriptor is loaded only
     * when a lookup needs it. Every lazy step is in {@link LazyBeans} and {@link LazyBeanDefinitionProducer}, which
     * are never loaded otherwise.
     */
    private final boolean lazy;
    private final boolean warmUp;
    /**
     * Whether the warm-up also loads every lazy reference whose conditions are only of classes that are present.
     */
    private final boolean warmUpAll;
    /**
     * In lazy mode, the references as the provider gave them, which carry what it counted, until the service is reset.
     */
    @Nullable
    private List<BeanDefinitionReference<?>> providedReferences;
    /**
     * In lazy mode, the warm-up of the current definitions, or {@code null}.
     */
    @Nullable
    private volatile Object runningWarmUp;
    /**
     * In lazy mode, the disabled beans of lazy references, built when they are read; {@code null} otherwise.
     */
    @Nullable
    private final CopyOnWriteArrayList<Object> deferredDisabledBeans;

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
    @Nullable
    private final Predicate<BeanConfiguration> beanConfigurationsPredicate;

    private final ClassLoader classLoader;
    @Nullable
    private Beans beans;

    /**
     * The producers of {@link #beans}, keyed by the definition name their reference reports. Built on the first
     * lookup by definition class and dropped as soon as the producers are rebuilt, which the {@link Beans} the
     * index was built from detects. Only a {@link io.micronaut.context.RuntimeBeanDefinition} is added to a live
     * {@code Beans}, and the name it generates is never a compiled definition class name, so an index built
     * before such an addition cannot answer a lookup wrongly.
     */
    @Nullable
    @SuppressWarnings("java:S3077")
    private volatile ProducersByDefinitionName producersByDefinitionName;

    private final BeanResolutionCustomizer beanResolutionCustomizer;

    private final List<BeanDefinitionProducer> additionalBeanDefinitions = new ArrayList<>();
    private final List<BeanConfiguration> additionalBeanConfigurations = new ArrayList<>();

    /**
     * Creates a new bean definition service with the given configuration and the customizer serving the context.
     *
     * @param contextConfiguration The context configuration
     * @param beanResolutionCustomizer The bean resolution customizer of the context
     */
    public DefaultBeanDefinitionService(BeanContextConfiguration contextConfiguration, BeanResolutionCustomizer beanResolutionCustomizer) {
        Set<Class<? extends Annotation>> eagerInitAnnotated = contextConfiguration.getEagerInitAnnotated();
        List<String> configuredEagerSingletonAnnotations = new ArrayList<>(eagerInitAnnotated.size());
        for (Class<? extends Annotation> ann : eagerInitAnnotated) {
            configuredEagerSingletonAnnotations.add(ann.getName());
        }
        this.eagerInitStereotypes = configuredEagerSingletonAnnotations.toArray(StringUtils.EMPTY_STRING_ARRAY);
        this.eagerInitStereotypesPresent = !configuredEagerSingletonAnnotations.isEmpty();
        this.eagerInitSingletons = eagerInitStereotypesPresent && (configuredEagerSingletonAnnotations.contains(AnnotationUtil.SINGLETON) || configuredEagerSingletonAnnotations.contains(Singleton.class.getName()));
        this.beansPredicate = contextConfiguration.beansPredicate();
        this.beanConfigurationsPredicate = contextConfiguration.beanConfiguraionsPredicate();
        this.beanDefinitionReferencesProvider = contextConfiguration.getBeanDefinitionsProvider();
        this.beanResolutionCustomizer = Objects.requireNonNull(beanResolutionCustomizer, "Bean resolution customizer cannot be null");
        this.classLoader = contextConfiguration.getClassLoader();
        this.lazy = (Boolean.getBoolean(DefaultBeanDefinitionsProvider.LAZY_PROPERTY) || Boolean.getBoolean(DefaultBeanDefinitionsProvider.LAZY_VERIFY_PROPERTY))
            && !NativeImageUtils.inImageCode();
        String warmUpProperty = lazy ? System.getProperty(DefaultBeanDefinitionsProvider.LAZY_WARM_UP_PROPERTY) : null;
        this.warmUp = lazy && ForkJoinPool.getCommonPoolParallelism() > 1 && !StringUtils.FALSE.equals(warmUpProperty);
        this.warmUpAll = warmUp && WARM_UP_ALL.equals(warmUpProperty);
        this.deferredDisabledBeans = lazy ? new CopyOnWriteArrayList<>() : null;
        if (beans == null) {
            beans = createBeans(resolveBeanDefinitionReferences(), List.of());
        }
    }

    /**
     * Creates the producers of the references, and in lazy mode starts their warm-up.
     */
    private Beans createBeans(List<BeanDefinitionReference<?>> beanDefinitionReferences, List<BeanConfiguration> beanConfigurations) {
        if (!lazy) {
            return createEagerBeans(beanDefinitionReferences, beanConfigurations);
        }
        Beans created = LazyBeans.createBeans(beanDefinitionReferences, beanConfigurations);
        if (warmUp) {
            runningWarmUp = LazyBeans.warmUp(created, warmUpAll);
        }
        return created;
    }

    /**
     * Configures the context reading all bean definitions.
     */
    @Internal
    @Override
    public void initialize(BeanContext beanContext) {
        readBeanDefinitionReferences(beanContext);
        for (Class<?> indexType : KNOWN_INDEX_TYPES) {
            resolveTypeIndex(indexType);
        }
    }

    @Override
    public void registerConfiguration(BeanConfiguration configuration) {
        if (beanConfigurationsPredicate == null || beanConfigurationsPredicate.test(configuration)) {
            additionalBeanConfigurations.add(configuration);
        }
    }

    @Override
    public void addBeanDefinition(RuntimeBeanDefinition<?> definition) {
        Objects.requireNonNull(definition, "Bean definition cannot be null");
        BeanDefinitionProducer producer = new BeanDefinitionProducer(definition);
        if (beans == null) {
            additionalBeanDefinitions.add(producer);
        } else {
            addBeanDefinition(producer);
        }
    }

    private void addBeanDefinition(BeanDefinitionProducer producer) {
        BeanDefinitionReference<?> reference = producer.reference;
        if (reference == null) {
            return;
        }
        Objects.requireNonNull(beans).all.add(producer);
        Class<?> beanType = reference.getBeanType();
        boolean beanTypeIndexAdded = false;
        Set<Class<?>> exposedTypes = reference.getExposedTypes();
        if (exposedTypes.isEmpty() && reference instanceof RuntimeBeanDefinition<?>) {
            // Without restricted exposed types the bean is a candidate for its whole hierarchy,
            // including index entries already created by earlier lookups
            exposedTypes = ReflectionUtils.getAllClassesInHierarchy(beanType);
        }
        for (Class<?> exposedType : exposedTypes) {
            resolveTypeIndex(exposedType).add(producer);
            if (beanType.equals(exposedType)) {
                beanTypeIndexAdded = true;
            }
        }
        if (!beanTypeIndexAdded) {
            resolveTypeIndex(beanType).add(producer);
        }
        for (Class<?> indexedType : reference.getIndexes()) {
            if (indexedType != beanType && !exposedTypes.contains(indexedType)) {
                resolveTypeIndex(indexedType).add(producer);
            }
        }
    }

    @Override
    public <B> Iterable<BeanDefinition<B>> getBeanDefinitions(BeanContext beanContext,
                                                              Argument<B> beanType,
                                                              @Nullable Predicate<BeanDefinitionReference<B>> refPredicate,
                                                              @Nullable Predicate<BeanDefinition<B>> defPredicate) {
        return getBeanDefinitions(resolveProducersForBeanType(beanType), beanContext, null, beanType, refPredicate, defPredicate);
    }

    @Override
    public List<BeanDefinitionReference<Object>> getPresentBeanReferences() {
        if (lazy && beans != null) {
            return LazyBeans.presentReferences(beans.all);
        }
        return getBeanReferences();
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
    @Nullable
    public <T> BeanDefinition<T> findBeanDefinitionByDefinitionClass(BeanContext beanContext, Class<? extends BeanDefinition<T>> definitionClass) {
        Beans current = beans;
        if (current == null) {
            return null;
        }
        List<BeanDefinitionProducer> candidates = producersByDefinitionName(current).get(definitionClass.getName());
        if (candidates == null) {
            return null;
        }
        for (BeanDefinitionProducer producer : candidates) {
            // The name is only a cheap filter: two references can carry the same name across class loaders,
            // so keep looking until one of them loads as the requested class
            BeanDefinition<T> definition = producer.getDefinitionIfEnabled(beanContext, null, beanResolutionCustomizer, null, null, null);
            if (definition != null && definition.getClass() == definitionClass) {
                return definition;
            }
        }
        return null;
    }

    private Map<String, List<BeanDefinitionProducer>> producersByDefinitionName(Beans current) {
        ProducersByDefinitionName cached = producersByDefinitionName;
        if (cached != null && cached.beans == current) {
            return cached.index;
        }
        Map<String, List<BeanDefinitionProducer>> index = new HashMap<>();
        indexByDefinitionName(index, current.all);
        // A proxied bean is disabled in the main list and its target is kept apart, see createBeans
        indexByDefinitionName(index, current.proxyTargetBeans);
        producersByDefinitionName = new ProducersByDefinitionName(current, index);
        return index;
    }

    private static void indexByDefinitionName(Map<String, List<BeanDefinitionProducer>> index, List<BeanDefinitionProducer> producers) {
        for (BeanDefinitionProducer producer : producers) {
            BeanDefinitionReference<?> reference = producer.reference;
            if (reference != null) {
                index.computeIfAbsent(reference.getBeanDefinitionName(), name -> new ArrayList<>(1)).add(producer);
            }
        }
    }

    @Override
    public <B> Iterable<BeanDefinition<B>> getBeanDefinitions(BeanResolutionContext beanResolutionContext,
                                                              Argument<B> beanType,
                                                              @Nullable Predicate<BeanDefinitionReference<B>> refPredicate,
                                                              @Nullable Predicate<BeanDefinition<B>> defPredicate) {
        return getBeanDefinitions(resolveProducersForBeanType(beanType), null, beanResolutionContext, beanType, refPredicate, defPredicate);
    }

    private <B> Iterable<BeanDefinition<B>> getBeanDefinitions(Collection<BeanDefinitionProducer> producers,
                                                               BeanContext beanContext,
                                                               Argument<B> beanType,
                                                               @Nullable Predicate<BeanDefinitionReference<B>> refPredicate,
                                                               @Nullable Predicate<BeanDefinition<B>> defPredicate) {
        return getBeanDefinitions(producers, beanContext, null, beanType, refPredicate, defPredicate);
    }

    private <B> Iterable<BeanDefinition<B>> getBeanDefinitions(Collection<BeanDefinitionProducer> producers,
                                                               @Nullable
                                                               BeanContext beanContext,
                                                               @Nullable
                                                               BeanResolutionContext beanResolutionContext,
                                                               Argument<B> beanType,
                                                               @Nullable Predicate<BeanDefinitionReference<B>> refPredicate,
                                                               @Nullable Predicate<BeanDefinition<B>> defPredicate) {
        return new Iterable<>() {

            @Override
            public Iterator<BeanDefinition<B>> iterator() {
                Iterator<BeanDefinitionProducer> iterator = producers.iterator();
                return new Iterator<>() {

                    @Nullable
                    private BeanDefinition<B> next;

                    private void advance() {
                        while (next == null && iterator.hasNext()) {
                            next = iterator.next().getDefinitionIfEnabled(
                                beanResolutionContext != null ? beanResolutionContext.getContext() : Objects.requireNonNull(beanContext, "Bean context is required"),
                                beanResolutionContext,
                                beanResolutionCustomizer,
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
                Iterator<BeanDefinitionProducer> iterator = Objects.requireNonNull(beans).all.iterator();
                return new Iterator<>() {

                    @Nullable
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
        Beans beansNotNull = Objects.requireNonNull(beans);
        if (type == Object.class) {
            return beansNotNull.all;
        }
        Map<String, List<BeanDefinitionProducer>> nameIndex = beansNotNull.nameIndex;
        List<BeanDefinitionProducer> producers = nameIndex != null ? nameIndex.get(type.getName()) : beansNotNull.beanIndex.get(type);
        if (producers == null) {
            return List.of();
        }
        return producers;
    }

    @Override
    public void reset() {
        if (lazy) {
            Object warmingUp = runningWarmUp;
            runningWarmUp = null;
            // the provided references, which also have the ones a condition dropped since, and which a stopped
            // context that is still referenced must not keep
            List<BeanDefinitionReference<?>> provided = providedReferences;
            providedReferences = null;
            LazyBeans.stop(warmingUp, provided != null ? provided : getBeanReferences());
            Objects.requireNonNull(deferredDisabledBeans).clear();
        }
        beans = null;
        producersByDefinitionName = null;
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
    @Override
    public void removeBeanDefinition(RuntimeBeanDefinition<?> definition) {
        Class<?> beanType = definition.getBeanType();
        for (Class<?> indexedType : Objects.requireNonNull(beans).beanIndex.keySet()) {
            if (indexedType == beanType || indexedType.isAssignableFrom(beanType)) {
                resolveTypeIndex(indexedType).forEach(p -> p.disableIfMatch(definition));
                break;
            }
        }
        beans.all.forEach(p -> p.disableIfMatch(definition));
    }

    @Override
    public void trackDisabled(QualifiedBeanType<?> beanType, List<String> reasons) {
        if (lazy && LazyBeans.deferDisabled(Objects.requireNonNull(deferredDisabledBeans), beanType, reasons)) {
            return;
        }
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
        return getBeanDefinitions(Objects.requireNonNull(beans).eagerInitBeans, beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public Iterable<BeanDefinition<Object>> getProcessedBeans(BeanContext beanContext) {
        return getBeanDefinitions(Objects.requireNonNull(beans).processedBeans, beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public Iterable<BeanDefinition<Object>> getParallelBeans(BeanContext beanContext) {
        return getBeanDefinitions(Objects.requireNonNull(beans).parallelBeans, beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public Iterable<BeanDefinition<Object>> getTargetProxyBeans(BeanContext beanContext) {
        return getBeanDefinitions(Objects.requireNonNull(beans).proxyTargetBeans, beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public Iterable<BeanDefinition<Object>> getTargetProxyBeans(BeanContext beanContext, Argument<?> beanType) {
        if (!lazy) {
            return getTargetProxyBeans(beanContext);
        }
        // only the targets a lookup of the type finds by name are loaded
        return getBeanDefinitions(LazyBeans.foundBy(Objects.requireNonNull(beans).proxyTargetBeans, beanType.getType().getName()), beanContext, Argument.OBJECT_ARGUMENT, null, null);
    }

    @Override
    public List<DisabledBean<?>> getDisabledBeans(BeanContext beanContext) {
        List<DisabledBean<?>> disabled = disabledBeans.stream()
            .map(producer -> (DisabledBean<?>) producer.reference)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (lazy) {
            disabled.addAll(LazyBeans.disabledBeans(Objects.requireNonNull(deferredDisabledBeans), null));
        }
        return disabled;
    }

    @Override
    public List<DisabledBean<?>> getDisabledBeans(BeanContext beanContext, Argument<?> beanType) {
        if (!lazy) {
            return getDisabledBeans(beanContext);
        }
        List<DisabledBean<?>> disabled = disabledBeans.stream()
            .map(producer -> (DisabledBean<?>) producer.reference)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        // only the ones a lookup of the type can find are built
        disabled.addAll(LazyBeans.disabledBeans(Objects.requireNonNull(deferredDisabledBeans), beanType.getType()));
        return disabled;
    }

    @Override
    public void started(BeanContext beanContext) {
        if (lazy) {
            LazyBeans.started(providedReferences, getBeanReferences());
        }
    }

    private Collection<BeanDefinitionProducer> resolveTypeIndex(Class<?> indexedType) {
        Beans current = Objects.requireNonNull(beans);
        Map<String, List<BeanDefinitionProducer>> nameIndex = current.nameIndex;
        if (nameIndex != null) {
            return LazyBeans.typeIndex(nameIndex, indexedType);
        }
        return current.beanIndex.computeIfAbsent(indexedType, COMPUTE_INDEXES_FN);
    }

    @Override
    public boolean exists(Class<?> beanType) {
        if (lazy) {
            Beans current = beans;
            return current == null
                ? LazyBeans.exists(resolveBeanDefinitionReferences(), beanType)
                : LazyBeans.exists(Objects.requireNonNull(current.nameIndex).get(beanType.getName()));
        }
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
                    if (!configuration.isEnabled(beanContext) && isWithin(configuration, additionalBeanDefinition)) {
                        additionalBeanDefinition.disable();
                    }
                }
                for (BeanConfiguration additionalBeanConfiguration : additionalBeanConfigurations) {
                    if (!additionalBeanConfiguration.isEnabled(beanContext) && isWithin(additionalBeanConfiguration, additionalBeanDefinition)) {
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
                    if (isWithin(additionalBeanConfiguration, producer)) {
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


    /**
     * Whether the reference of a producer is within a configuration. In lazy mode, the default
     * {@link BeanConfiguration#isWithin(io.micronaut.inject.BeanDefinitionReference)} loads the bean type of a lazy
     * reference, and a reference whose bean type does not load is not present.
     */
    private boolean isWithin(BeanConfiguration configuration, BeanDefinitionProducer producer) {
        BeanDefinitionReference<?> reference = producer.reference;
        if (reference == null) {
            return false;
        }
        return lazy ? LazyBeans.isWithin(configuration, producer, reference) : configuration.isWithin(reference);
    }

    private boolean isEagerInit(BeanDefinitionReference<?> beanDefinitionReference) {
        return eagerInitSingletons && beanDefinitionReference.isSingleton() ||
            eagerInitStereotypesPresent && beanDefinitionReference.getAnnotationMetadata().hasDeclaredStereotype(eagerInitStereotypes);
    }

    private static Beans createEagerBeans(List<BeanDefinitionReference<?>> beanDefinitionReferences, List<BeanConfiguration> beanConfigurations) {
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
            if (beanDefinitionReference == null) {
                continue;
            }
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
            null,
            byConfiguration
        );
    }

    private static void indexBean(Map<Class<?>, List<BeanDefinitionProducer>> indexByType, BeanDefinitionProducer beanDefinitionProducer) {
        BeanDefinitionReference<?> reference = beanDefinitionProducer.reference;
        if (reference == null) {
            return;
        }
        Set<Class<?>> exposedTypes = reference.getExposedTypes();
        if (exposedTypes.isEmpty()) {
            // The reference must be compiled prior to v5, use reflection to find exposed types till it's recompiled
            exposedTypes = ReflectionUtils.getAllClassesInHierarchy(reference.getBeanType());
        }
        for (Class<?> indexedType : exposedTypes) {
            indexByType.computeIfAbsent(indexedType, COMPUTE_INDEXES_FN).add(beanDefinitionProducer);
        }
        // A bean can also be enumerated by the types it is @Indexed by, even ones it does not implement
        for (Class<?> indexedType : reference.getIndexes()) {
            if (!exposedTypes.contains(indexedType)) {
                indexByType.computeIfAbsent(indexedType, COMPUTE_INDEXES_FN).add(beanDefinitionProducer);
            }
        }
    }

    private static void indexDisabledBean(Map<Class<?>, List<BeanDefinitionProducer>> indexByType, BeanDefinitionReference<?> reference) {
        // For disabled beans we want to initialize the index collection, otherwise MISS will cause N search
        for (Class<?> indexedType : reference.getExposedTypes()) {
            indexByType.computeIfAbsent(indexedType, COMPUTE_INDEXES_FN);
        }
        for (Class<?> indexedType : reference.getIndexes()) {
            indexByType.computeIfAbsent(indexedType, COMPUTE_INDEXES_FN);
        }
    }

    private List<BeanDefinitionReference<?>> resolveBeanDefinitionReferences() {
        List<BeanDefinitionReference<?>> refs;
        if (lazy) {
            // the context's own provider gives lazy references when asked; a provider of the application decides itself
            refs = beanDefinitionReferencesProvider instanceof DefaultBeanDefinitionsProvider defaultProvider
                ? defaultProvider.provide(classLoader, true)
                : beanDefinitionReferencesProvider.provide(classLoader);
            providedReferences = refs;
        } else {
            refs = beanDefinitionReferencesProvider.provide(classLoader);
        }
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

    private record ProducersByDefinitionName(Beans beans, Map<String, List<BeanDefinitionProducer>> index) {
    }

    /**
     * The producers of the definitions.
     *
     * @param beanIndex The producers by the types they are found by, in eager mode
     * @param nameIndex The producers by the names of the types they are found by, in lazy mode, else {@code null}
     */
    private record Beans(
        List<BeanDefinitionProducer> eagerInitBeans,
        List<BeanDefinitionProducer> processedBeans,
        List<BeanDefinitionProducer> parallelBeans,
        List<BeanDefinitionProducer> all,
        List<BeanDefinitionProducer> proxyTargetBeans,
        Map<Class<?>, List<BeanDefinitionProducer>> beanIndex,
        @Nullable Map<String, List<BeanDefinitionProducer>> nameIndex,
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
    private static class BeanDefinitionProducer {
        static final AtomicReferenceFieldUpdater<BeanDefinitionProducer, Object> DEFINITION_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(BeanDefinitionProducer.class, Object.class, "definition");
        static final Object DEFINITION_DISABLED_SENTINEL = "";

        /**
         * Initially the reference, may be set to {@code null} by {@link #disable()} or if
         * {@link #getReferenceIfEnabled} determines the ref is disabled.
         */
        @Nullable
        @SuppressWarnings("java:S3077")
        volatile BeanDefinitionReference<?> reference;
        /**
         * Initially {@code null}. If the {@link #reference} is enabled, and the definition from it
         * is also enabled, this is set to the {@link BeanDefinition}. If the definition is
         * disabled (either through conditions or explicitly by {@link #disable()}), this is set to
         * {@link #DEFINITION_DISABLED_SENTINEL}.
         */
        @Nullable
        @SuppressWarnings("java:S3077")
        volatile Object definition;

        BeanDefinitionProducer(BeanDefinitionReference<?> reference) {
            this.reference = reference;
        }

        private static boolean isReferenceEnabled(@Nullable BeanDefinitionReference<?> ref, BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            if (ref == null) {
                return false;
            }
            if (ref instanceof io.micronaut.context.AbstractInitializableBeanDefinitionAndReference<?> referenceAndDefinition) {
                return referenceAndDefinition.isEnabled(context, resolutionContext, true);
            }
            return ref.isEnabled(context);
        }

        static boolean isDefinitionEnabled(BeanContext context,
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
                // Only permanently disable if the context is running.
                // Before startup, property sources may not yet be processed
                // into the environment resolver, so condition evaluation
                // results are unreliable and should not be cached.
                if (context.isRunning()) {
                    this.reference = null;
                }
                return null;
            }
        }

        @Nullable
        <T> BeanDefinition<T> getDefinitionIfEnabled(BeanContext context,
                                                     @Nullable BeanResolutionContext resolutionContext,
                                                     BeanResolutionCustomizer beanResolutionCustomizer,
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
                if (beanType != null && !(beanType.getType().equals(Object.class) || beanResolutionCustomizer.isCandidateBean(beanType, def) || isIndexedBy(beanType))) {
                    return null;
                }
                if (defPredicate != null && !defPredicate.test(def)) {
                    return null;
                }
                return def;
            }
            BeanDefinitionReference<T> ref = getReferenceIfEnabled(context, resolutionContext);
            if (ref == null) {
                if (context.isRunning()) {
                    // shortcut for future calls
                    this.definition = DEFINITION_DISABLED_SENTINEL;
                }
                return null;
            }
            if (beanType != null && !(beanType.getType().equals(Object.class) || beanResolutionCustomizer.isCandidateBean(beanType, ref) || isIndexedBy(beanType))) {
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

        /**
         * Whether the bean is explicitly indexed by the requested type. A bean annotated with
         * {@code @Indexed(Marker.class)} is enumerable by {@code Marker} even when it does not implement it.
         * A bean that implements the type but does not expose it (for example {@code @Bean(typed = ...)})
         * is deliberately hidden from that type and is not enumerable by it.
         *
         * @param beanType The requested bean type
         * @return True if the bean declares an index for exactly the requested type and does not implement it
         */
        boolean isIndexedBy(Argument<?> beanType) {
            BeanDefinitionReference<?> ref = reference;
            if (ref == null) {
                return false;
            }
            Class<?> type = beanType.getType();
            for (Class<?> indexedType : ref.getIndexes()) {
                if (indexedType == type) {
                    return !type.isAssignableFrom(ref.getBeanType());
                }
            }
            return false;
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
    /**
     * A producer of a {@link LazyBeanDefinitionReference}. The steps with side effects keep the order eager mode
     * gives them: the conditions checked before loading, then, for a lookup of a type, the class of the definition is
     * instantiated without a context and the candidate check runs on it, then the reference predicate, then the
     * definition is loaded for the context, then the definition predicate and the conditions checked after loading.
     * The reference predicate is asked of the lazy reference first, which answers a lookup by a qualifier alone
     * without loading anything, and then, for the references it selects, of the instance, as eager mode asks it. A
     * reference whose definition cannot be linked disables the producer for good, as the eager read would have
     * dropped it.
     */
    private static final class LazyBeanDefinitionProducer extends BeanDefinitionProducer {

        private final LazyBeanDefinitionReference<?> lazy;

        LazyBeanDefinitionProducer(LazyBeanDefinitionReference<?> lazy) {
            super(lazy);
            this.lazy = lazy;
        }

        @Override
        @Nullable
        <T> BeanDefinitionReference<T> getReferenceIfEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            BeanDefinitionReference ref = reference;
            if (ref == null) {
                return null;
            }
            if (lazy.isPreLoadEnabled(context, resolutionContext)) {
                return ref;
            }
            // Only permanently disable if the context is running, as eager mode does
            if (context.isRunning()) {
                this.reference = null;
            }
            return null;
        }

        @Override
        @Nullable
        @SuppressWarnings({"unchecked", "rawtypes"})
        <T> BeanDefinition<T> getDefinitionIfEnabled(BeanContext context,
                                                     @Nullable BeanResolutionContext resolutionContext,
                                                     BeanResolutionCustomizer beanResolutionCustomizer,
                                                     @Nullable Argument<T> beanType,
                                                     @Nullable Predicate<BeanDefinitionReference<T>> refPredicate,
                                                     @Nullable Predicate<BeanDefinition<T>> defPredicate) {
            Object defObject = this.definition;
            if (defObject != DEFINITION_DISABLED_SENTINEL && defObject != null) {
                if (refPredicate != null) {
                    // eager mode asks the reference it holds, which for a compiled definition is the definition, not
                    // the descriptor
                    BeanDefinitionReference instance = reference == null ? null : lazy.instantiate();
                    if (instance == null || !refPredicate.test(instance)) {
                        return null;
                    }
                }
                return super.getDefinitionIfEnabled(context, resolutionContext, beanResolutionCustomizer, beanType, null, defPredicate);
            }
            BeanDefinitionReference<T> ref = getReferenceIfEnabled(context, resolutionContext);
            if (ref == null) {
                if (context.isRunning()) {
                    // shortcut for future calls
                    this.definition = DEFINITION_DISABLED_SENTINEL;
                }
                return null;
            }
            try {
                if (beanType != null && !beanType.getType().equals(Object.class)) {
                    // the instance eager mode checks, without the configuration load(context) gives it
                    BeanDefinitionReference instance = lazy.instantiate();
                    if (instance == null) {
                        disable();
                        return null;
                    }
                    if (!(beanResolutionCustomizer.isCandidateBean(beanType, instance) || isIndexedBy(beanType))) {
                        return null;
                    }
                }
                if (refPredicate != null) {
                    boolean described = refPredicate.test(ref);
                    // with the verification on, the definition is asked too, which finds a bean the descriptor leaves out
                    lazy.verifyPredicate(refPredicate, described);
                    if (!described) {
                        return null;
                    }
                    // the descriptor selected it: ask the instance, as eager mode does, which a definition whose
                    // static initializer failed answers differently
                    BeanDefinitionReference instance = lazy.instantiate();
                    if (instance == null) {
                        disable();
                        return null;
                    }
                    if (!refPredicate.test(instance)) {
                        return null;
                    }
                }
            } catch (NoClassDefFoundError e) {
                if (!lazy.isPresent()) {
                    // the bean type or the definition does not link, so the bean is not present
                    disable();
                    return null;
                }
                throw e;
            }
            BeanDefinition<T> def = ref.load(context);
            if (def == null) {
                disable();
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
    }

    /**
     * The steps of lazy mode, which the service calls only in lazy mode, so that this class and
     * {@link LazyBeanDefinitionReference} are never loaded otherwise.
     */
    private static final class LazyBeans {

        private static final Function<String, List<BeanDefinitionProducer>> COMPUTE_NAME_INDEXES_FN = new Function<String, List<BeanDefinitionProducer>>() {
            // Keep an anonymous class for performance
            @Override
            public List<BeanDefinitionProducer> apply(String indexedType) {
                return new ArrayList<>(20);
            }
        };

        private LazyBeans() {
        }

        /**
         * The producers indexed under the name of a type, created when there are none.
         */
        static Collection<BeanDefinitionProducer> typeIndex(Map<String, List<BeanDefinitionProducer>> nameIndex, Class<?> type) {
            return nameIndex.computeIfAbsent(type.getName(), COMPUTE_NAME_INDEXES_FN);
        }

        /**
         * The references of the producers that are present, as eager mode, which drops the ones that cannot be linked
         * when it reads them, lists them: a lazy reference is loaded for that, and its producer disabled when it is
         * not present.
         */
        @SuppressWarnings({"unchecked", "java:S1181"})
        static List<BeanDefinitionReference<Object>> presentReferences(List<BeanDefinitionProducer> all) {
            List<BeanDefinitionReference<Object>> references = new ArrayList<>(all.size());
            for (BeanDefinitionProducer producer : all) {
                BeanDefinitionReference<?> reference = producer.reference;
                if (reference instanceof LazyBeanDefinitionReference<?> lazy) {
                    BeanDefinitionReference<?> instance;
                    try {
                        instance = lazy.instantiate();
                    } catch (Throwable e) {
                        // present, with a failure the lookup that needs it meets, as it would have failed the start
                        instance = lazy;
                    }
                    if (instance == null) {
                        producer.disable();
                        continue;
                    }
                }
                if (reference != null) {
                    references.add((BeanDefinitionReference<Object>) reference);
                }
            }
            return references;
        }

        static BeanDefinitionProducer producer(BeanDefinitionReference<?> reference) {
            return reference instanceof LazyBeanDefinitionReference<?> lazy ? new LazyBeanDefinitionProducer(lazy) : new BeanDefinitionProducer(reference);
        }

        /**
         * The producers of lazy mode: as in eager mode, but indexed by the names of the types, which a lazy
         * reference answers without loading a class.
         */
        static Beans createBeans(List<BeanDefinitionReference<?>> beanDefinitionReferences, List<BeanConfiguration> beanConfigurations) {
            List<BeanDefinitionProducer> eagerInitBeans = new ArrayList<>(20);
            List<BeanDefinitionProducer> processedBeans = new ArrayList<>(10);
            List<BeanDefinitionProducer> parallelBeans = new ArrayList<>(10);

            Map<String, List<BeanDefinitionProducer>> indexByName = CollectionUtils.newHashMap(beanDefinitionReferences.size());
            List<BeanDefinitionProducer> all = new ArrayList<>(beanDefinitionReferences.size());
            List<BeanDefinitionProducer> proxyTargetBeans = new ArrayList<>(beanDefinitionReferences.size());
            for (BeanDefinitionReference<?> beanDefinitionReference : beanDefinitionReferences) {
                all.add(producer(beanDefinitionReference));
            }

            List<Map.Entry<BeanConfiguration, List<BeanDefinitionProducer>>> byConfiguration = new ArrayList<>(beanConfigurations.size());
            for (BeanConfiguration beanConfiguration : beanConfigurations) {
                byConfiguration.add(Map.entry(beanConfiguration, new ArrayList<>()));
            }

            for (BeanDefinitionProducer beanDefinitionProducer : all) {
                BeanDefinitionReference<?> beanDefinitionReference = beanDefinitionProducer.reference;
                if (beanDefinitionReference == null) {
                    continue;
                }
                for (Map.Entry<BeanConfiguration, List<BeanDefinitionProducer>> e : byConfiguration) {
                    if (isWithin(e.getKey(), beanDefinitionProducer, beanDefinitionReference)) {
                        e.getValue().add(beanDefinitionProducer);
                    }
                }
                if (beanDefinitionReference.isProxiedBean()) {
                    beanDefinitionProducer.disable();
                    indexNames(indexByName, beanDefinitionReference, null);
                    // retain only if proxy target otherwise the target is never used
                    if (beanDefinitionReference.isProxyTarget()) {
                        proxyTargetBeans.add(producer(beanDefinitionReference));
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
                indexNames(indexByName, beanDefinitionReference, beanDefinitionProducer);
            }

            return new Beans(
                eagerInitBeans,
                processedBeans,
                parallelBeans,
                new CopyOnWriteArrayList<>(all),
                proxyTargetBeans,
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(indexByName),
                byConfiguration
            );
        }

        /**
         * Indexes a producer under the names of the types a lookup finds it by: for a lazy reference, the exposed
         * types and the indexes the descriptor names; for an eager one, the names of the types eager mode indexes it
         * by. Without a producer, only creates the keys, as eager mode does for a disabled bean.
         */
        private static void indexNames(Map<String, List<BeanDefinitionProducer>> indexByName,
                                       BeanDefinitionReference<?> reference,
                                       @Nullable BeanDefinitionProducer producer) {
            if (reference instanceof LazyBeanDefinitionReference<?> lazy) {
                String[] exposed = lazy.exposedTypeNames();
                for (String type : exposed) {
                    index(indexByName, type, producer);
                }
                for (String type : lazy.indexNames()) {
                    if (!lazy.isExposedAs(type)) {
                        index(indexByName, type, producer);
                    }
                }
                return;
            }
            Set<Class<?>> exposedTypes = reference.getExposedTypes();
            if (exposedTypes.isEmpty() && producer != null) {
                // The reference must be compiled prior to v5, use reflection to find exposed types till it's recompiled
                exposedTypes = ReflectionUtils.getAllClassesInHierarchy(reference.getBeanType());
            }
            for (Class<?> type : exposedTypes) {
                index(indexByName, type.getName(), producer);
            }
            for (Class<?> type : reference.getIndexes()) {
                if (!exposedTypes.contains(type)) {
                    index(indexByName, type.getName(), producer);
                }
            }
        }

        private static void index(Map<String, List<BeanDefinitionProducer>> indexByName, String type, @Nullable BeanDefinitionProducer producer) {
            List<BeanDefinitionProducer> producers = indexByName.computeIfAbsent(type, COMPUTE_NAME_INDEXES_FN);
            if (producer != null) {
                producers.add(producer);
            }
        }

        static boolean isWithin(BeanConfiguration configuration, BeanDefinitionProducer producer, BeanDefinitionReference<?> reference) {
            try {
                return configuration.isWithin(reference);
            } catch (NoClassDefFoundError e) {
                if (reference instanceof LazyBeanDefinitionReference<?> lazy && !lazy.isPresent()) {
                    // the default reads the bean type, which does not load: the bean is not present
                    producer.disable();
                    return false;
                }
                throw e;
            }
        }

        /**
         * Whether one of the producers a lookup by name found holds a reference that links, as eager mode, which
         * only has references that do, answers.
         */
        static boolean exists(@Nullable List<BeanDefinitionProducer> producers) {
            if (producers == null) {
                return false;
            }
            for (BeanDefinitionProducer producer : producers) {
                BeanDefinitionReference<?> reference = producer.reference;
                if (reference != null && (!(reference instanceof LazyBeanDefinitionReference<?> lazy) || lazy.links())) {
                    return true;
                }
            }
            return false;
        }

        static boolean exists(List<BeanDefinitionReference<?>> references, Class<?> beanType) {
            for (BeanDefinitionReference<?> reference : references) {
                if (reference instanceof LazyBeanDefinitionReference<?> lazy) {
                    if (lazy.isFoundBy(beanType.getName()) && lazy.links()) {
                        return true;
                    }
                } else if (beanType.isAssignableFrom(reference.getBeanType())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The producers of a list that a lookup of the type finds by name, and every eager one.
         */
        static List<BeanDefinitionProducer> foundBy(List<BeanDefinitionProducer> producers, String typeName) {
            List<BeanDefinitionProducer> found = new ArrayList<>(producers.size());
            for (BeanDefinitionProducer producer : producers) {
                if (!(producer.reference instanceof LazyBeanDefinitionReference<?> lazy) || lazy.isFoundBy(typeName)) {
                    found.add(producer);
                }
            }
            return found;
        }

        /**
         * Keeps a bean of a lazy reference that its conditions disable, to be built when the disabled beans are read:
         * building it loads its bean type at least.
         */
        static boolean deferDisabled(List<Object> deferred, QualifiedBeanType<?> beanType, List<String> reasons) {
            if (beanType instanceof LazyBeanDefinitionReference<?> lazy) {
                deferred.add(new DeferredDisabledBean(lazy, reasons));
                return true;
            }
            return false;
        }

        /**
         * Builds the disabled beans of lazy references.
         *
         * @param deferred The beans kept by {@link #deferDisabled(List, QualifiedBeanType, List)}
         * @param type     The type a lookup asked for, to build only the ones it can find, or {@code null}
         * @return The disabled beans that could be built
         */
        static List<DisabledBean<?>> disabledBeans(List<Object> deferred, @Nullable Class<?> type) {
            List<DisabledBean<?>> disabled = new ArrayList<>(deferred.size());
            for (Object entry : deferred) {
                DeferredDisabledBean bean = (DeferredDisabledBean) entry;
                if (type == null || bean.reference.isFoundBy(type.getName()) || isAssignable(type, bean.reference)) {
                    DisabledBean<?> built = bean.build();
                    if (built != null) {
                        disabled.add(built);
                    }
                }
            }
            return disabled;
        }

        /**
         * Whether the bean type is a subtype of the type: eager mode lists a disabled bean for such a lookup, as a
         * disabled bean exposes no type of its own. Only an error path asks, and building the entry loads the bean type
         * anyway.
         */
        @SuppressWarnings("java:S1181")
        private static boolean isAssignable(Class<?> type, LazyBeanDefinitionReference<?> reference) {
            try {
                return type.isAssignableFrom(reference.getBeanType());
            } catch (LinkageError e) {
                return false;
            }
        }

        /**
         * Starts the warm-up, when the common pool has more than one thread, on threads of its own, as many as the pool
         * has, so that the pool stays free for the work the context and the application give it as they start.
         *
         * @param beans The producers
         * @param all   Whether to also load every other reference whose conditions are only of classes that are present
         * @return The warm-up, to cancel
         */
        static Object warmUp(Beans beans, boolean all) {
            Map<BeanDefinitionReference<?>, Boolean> ordered = new IdentityHashMap<>();
            List<LazyBeanDefinitionReference<?>> first = new ArrayList<>();
            // the beans the context creates or processes as it starts, and the infrastructure it looks up first
            addLazy(first, ordered, beans.eagerInitBeans);
            addLazy(first, ordered, beans.parallelBeans);
            addLazy(first, ordered, beans.processedBeans);
            Map<String, List<BeanDefinitionProducer>> nameIndex = Objects.requireNonNull(beans.nameIndex);
            for (Class<?> type : KNOWN_INDEX_TYPES) {
                List<BeanDefinitionProducer> producers = nameIndex.get(type.getName());
                if (producers != null) {
                    addLazy(first, ordered, producers);
                }
            }
            // then, when asked to, every other one whose conditions are of classes that load
            List<LazyBeanDefinitionReference<?>> rest = new ArrayList<>();
            if (all) {
                addLazy(rest, ordered, beans.all);
            }
            WarmUp warmUp = new WarmUp(first, rest);
            int threads = Math.min(ForkJoinPool.getCommonPoolParallelism(), first.size() + rest.size());
            for (int i = 0; i < threads; i++) {
                Thread.ofPlatform().daemon().name("micronaut-lazy-warm-up-" + i).start(warmUp);
            }
            return warmUp;
        }

        private static void addLazy(List<LazyBeanDefinitionReference<?>> list, Map<BeanDefinitionReference<?>, Boolean> added, List<BeanDefinitionProducer> producers) {
            for (BeanDefinitionProducer producer : producers) {
                if (producer.reference instanceof LazyBeanDefinitionReference<?> lazy && added.put(lazy, Boolean.TRUE) == null) {
                    list.add(lazy);
                }
            }
        }

        /**
         * Logs, once the context has started, how many definitions were loaded.
         */
        static void started(@Nullable List<BeanDefinitionReference<?>> provided, List<BeanDefinitionReference<Object>> references) {
            LazyBeanDefinitionReference.started(provided, references);
        }

        /**
         * Stops the warm-up, and with the verification on, loads and compares every definition no lookup needed.
         */
        static void stop(@Nullable Object warmUp, Collection<? extends BeanDefinitionReference<?>> references) {
            if (warmUp instanceof WarmUp running) {
                running.cancelled = true;
            }
            LazyBeanDefinitionReference.stopped(references);
        }
    }

    /**
     * A bean of a lazy reference that its conditions disabled, built once when it is read.
     */
    private static final class DeferredDisabledBean {

        final LazyBeanDefinitionReference<?> reference;
        final List<String> reasons;
        @Nullable
        private volatile Object built;

        DeferredDisabledBean(LazyBeanDefinitionReference<?> reference, List<String> reasons) {
            this.reference = reference;
            this.reasons = reasons;
        }

        @Nullable
        @SuppressWarnings({"unchecked", "rawtypes", "java:S1181"})
        DisabledBean<?> build() {
            Object current = built;
            if (current == null) {
                try {
                    current = new DisabledBean(reference.disabledBeanType(), reference.getDeclaredQualifier(), reasons);
                } catch (LinkageError | RuntimeException e) {
                    // the maps built here are purely to aid error diagnosis, as in eager mode
                    current = Boolean.FALSE;
                }
                built = current;
            }
            return current instanceof DisabledBean<?> bean ? bean : null;
        }
    }

    /**
     * Instantiates lazy references on threads of its own, without a context, in the order given: the definitions the
     * context needs first, then, when asked to, every other one whose conditions are only of classes, which hold. The
     * threads share a cursor, so the order holds across them. A reference a lookup instantiated meanwhile is skipped.
     */
    private static final class WarmUp implements Runnable {

        private final LazyBeanDefinitionReference<?>[] references;
        private final int first;
        private final AtomicInteger cursor = new AtomicInteger();
        volatile boolean cancelled;

        WarmUp(List<LazyBeanDefinitionReference<?>> first, List<LazyBeanDefinitionReference<?>> rest) {
            this.references = new LazyBeanDefinitionReference<?>[first.size() + rest.size()];
            for (int i = 0; i < first.size(); i++) {
                references[i] = first.get(i);
            }
            for (int i = 0; i < rest.size(); i++) {
                references[first.size() + i] = rest.get(i);
            }
            this.first = first.size();
        }

        @Override
        @SuppressWarnings("java:S1181") // a failure is kept by the reference and met again by the lookup that needs it
        public void run() {
            int index;
            while (!cancelled && (index = cursor.getAndIncrement()) < references.length) {
                LazyBeanDefinitionReference<?> reference = references[index];
                try {
                    if (index < first || reference.isEnabledByClasses()) {
                        reference.instantiate();
                    }
                } catch (Throwable e) {
                    // kept by the reference
                }
            }
        }
    }
}
