/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Infrastructure;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.Failure;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.exceptions.BeanCreationException;
import io.micronaut.context.exceptions.BeanDestructionException;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.context.processor.AnnotationProcessor;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.context.scope.CustomScopeRegistry;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Indexes;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.type.UnsafeExecutable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.core.value.ValueResolver;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionMethodReference;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.InjectableBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.ParametrizedInstantiatableBeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.UnsafeExecutionHandle;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.provider.AbstractProviderDefinition;
import io.micronaut.inject.proxy.InterceptedBeanProxy;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualified;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.validation.BeanDefinitionValidator;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The default context implementations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("MagicNumber")
public class DefaultBeanContext implements InitializableBeanContext {

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultBeanContext.class);
    protected static final Logger LOG_LIFECYCLE = LoggerFactory.getLogger(DefaultBeanContext.class.getPackage().getName() + ".lifecycle");
    private static final String SCOPED_PROXY_ANN = "io.micronaut.runtime.context.scope.ScopedProxy";
    private static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction";
    private static final String ADAPTER_TYPE = "io.micronaut.aop.Adapter";
    private static final String PARALLEL_TYPE = Parallel.class.getName();
    private static final String INDEXES_TYPE = Indexes.class.getName();
    private static final String REPLACES_ANN = Replaces.class.getName();
    private static final Comparator<BeanRegistration<?>> BEAN_REGISTRATION_COMPARATOR = (o1, o2) -> {
        int order1 = OrderUtil.getOrder(o1.getBeanDefinition(), o1.getBean());
        int order2 = OrderUtil.getOrder(o2.getBeanDefinition(), o2.getBean());
        return Integer.compare(order1, order2);
    };
    private static final String MSG_COULD_NOT_BE_LOADED = "] could not be loaded: ";
    public static final String MSG_BEAN_DEFINITION = "Bean definition [";


    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean initializing = new AtomicBoolean(false);
    protected final AtomicBoolean terminating = new AtomicBoolean(false);

    final Map<BeanIdentifier, BeanRegistration<?>> singlesInCreation = new ConcurrentHashMap<>(5);

    private final SingletonScope singletonScope = new SingletonScope();

    private final BeanContextConfiguration beanContextConfiguration;

    // The collection should be modified only when new bean definition is added
    // That shouldn't happen that often, so we can use CopyOnWriteArrayList
    private final Collection<BeanDefinitionProducer> beanDefinitionsClasses = new CopyOnWriteArrayList<>();
    private final Collection<BeanDefinitionProducer> proxyTargetBeans = new CopyOnWriteArrayList<>();

    private final Map<BeanKey<?>, BeanDefinitionProducer> disabledBeans = new ConcurrentHashMap<>(20);
    private final Map<String, List<String>> disabledConfigurations = new ConcurrentHashMap<>(5);
    private final Map<String, BeanConfiguration> beanConfigurations = new HashMap<>(10);
    private final Map<BeanKey, Boolean> containsBeanCache = new ConcurrentHashMap<>(30);
    private final Map<CharSequence, Object> attributes = Collections.synchronizedMap(new HashMap<>(5));

    private final Map<BeanKey, CollectionHolder> singletonBeanRegistrations = new ConcurrentHashMap<>(50);

    private final Map<BeanCandidateKey, Optional<BeanDefinition>> beanConcreteCandidateCache =
            new ConcurrentLinkedHashMap.Builder<BeanCandidateKey, Optional<BeanDefinition>>().maximumWeightedCapacity(30).build();

    private final Map<BeanCandidateKey, Optional<BeanDefinition>> beanProxyTargetCache =
        new ConcurrentLinkedHashMap.Builder<BeanCandidateKey, Optional<BeanDefinition>>().maximumWeightedCapacity(30).build();

    private final Map<Argument, Collection<BeanDefinition>> beanCandidateCache = new ConcurrentLinkedHashMap.Builder<Argument, Collection<BeanDefinition>>().maximumWeightedCapacity(30).build();

    private final Map<Class<?>, Collection<BeanDefinitionProducer>> beanIndex = new ConcurrentHashMap<>(12);

    private final ClassLoader classLoader;
    private final Set<Class<?>> thisInterfaces = CollectionUtils.setOf(
            BeanDefinitionRegistry.class,
            BeanContext.class,
            AnnotationMetadataResolver.class,
            BeanLocator.class,
            ExecutionHandleLocator.class,
            ApplicationContext.class,
            PropertyResolver.class,
            ValueResolver.class,
            PropertyPlaceholderResolver.class
    );
    private final Set<Class<?>> indexedTypes = CollectionUtils.setOf(
            ResourceLoader.class,
            TypeConverter.class,
            TypeConverterRegistrar.class,
            ApplicationEventListener.class,
            BeanCreatedEventListener.class,
            BeanInitializedEventListener.class
    );
    private final CustomScopeRegistry customScopeRegistry;
    private final String[] eagerInitStereotypes;
    private final boolean eagerInitStereotypesPresent;
    private final boolean eagerInitSingletons;

    private BeanDefinitionValidator beanValidator;
    private List<BeanDefinitionReference> beanDefinitionReferences;
    private List<BeanConfiguration> beanConfigurationsList;

    List<Map.Entry<Class<?>, ListenersSupplier<BeanInitializedEventListener>>> beanInitializedEventListeners;
    private List<Map.Entry<Class<?>, ListenersSupplier<BeanCreatedEventListener>>> beanCreationEventListeners;
    private List<Map.Entry<Class<?>, ListenersSupplier<BeanPreDestroyEventListener>>> beanPreDestroyEventListeners;
    private List<Map.Entry<Class<?>, ListenersSupplier<BeanDestroyedEventListener>>> beanDestroyedEventListeners;

    @Nullable
    private MutableConversionService conversionService;

    /**
     * Construct a new bean context using the same classloader that loaded this DefaultBeanContext class.
     */
    public DefaultBeanContext() {
        this(BeanContext.class.getClassLoader());
    }

    /**
     * Construct a new bean context with the given class loader.
     *
     * @param classLoader The class loader
     */
    public DefaultBeanContext(@NonNull ClassLoader classLoader) {
        this(new BeanContextConfiguration() {
            @NonNull
            @Override
            public ClassLoader getClassLoader() {
                ArgumentUtils.requireNonNull("classLoader", classLoader);
                return classLoader;
            }
        });
    }

    /**
     * Construct a new bean context with the given class loader.
     *
     * @param resourceLoader The resource loader
     */
    public DefaultBeanContext(@NonNull ClassPathResourceLoader resourceLoader) {
        this(new BeanContextConfiguration() {
            @NonNull
            @Override
            public ClassLoader getClassLoader() {
                ArgumentUtils.requireNonNull("resourceLoader", resourceLoader);
                return resourceLoader.getClassLoader();
            }
        });
    }

    /**
     * Creates a new bean context with the given configuration.
     *
     * @param contextConfiguration The context configuration
     */
    public DefaultBeanContext(@NonNull BeanContextConfiguration contextConfiguration) {
        ArgumentUtils.requireNonNull("contextConfiguration", contextConfiguration);
        // enable classloader logging
        System.setProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING, "true");
        this.classLoader = contextConfiguration.getClassLoader();
        this.customScopeRegistry = Objects.requireNonNull(createCustomScopeRegistry(), "Scope registry cannot be null");
        Set<Class<? extends Annotation>> eagerInitAnnotated = contextConfiguration.getEagerInitAnnotated();
        List<String> configuredEagerSingletonAnnotations = new ArrayList<>(eagerInitAnnotated.size());
        for (Class<? extends Annotation> ann : eagerInitAnnotated) {
            configuredEagerSingletonAnnotations.add(ann.getName());
        }
        this.eagerInitStereotypes = configuredEagerSingletonAnnotations.toArray(new String[0]);
        this.eagerInitStereotypesPresent = !configuredEagerSingletonAnnotations.isEmpty();
        this.eagerInitSingletons = eagerInitStereotypesPresent && (configuredEagerSingletonAnnotations.contains(AnnotationUtil.SINGLETON) || configuredEagerSingletonAnnotations.contains(Singleton.class.getName()));
        this.beanContextConfiguration = contextConfiguration;
    }

    /**
     * Allows customizing the custom scope registry.
     *
     * @return The custom scope registry to use.
     * @since 3.0.0
     */
    @NonNull
    protected CustomScopeRegistry createCustomScopeRegistry() {
        return new DefaultCustomScopeRegistry(this);
    }

    /**
     * @return The custom scope registry
     */
    @Internal
    @NonNull
    CustomScopeRegistry getCustomScopeRegistry() {
        return customScopeRegistry;
    }

    @Override
    public boolean isRunning() {
        return running.get() && !initializing.get();
    }

    /**
     * The start method will read all bean definition classes found on the classpath and initialize any pre-required
     * state.
     */
    @Override
    public synchronized BeanContext start() {
        if (!isRunning()) {

            if (initializing.compareAndSet(false, true)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Starting BeanContext");
                }
                registerConversionService();
                finalizeConfiguration();
                if (LOG.isDebugEnabled()) {
                    String activeConfigurations = beanConfigurations
                            .values()
                            .stream()
                            .filter(config -> config.isEnabled(this))
                            .map(BeanConfiguration::getName)
                            .collect(Collectors.joining(","));
                    if (StringUtils.isNotEmpty(activeConfigurations)) {
                        LOG.debug("Loaded active configurations: {}", activeConfigurations);
                    }
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("BeanContext Started.");
                }
                publishEvent(new StartupEvent(this));
            }
            running.set(true);
            initializing.set(false);
        }
        return this;
    }

    /**
     * Registers conversion service.
     */
    protected void registerConversionService() {
        conversionService = MutableConversionService.create();
        //noinspection resource
        registerSingleton(MutableConversionService.class, conversionService,  null, false);
    }

    /**
     * Tracks when a bean or configuration is disabled.
     * @param conditionContext The conditional context
     * @param <C> The component type
     */
    @Internal
    <C extends AnnotationMetadataProvider> void trackDisabledComponent(@NonNull ConditionContext<C> conditionContext) {
        C component = conditionContext.getComponent();
        List<String> reasons = conditionContext.getFailures().stream().map(Failure::getMessage).toList();
        if (component instanceof QualifiedBeanType<?> beanType) {
            try {
                @SuppressWarnings("unchecked")
                Argument<Object> argument = (Argument<Object>) beanType.getGenericBeanType();
                @SuppressWarnings("unchecked")
                Qualifier<Object> declaredQualifier = (Qualifier<Object>) beanType.getDeclaredQualifier();
                this.disabledBeans.put(new BeanKey<>(argument, declaredQualifier), new BeanDefinitionProducer(new DisabledBean<>(
                    argument,
                    declaredQualifier,
                    reasons
                )));
            } catch (Exception | NoClassDefFoundError e) {
                // it is theoretically possible that resolving the generic type results in an error
                // in this case just ignore this as the maps built here are purely to aid error diagnosis
            }
        } else if (component instanceof BeanConfiguration configuration) {
            this.disabledConfigurations.put(configuration.getName(), reasons);
        }
    }

    /**
     * The close method will shut down the context calling {@link jakarta.annotation.PreDestroy} hooks on loaded
     * singletons.
     */
    @Override
    public synchronized BeanContext stop() {
        if (terminating.compareAndSet(false, true) && isRunning()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping BeanContext");
            }
            publishEvent(new ShutdownEvent(this));
            attributes.clear();

            // need to sort registered singletons so that beans with that require other beans appear first
            List<BeanRegistration> objects = topologicalSort(singletonScope.getBeanRegistrations());

            Map<Boolean, List<BeanRegistration>> result = objects.stream().collect(Collectors.groupingBy(br -> br.bean != null
                    && (br.bean instanceof BeanPreDestroyEventListener || br.bean instanceof BeanDestroyedEventListener)));

            List<BeanRegistration> listeners = result.get(true);
            if (listeners != null) {
                // destroy all bean destroy listeners at the end
                objects.clear();
                objects.addAll(result.get(false));
                objects.addAll(listeners);
            }

            Set<Integer> processed = new HashSet<>();
            for (BeanRegistration beanRegistration : objects) {
                Object bean = beanRegistration.bean;
                int sysId = System.identityHashCode(bean);
                if (processed.contains(sysId)) {
                    continue;
                }

                if (LOG_LIFECYCLE.isDebugEnabled()) {
                    LOG_LIFECYCLE.debug("Destroying bean [{}] with identifier [{}]", bean, beanRegistration.identifier);
                }

                processed.add(sysId);
                try {
                    destroyBean(beanRegistration);
                } catch (BeanDestructionException e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }

            singlesInCreation.clear();
            singletonBeanRegistrations.clear();
            beanConcreteCandidateCache.clear();
            beanCandidateCache.clear();
            beanProxyTargetCache.clear();
            containsBeanCache.clear();
            beanConfigurations.clear();
            disabledConfigurations.clear();
            singletonScope.clear();
            beanDefinitionsClasses.clear();
            disabledBeans.clear();
            proxyTargetBeans.clear();
            attributes.clear();
            beanIndex.clear();
            beanConfigurationsList = null;
            beanDefinitionReferences = null;
            beanInitializedEventListeners = null;
            beanCreationEventListeners = null;
            beanPreDestroyEventListeners = null;
            beanDestroyedEventListeners = null;
            conversionService = null;
            terminating.set(false);
            running.set(false);
        }
        return this;
    }

    @Override
    @NonNull
    public AnnotationMetadata resolveMetadata(Class<?> type) {
        if (type == null) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        return findBeanDefinitionInternal(Argument.of(type), null)
                .map(AnnotationMetadataProvider::getAnnotationMetadata)
                .orElse(AnnotationMetadata.EMPTY_METADATA);
    }

    @Override
    public <T> Optional<T> refreshBean(@Nullable BeanIdentifier identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        BeanRegistration<T> beanRegistration = singletonScope.findBeanRegistration(identifier);
        if (beanRegistration != null) {
            refreshBean(beanRegistration);
            return Optional.of(beanRegistration.bean);
        }
        return Optional.empty();
    }

    @Override
    public <T> void refreshBean(@NonNull BeanRegistration<T> beanRegistration) {
        Objects.requireNonNull(beanRegistration, "BeanRegistration cannot be null");
        T bean = beanRegistration.bean;
        if (bean != null) {
            BeanDefinition<T> definition = beanRegistration.definition();
            if (definition instanceof InjectableBeanDefinition<T> injectableBeanDefinition) {
                injectableBeanDefinition.inject(this, bean);
            }
        }
    }

    @Override
    public Collection<BeanRegistration<?>> getActiveBeanRegistrations(Qualifier<?> qualifier) {
        if (qualifier == null) {
            return Collections.emptyList();
        }
        return singletonScope.getBeanRegistrations(qualifier);
    }

    @Override
    public <T> Collection<BeanRegistration<T>> getActiveBeanRegistrations(Class<T> beanType) {
        if (beanType == null) {
            return Collections.emptyList();
        }
        return singletonScope.getBeanRegistrations(beanType);
    }

    @Override
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(Class<T> beanType) {
        if (beanType == null) {
            return Collections.emptyList();
        }
        return getBeanRegistrations(null, Argument.of(beanType), null);
    }

    @Override
    public <T> BeanRegistration<T> getBeanRegistration(Class<T> beanType, Qualifier<T> qualifier) {
        return getBeanRegistration(null, Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(Class<T> beanType, Qualifier<T> qualifier) {
        if (beanType == null) {
            return Collections.emptyList();
        }
        return getBeanRegistrations(null, Argument.of(beanType), null);
    }

    @Override
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(Argument<T> beanType, Qualifier<T> qualifier) {
        return getBeanRegistrations(
                null,
                Objects.requireNonNull(beanType, "Bean type cannot be null"),
                qualifier
        );
    }

    @Override
    public <T> BeanRegistration<T> getBeanRegistration(Argument<T> beanType, Qualifier<T> qualifier) {
        return getBeanRegistration(
                null,
                Objects.requireNonNull(beanType, "Bean type cannot be null"),
                qualifier
        );
    }

    @Override
    public <T> BeanRegistration<T> getBeanRegistration(BeanDefinition<T> beanDefinition) {
        return resolveBeanRegistration(null, beanDefinition);
    }

    @Override
    public <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean) {
        if (bean == null) {
            return Optional.empty();
        }
        BeanRegistration<T> beanRegistration = singletonScope.findBeanRegistration(bean);
        if (beanRegistration != null) {
            return Optional.of(beanRegistration);
        }
        return customScopeRegistry.findBeanRegistration(bean);
    }

    @Override
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(Class<T> beanType, String method, Class<?>... arguments) {
        return findExecutionHandle(beanType, null, method, arguments);
    }

    @Override
    public MethodExecutionHandle<?, Object> createExecutionHandle(BeanDefinition<?> beanDefinition, ExecutableMethod<Object, ?> method) {
        if (method instanceof UnsafeExecutable<?,?>) {
            return new BeanContextUnsafeExecutionHandle(method, beanDefinition, (UnsafeExecutable<Object, Object>) method);
        }
        return new BeanContextExecutionHandle(method, beanDefinition);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(Class<T> beanType, Qualifier<?> q, String method, Class<?>... arguments) {
        Qualifier<T> qualifier = (Qualifier<T>) q;
        Optional<BeanDefinition<T>> foundBean = findBeanDefinition(beanType, qualifier);
        if (foundBean.isEmpty()) {
            return Optional.empty();
        }
        BeanDefinition<T> beanDefinition = foundBean.get();
        Optional<ExecutableMethod<T, R>> foundMethod = beanDefinition.findMethod(method, arguments);
        if (foundMethod.isEmpty()) {
            foundMethod = beanDefinition.<R>findPossibleMethods(method)
                .findFirst()
                .filter(m -> {
                    Class<?>[] argTypes = m.getArgumentTypes();
                    if (argTypes.length == arguments.length) {
                        for (int i = 0; i < argTypes.length; i++) {
                            if (!arguments[i].isAssignableFrom(argTypes[i])) {
                                return false;
                            }
                        }
                        return true;
                    }
                    return false;
                });
        }
        return foundMethod.map(executableMethod -> new BeanExecutionHandle<>(this, beanType, qualifier, executableMethod));
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findExecutableMethod(Class<T> beanType, String method, Class<?>[] arguments) {
        if (beanType == null) {
            return Optional.empty();
        }
        Collection<BeanDefinition<T>> definitions = getBeanDefinitions(beanType);
        if (definitions.isEmpty()) {
            return Optional.empty();
        }
        BeanDefinition<T> beanDefinition = definitions.iterator().next();
        Optional<ExecutableMethod<T, R>> foundMethod = beanDefinition.findMethod(method, arguments);
        if (foundMethod.isPresent()) {
            return foundMethod;
        }
        return beanDefinition.<R>findPossibleMethods(method).findFirst();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(T bean, String method, Class<?>[] arguments) {
        if (bean != null) {
            Optional<? extends BeanDefinition<?>> foundBean = findBeanDefinition(bean.getClass());
            if (foundBean.isPresent()) {
                BeanDefinition<?> beanDefinition = foundBean.get();
                Optional<? extends ExecutableMethod<?, Object>> foundMethod = beanDefinition.findMethod(method, arguments);
                if (foundMethod.isPresent()) {
                    return foundMethod.map((ExecutableMethod executableMethod) -> new ObjectExecutionHandle<>(bean, executableMethod));
                } else {
                    return beanDefinition.findPossibleMethods(method)
                            .findFirst()
                            .map((ExecutableMethod executableMethod) -> new ObjectExecutionHandle<>(bean, executableMethod));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public <T> BeanContext registerSingleton(@NonNull Class<T> type, @NonNull T singleton, Qualifier<T> qualifier, boolean inject) {
        purgeCacheForBeanInstance(singleton);

        BeanDefinition<T> beanDefinition;
        if (inject && running.get()) {
            // Bean cannot be injected before the start of the context
            beanDefinition = findConcreteCandidate(null, Argument.of(type), qualifier, false).orElse(null);
            if (beanDefinition == null) {
                // Purge cache miss
                purgeCacheForBeanInstance(singleton);
            }
        } else {
            beanDefinition = null;
        }
        if (beanDefinition != null && !(beanDefinition instanceof RuntimeBeanDefinition<T>) && beanDefinition.getBeanType().isInstance(singleton)) {
            try (BeanResolutionContext context = newResolutionContext(beanDefinition, null)) {
                if (inject) {
                    doInjectAndInitialize(context, singleton, beanDefinition);
                }
                DefaultBeanContext.BeanKey<T> key = new DefaultBeanContext.BeanKey<>(beanDefinition.asArgument(), qualifier);
                singletonScope.registerSingletonBean(BeanRegistration.of(this, key, beanDefinition, singleton), qualifier);
            }
        } else {
            RuntimeBeanDefinition<T> runtimeBeanDefinition = RuntimeBeanDefinition.builder(type, () -> singleton)
                .singleton(true)
                .qualifier(qualifier)
                .build();

            var registration = BeanRegistration.of(
                this,
                new BeanKey<>(runtimeBeanDefinition, qualifier),
                runtimeBeanDefinition,
                singleton
            );
            singletonScope.registerSingletonBean(registration, qualifier);
            registerBeanDefinition(runtimeBeanDefinition);
        }
        return this;
    }

    private <T> void purgeCacheForBeanInstance(T singleton) {
        beanCandidateCache.entrySet().removeIf(entry -> entry.getKey().isInstance(singleton));
        beanConcreteCandidateCache.entrySet().removeIf(entry -> entry.getKey().beanType.isInstance(singleton));
        singletonBeanRegistrations.entrySet().removeIf(entry -> entry.getKey().beanType.isInstance(singleton));
        containsBeanCache.entrySet().removeIf(entry -> entry.getKey().beanType.isInstance(singleton));
    }

    @NonNull
    final BeanResolutionContext newResolutionContext(BeanDefinition<?> beanDefinition, @Nullable BeanResolutionContext currentContext) {
        if (currentContext == null) {
            return new SingletonBeanResolutionContext(beanDefinition);
        } else {
            return currentContext;
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public BeanDefinitionValidator getBeanValidator() {
        if (beanValidator == null) {
            this.beanValidator = findBean(BeanDefinitionValidator.class).orElse(BeanDefinitionValidator.DEFAULT);
        }
        return beanValidator;
    }

    @Override
    public Optional<BeanConfiguration> findBeanConfiguration(String configurationName) {
        BeanConfiguration configuration = this.beanConfigurations.get(configurationName);
        if (configuration != null) {
            return Optional.of(configuration);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public <T> BeanDefinition<T> getBeanDefinition(Argument<T> beanType, Qualifier<T> qualifier) {
        return findBeanDefinition(beanType, qualifier)
                .orElseThrow(() -> newNoSuchBeanException(null, beanType, qualifier, null));
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Argument<T> beanType, Qualifier<T> qualifier) {
        BeanDefinition<T> beanDefinition = singletonScope.findCachedSingletonBeanDefinition(beanType, qualifier);
        if (beanDefinition != null) {
            return Optional.of(beanDefinition);
        }
        return findConcreteCandidate(null, beanType, qualifier, true);
    }

    private <T> Optional<BeanDefinition<T>> findBeanDefinitionInternal(Argument<T> beanType, Qualifier<T> qualifier) {
        return findConcreteCandidate(null, beanType, qualifier, false);
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType, Qualifier<T> qualifier) {
        return findBeanDefinition(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType) {
        return getBeanDefinitions(Argument.of(beanType));
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> beanType) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(null, beanType);
        return Collections.unmodifiableCollection(candidates);
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType, Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeanDefinitions(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> beanType, Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(null, beanType);
        if (qualifier != null) {
            candidates = qualifier.reduce(beanType.getType(), candidates.stream()).toList();
        }
        return Collections.unmodifiableCollection(candidates);
    }

    @Override
    public <T> boolean containsBean(@NonNull Class<T> beanType, Qualifier<T> qualifier) {
        return containsBean(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> boolean containsBean(Argument<T> beanType, Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);
        if (containsBeanCache.containsKey(beanKey)) {
            return containsBeanCache.get(beanKey);
        } else {
            boolean result = singletonScope.containsBean(beanType, qualifier) ||
                    isCandidatePresent(beanKey.beanType, qualifier);

            containsBeanCache.put(beanKey, result);
            return result;
        }
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBean(Argument.of(beanType), qualifier);
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull Class<T> beanType) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBean(Argument.of(beanType), null);
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        try {
            return getBean(null, beanType, qualifier);
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.ConditionLog.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.ConditionLog.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getSimpleName(), e.getMessage());
            }
            throw newNoSuchBeanException(
                null,
                beanType,
                qualifier,
                "Bean of type [" + beanType.getTypeString(true) + "] disabled for reason: " + e.getMessage()
            );
        }
    }

    @Override
    public <T> Optional<T> findBean(Class<T> beanType, Qualifier<T> qualifier) {
        return findBean(null, beanType, qualifier);
    }

    @Override
    public <T> Optional<T> findBean(Argument<T> beanType, Qualifier<T> qualifier) {
        return findBean(null, beanType, qualifier);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType) {
        return getBeansOfType(null, Argument.of(beanType));
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType, Qualifier<T> qualifier) {
        return getBeansOfType(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Argument<T> beanType) {
        return getBeansOfType(null, beanType);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Argument<T> beanType, Qualifier<T> qualifier) {
        return getBeansOfType(null, beanType, qualifier);
    }

    @Override
    public <T> Stream<T> streamOfType(Class<T> beanType, Qualifier<T> qualifier) {
        return streamOfType(null, beanType, qualifier);
    }

    @Override
    public <T> Stream<T> streamOfType(Argument<T> beanType, Qualifier<T> qualifier) {
        return streamOfType(null, beanType, qualifier);
    }

    @Override
    public <V> Map<String, V> mapOfType(Argument<V> beanType, Qualifier<V> qualifier) {
        return mapOfType(null, beanType, qualifier);
    }

    /**
     * Obtains a stream of beans of the given type and qualifier.
     *
     * @param resolutionContext The bean resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean concrete type
     * @return A stream
     */
    protected <T> Stream<T> streamOfType(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        return streamOfType(resolutionContext, Argument.of(beanType), qualifier);
    }

    /**
     * Obtains a map of beans of the given type and qualifier.
     * @param resolutionContext  The resolution context
     * @param beanType           The bean type
     * @param qualifier          The qualifier
     * @param <V>                The bean type
     * @return A map of beans, never {@code null}.
     * @since 4.0.0
     */
    protected <V> @NonNull Map<String, V> mapOfType(@Nullable BeanResolutionContext resolutionContext, @NonNull Argument<V> beanType, @Nullable Qualifier<V> qualifier) {
        // try and find a bean that implements the map with the generics
        Argument<Map<String, V>> mapType = Argument.mapOf(Argument.of(String.class), beanType);
        @SuppressWarnings("unchecked") Qualifier<Map<String, V>> mapQualifier = (Qualifier<Map<String, V>>) qualifier;
        BeanDefinition<Map<String, V>> existingBean = findBeanDefinitionInternal(mapType, mapQualifier).orElse(null);
        if (existingBean != null) {
            return getBean(existingBean);
        } else {
            Collection<BeanRegistration<V>> beanRegistrations = getBeanRegistrations(resolutionContext, beanType, qualifier);
            if (beanRegistrations.isEmpty()) {
                return Collections.emptyMap();
            } else {
                try {
                    return beanRegistrations.stream().collect(Collectors.toUnmodifiableMap(
                        DefaultBeanContext::resolveKey,
                        reg -> reg.bean
                    ));
                } catch (IllegalStateException e) { // occurs for duplicate keys
                    List<BeanDefinition<V>> beanDefinitions = beanRegistrations.stream().map(reg -> reg.beanDefinition).toList();
                    throw new DependencyInjectionException(
                        resolutionContext,
                        "Injecting a map of beans requires each bean to define a qualifier. Multiple beans were found missing a qualifier resulting in duplicate keys: " + e.getMessage(),
                        new NonUniqueBeanException(
                            beanType.getType(),
                            beanDefinitions.iterator()
                        )
                    );
                }
            }
        }
    }

    @NonNull
    private static String resolveKey(BeanRegistration<?> reg) {
        BeanDefinition<?> definition = reg.beanDefinition;
        BeanIdentifier identifier = reg.identifier;
        if (definition instanceof NameResolver resolver) {
            return resolver.resolveName().orElse(identifier.getName());
        } else {
            String name = identifier.getName();
            if (name.equals(Primary.SIMPLE_NAME)) {
                Class<?> candidateType = reg.beanDefinition.getBeanType();
                String candidateSimpleName = candidateType.getSimpleName();
                return NameUtils.decapitalize(candidateSimpleName);
            } else {
                return name;
            }
        }
    }

    /**
     * Obtains a stream of beans of the given type and qualifier.
     *
     * @param resolutionContext The bean resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean concrete type
     * @return A stream
     */
    @Internal
    public <T> Stream<T> streamOfType(BeanResolutionContext resolutionContext, Argument<T> beanType, Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeanRegistrations(resolutionContext, beanType, qualifier).stream()
                .map(BeanRegistration::getBean);
    }

    @NonNull
    @Override
    public <T> T inject(@NonNull T instance) {
        Objects.requireNonNull(instance, "Instance cannot be null");

        Collection<BeanDefinition<T>> candidates = findBeanCandidatesForInstance(instance);
        BeanDefinition<T> beanDefinition;
        if (candidates.size() == 1) {
            beanDefinition = candidates.iterator().next();
        } else if (!candidates.isEmpty()) {
            Argument t = Argument.of(instance.getClass());
            beanDefinition = lastChanceResolve(t, null, true, (Collection) candidates);
        } else {
            beanDefinition = null;
        }

        if (beanDefinition != null && !(beanDefinition instanceof RuntimeBeanDefinition<T>)) {
            try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
                final BeanKey<T> beanKey = new BeanKey<>(beanDefinition.getBeanType(), null);
                resolutionContext.addInFlightBean(
                    beanKey,
                    new BeanRegistration<>(beanKey, beanDefinition, instance)
                );
                doInjectAndInitialize(
                    resolutionContext,
                    instance,
                    beanDefinition
                );
            }
        }
        return instance;

    }

    @NonNull
    @Override
    public <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return createBean(null, beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Map<String, Object> argumentValues) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        Optional<BeanDefinition<T>> candidate = findBeanDefinition(Argument.of(beanType), qualifier);
        if (candidate.isPresent()) {
            try (BeanResolutionContext resolutionContext = newResolutionContext(candidate.get(), null)) {
                return doCreateBean(resolutionContext, candidate.get(), qualifier, argumentValues);
            }
        }
        throw newNoSuchBeanException(
            null,
            Argument.of(beanType),
            qualifier,
            null
        );
    }

    @NonNull
    @Override
    public <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Object... args) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        final Argument<T> beanArg = Argument.of(beanType);
        Optional<BeanDefinition<T>> candidate = findBeanDefinition(beanArg, qualifier);
        if (candidate.isPresent()) {
            BeanDefinition<T> definition = candidate.get();
            try (BeanResolutionContext resolutionContext = newResolutionContext(definition, null)) {
                return doCreateBean(resolutionContext, definition, qualifier, args);
            }
        }
        throw newNoSuchBeanException(
            null,
            Argument.of(beanType),
            qualifier,
            null
        );
    }

    @NonNull
    private <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                               @NonNull BeanDefinition<T> definition,
                               @Nullable Qualifier<T> qualifier,
                               @Nullable Object... args) {
        Map<String, Object> argumentValues = resolveArgumentValues(resolutionContext, definition, args);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Computed bean argument values: {}", argumentValues);
        }
        return doCreateBean(resolutionContext, definition, qualifier, argumentValues);
    }

    @NonNull
    private <T> Map<String, Object> resolveArgumentValues(BeanResolutionContext resolutionContext, BeanDefinition<T> definition, Object[] args) {
        Argument[] requiredArguments;
        if (definition instanceof ParametrizedInstantiatableBeanDefinition<T> parametrizedInstantiatableBeanDefinition) {
            requiredArguments = parametrizedInstantiatableBeanDefinition.getRequiredArguments();
        } else {
            return null;
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating bean for parameters: {}", ArrayUtils.toString(args));
        }
        MutableConversionService conversionService = getConversionService();
        Map<String, Object> argumentValues = CollectionUtils.newLinkedHashMap(requiredArguments.length);
        BeanResolutionContext.Path currentPath = resolutionContext.getPath();
        for (int i = 0; i < requiredArguments.length; i++) {
            Argument<?> requiredArgument = requiredArguments[i];
            try (BeanResolutionContext.Path ignored = currentPath.pushConstructorResolve(definition, requiredArgument)) {
                Class<?> argumentType = requiredArgument.getType();
                if (args.length > i) {
                    Object val = args[i];
                    if (val != null) {
                        if (argumentType.isInstance(val) && !CollectionUtils.isIterableOrMap(argumentType)) {
                            argumentValues.put(requiredArgument.getName(), val);
                        } else {
                            argumentValues.put(requiredArgument.getName(), conversionService.convert(val, requiredArgument).orElseThrow(() ->
                                    new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. Cannot convert object [" + val + "] to required type: " + argumentType)
                            ));
                        }
                    } else if (!requiredArgument.isDeclaredNullable()) {
                        throw new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. Argument cannot be null");
                    }
                } else {
                    // attempt resolve from context
                    Optional<?> existingBean = findBean(resolutionContext, argumentType, null);
                    if (existingBean.isPresent()) {
                        argumentValues.put(requiredArgument.getName(), existingBean.get());
                    } else if (!requiredArgument.isDeclaredNullable()) {
                        throw new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. No bean found for type: " + argumentType);
                    }
                }
            }
        }
        return argumentValues;
    }

    @Nullable
    @Override
    public <T> T destroyBean(@NonNull Argument<T> beanType, Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return findBeanDefinition(beanType, qualifier)
                .map(this::destroyBean)
                .orElse(null);
    }

    @Override
    @NonNull
    public <T> T destroyBean(@NonNull T bean) {
        ArgumentUtils.requireNonNull("bean", bean);
        Optional<BeanRegistration<T>> beanRegistration = findBeanRegistration(bean);
        if (beanRegistration.isPresent()) {
            destroyBean(beanRegistration.get());
        } else {
            Optional<BeanDefinition<T>> beanDefinition = findBeanDefinition((Class<T>) bean.getClass());
            if (beanDefinition.isPresent()) {
                BeanDefinition<T> definition = beanDefinition.get();
                BeanKey<T> key = new BeanKey<>(definition, definition.getDeclaredQualifier());
                destroyBean(BeanRegistration.of(this, key, definition, bean));
            }
        }
        return bean;
    }

    @Override
    @Nullable
    public <T> T destroyBean(@NonNull Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return destroyBean(Argument.of(beanType), null);
    }

    @Nullable
    private <T> T destroyBean(@NonNull BeanDefinition<T> beanDefinition) {
        if (beanDefinition.isSingleton()) {
            BeanRegistration<T> beanRegistration = singletonScope.findBeanRegistration(beanDefinition);
            if (beanRegistration != null) {
                destroyBean(beanRegistration);
                return beanRegistration.bean;
            }
        }
        throw new IllegalArgumentException("Cannot destroy non-singleton bean using bean definition! Use 'destroyBean(BeanRegistration)` or `destroyBean(<BeanInstance>)`.");
    }

    @Override
    public <T> void destroyBean(@NonNull BeanRegistration<T> registration) {
        destroyBean(registration, false);
    }

    private <T> void destroyBean(@NonNull BeanRegistration<T> registration, boolean dependent) {
        if (LOG_LIFECYCLE.isDebugEnabled()) {
            LOG_LIFECYCLE.debug("Destroying bean [{}] with identifier [{}]", registration.bean, registration.identifier);
        }
        if (registration.beanDefinition instanceof ProxyBeanDefinition) {
            if (registration.bean instanceof InterceptedBeanProxy) {
                // Ignore the proxy and destroy the target
                destroyProxyTargetBean(registration, dependent);
                return;
            }
            if (dependent && registration.beanDefinition.isSingleton()) {
                return;
            }
        }
        T beanToDestroy = registration.getBean();
        BeanDefinition<T> definition = registration.getBeanDefinition();
        if (beanToDestroy != null) {
            purgeCacheForBeanInstance(beanToDestroy);
            if (definition.isSingleton()) {
                singletonScope.purgeCacheForBeanInstance(definition, beanToDestroy);
            }
        }
        beanToDestroy = triggerPreDestroyListeners(definition, beanToDestroy);

        if (definition instanceof DisposableBeanDefinition) {
            try {
                ((DisposableBeanDefinition<T>) definition).dispose(this, beanToDestroy);
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error disposing bean [" + beanToDestroy + "]... Continuing...", e);
                }
            }
        }
        if (beanToDestroy instanceof LifeCycle cycle) {
            try {
                cycle.stop();
            } catch (Exception e) {
                throw new BeanDestructionException(definition, e);
            }
        }
        if (registration instanceof BeanDisposingRegistration) {
            List<BeanRegistration<?>> dependents = ((BeanDisposingRegistration<T>) registration).getDependents();
            if (CollectionUtils.isNotEmpty(dependents)) {
                final ListIterator<BeanRegistration<?>> i = dependents.listIterator(dependents.size());
                while (i.hasPrevious()) {
                    destroyBean(i.previous(), true);
                }
            }
        } else {
            try {
                registration.close();
            } catch (Exception e) {
                throw new BeanDestructionException(definition, e);
            }
        }

        triggerBeanDestroyedListeners(definition, beanToDestroy);
    }

    @NonNull
    private <T> T triggerPreDestroyListeners(@NonNull BeanDefinition<T> beanDefinition, @NonNull T bean) {
        if (beanPreDestroyEventListeners == null) {
            beanPreDestroyEventListeners = loadListeners(BeanPreDestroyEventListener.class);
        }
        if (!beanPreDestroyEventListeners.isEmpty()) {
            Class<T> beanType = getBeanType(beanDefinition);
            for (Map.Entry<Class<?>, ListenersSupplier<BeanPreDestroyEventListener>> entry : beanPreDestroyEventListeners) {
                if (entry.getKey().isAssignableFrom(beanType)) {
                    final BeanPreDestroyEvent<T> event = new BeanPreDestroyEvent<>(this, beanDefinition, bean);
                    for (BeanPreDestroyEventListener<T> listener : entry.getValue().get(null)) {
                        try {
                            bean = Objects.requireNonNull(
                                    listener.onPreDestroy(event),
                                    "PreDestroy event listener illegally returned null: " + listener.getClass()
                            );
                        } catch (Exception e) {
                            throw new BeanDestructionException(beanDefinition, e);
                        }
                    }

                }
            }
        }
        return bean;
    }

    private <T> void destroyProxyTargetBean(@NonNull BeanRegistration<T> registration, boolean dependent) {
        Set<Object> destroyed = Collections.emptySet();
        if (registration instanceof BeanDisposingRegistration<?> disposingRegistration) {
            if (disposingRegistration.getDependents() != null) {
                destroyed = Collections.newSetFromMap(new IdentityHashMap<>());
                for (BeanRegistration<?> beanRegistration : disposingRegistration.getDependents()) {
                    destroyBean(beanRegistration, true);
                    destroyed.add(beanRegistration.bean);
                }
            }
        }
        BeanDefinition<T> proxyTargetBeanDefinition = findProxyTargetBeanDefinition(registration.beanDefinition)
                .orElseThrow(() -> new IllegalStateException("Cannot find a proxy target bean definition for: " + registration.beanDefinition));
        Optional<CustomScope<?>> declaredScope = customScopeRegistry.findDeclaredScope(proxyTargetBeanDefinition);
        if (declaredScope.isEmpty()) {
            if (proxyTargetBeanDefinition.isSingleton()) {
                return;
            }
            // Scope is not present, try to get the actual target bean and destroy it
            if (registration.bean instanceof InterceptedBeanProxy) {
                InterceptedBeanProxy<T> interceptedProxy = (InterceptedBeanProxy<T>) registration.bean;
                if (interceptedProxy.hasCachedInterceptedTarget()) {
                    T interceptedTarget = interceptedProxy.interceptedTarget();
                    if (destroyed.contains(interceptedTarget)) {
                        return;
                    }
                    destroyBean(BeanRegistration.of(this,
                            new BeanKey<>(proxyTargetBeanDefinition, proxyTargetBeanDefinition.getDeclaredQualifier()),
                            proxyTargetBeanDefinition,
                            interceptedTarget,
                            registration instanceof BeanDisposingRegistration ? ((BeanDisposingRegistration<T>) registration).getDependents() : null
                    ));
                }
            }
            return;
        }
        CustomScope<?> customScope = declaredScope.get();
        if (dependent) {
            return;
        }
        Optional<BeanRegistration<T>> targetBeanRegistration = customScope.findBeanRegistration(proxyTargetBeanDefinition);
        if (targetBeanRegistration.isPresent()) {
            BeanRegistration<T> targetRegistration = targetBeanRegistration.get();
            customScope.remove(targetRegistration.identifier);
        }
    }

    @NonNull
    private <T> void triggerBeanDestroyedListeners(@NonNull BeanDefinition<T> beanDefinition, @NonNull T bean) {
        if (beanDestroyedEventListeners == null) {
            beanDestroyedEventListeners = loadListeners(BeanDestroyedEventListener.class);
        }
        if (!beanDestroyedEventListeners.isEmpty()) {
            Class<T> beanType = getBeanType(beanDefinition);
            for (Map.Entry<Class<?>, ListenersSupplier<BeanDestroyedEventListener>> entry : beanDestroyedEventListeners) {
                if (entry.getKey().isAssignableFrom(beanType)) {
                    final BeanDestroyedEvent<T> event = new BeanDestroyedEvent<>(this, beanDefinition, bean);
                    for (BeanDestroyedEventListener<T> listener : entry.getValue().get(null)) {
                        try {
                            listener.onDestroyed(event);
                        } catch (Exception e) {
                            throw new BeanDestructionException(beanDefinition, e);
                        }
                    }
                }
            }
        }
    }

    @NonNull
    private <T> Class<T> getBeanType(@NonNull BeanDefinition<T> beanDefinition) {
        if (beanDefinition instanceof ProxyBeanDefinition) {
            return ((ProxyBeanDefinition<T>) beanDefinition).getTargetType();
        }
        return beanDefinition.getBeanType();
    }

    /**
     * Find an active singleton bean for the given definition and qualifier.
     *
     * @param beanDefinition The bean definition
     * @param qualifier      The qualifier
     * @param <T>            The bean generic type
     * @return The bean registration
     */
    @Nullable
    protected <T> BeanRegistration<T> getActiveBeanRegistration(BeanDefinition<T> beanDefinition, Qualifier qualifier) {
        if (beanDefinition == null) {
            return null;
        }
        return singletonScope.findBeanRegistration(beanDefinition, qualifier);
    }

    /**
     * Creates a bean.
     *
     * @param resolutionContext The bean resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean generic type
     * @return The instance
     */
    @NonNull
    protected <T> T createBean(@Nullable BeanResolutionContext resolutionContext,
                               @NonNull Class<T> beanType,
                               @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);

        Optional<BeanDefinition<T>> concreteCandidate = findBeanDefinition(beanType, qualifier);
        if (concreteCandidate.isPresent()) {
            BeanDefinition<T> candidate = concreteCandidate.get();
            try (BeanResolutionContext context = newResolutionContext(candidate, resolutionContext)) {
                return doCreateBean(context, candidate, qualifier);
            }
        }
        throw newNoSuchBeanException(
            resolutionContext,
            Argument.of(beanType),
            qualifier,
            null
        );
    }

    /**
     * Injects a bean.
     *
     * @param resolutionContext        The bean resolution context
     * @param requestingBeanDefinition The requesting bean definition
     * @param instance                 The instance
     * @param <T>                      The instance type
     * @return The instance
     */
    @Internal
    @NonNull
    protected <T> T inject(@NonNull BeanResolutionContext resolutionContext,
                           @Nullable BeanDefinition<?> requestingBeanDefinition,
                           @NonNull T instance) {
        @SuppressWarnings("unchecked") Class<T> beanType = (Class<T>) instance.getClass();
        Optional<BeanDefinition<T>> concreteCandidate = findBeanDefinition(beanType, null);
        if (concreteCandidate.isPresent()) {
            BeanDefinition<T> definition = concreteCandidate.get();
            if (requestingBeanDefinition != null && requestingBeanDefinition.equals(definition)) {
                // bail out, don't inject for bean definition in creation
                return instance;
            }
            doInjectAndInitialize(resolutionContext, instance, definition);
        }
        return instance;
    }

    /**
     * Get all beans of the given type.
     *
     * @param resolutionContext The bean resolution context
     * @param beanType          The bean type
     * @param <T>               The bean type parameter
     * @return The found beans
     */
    @NonNull
    protected <T> Collection<T> getBeansOfType(@Nullable BeanResolutionContext resolutionContext, @NonNull Argument<T> beanType) {
        return getBeansOfType(resolutionContext, beanType, null);
    }

    /**
     * Get all beans of the given type and qualifier.
     *
     * @param resolutionContext The bean resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found beans
     */
    @Internal
    @NonNull
    public <T> Collection<T> getBeansOfType(@Nullable BeanResolutionContext resolutionContext,
                                            @NonNull Argument<T> beanType,
                                            @Nullable Qualifier<T> qualifier) {
        Collection<BeanRegistration<T>> beanRegistrations = getBeanRegistrations(resolutionContext, beanType, qualifier);
        List<T> list = new ArrayList<>(beanRegistrations.size());
        for (BeanRegistration<T> beanRegistration : beanRegistrations) {
            list.add(beanRegistration.getBean());
        }
        return list;
    }

    @Override
    @NonNull
    public <T> T getProxyTargetBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getProxyTargetBean(null, Argument.of(beanType), qualifier);
    }

    @NonNull
    @Override
    public <T> T getProxyTargetBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getProxyTargetBean(null, beanType, qualifier);
    }

    /**
     * Resolves the proxy target for a given bean type. If the bean has no proxy then the original bean is returned.
     *
     * @param resolutionContext The bean resolution context
     * @param beanType          The bean type
     * @param qualifier         The bean qualifier
     * @param <T>               The generic type
     * @return The proxied instance
     * @since 3.1.0
     */
    @NonNull
    @UsedByGeneratedCode
    public <T> T getProxyTargetBean(@Nullable BeanResolutionContext resolutionContext,
                                    @NonNull Argument<T> beanType,
                                    @Nullable Qualifier<T> qualifier) {
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return resolveBeanRegistration(resolutionContext, definition, beanType, qualifier).bean;
    }

    @NonNull
    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(@NonNull Class<T> beanType, @NonNull String method, @NonNull Class<?>[] arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, null);
        return definition.findMethod(method, arguments);
    }

    @NonNull
    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(@NonNull Class<T> beanType, Qualifier<T> qualifier, @NonNull String method, Class<?>... arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return definition.findMethod(method, arguments);
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(@NonNull Argument<T> beanType, Qualifier<T> qualifier, @NonNull String method, Class<?>... arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return definition.findMethod(method, arguments);
    }

    @NonNull
    @Override
    public <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findProxyTargetBeanDefinition(Argument.of(beanType), qualifier);
    }

    @Override
    @SuppressWarnings("java:S2789") // performance optimization
    public <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        BeanCandidateKey<T> key = new BeanCandidateKey<>(beanType, qualifier, true);

        Optional beanDefinition = beanProxyTargetCache.get(key);
        if (beanDefinition == null) {
            beanDefinition = findProxyTargetNoCache(null, beanType, qualifier);
            beanProxyTargetCache.put(key, beanDefinition);
        }
        return beanDefinition;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public Collection<BeanDefinition<?>> getBeanDefinitions(@Nullable Qualifier<Object> qualifier) {
        if (qualifier == null) {
            return Collections.emptyList();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for qualifier: {}", qualifier);
        }
        // first traverse component definition classes and load candidates
        Collection candidates;
        if (!beanDefinitionsClasses.isEmpty()) {
            Stream<BeanDefinitionReference> reduced = qualifier.reduce(Object.class, beanDefinitionsClasses.stream()
                    .filter(p -> p.isReferenceEnabled(this))
                    .map(BeanDefinitionProducer::getReference));
            Stream<BeanDefinition> candidateStream = qualifier.reduce(Object.class,
                    reduced
                            .map(ref -> ref.load(this))
                            .filter(candidate -> candidate.isEnabled(this))
            );
            candidates = candidateStream.collect(Collectors.toList());

        } else {
            return Collections.emptyList();
        }
        if (CollectionUtils.isNotEmpty(candidates)) {
            filterReplacedBeans(null, candidates);
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public Collection<BeanDefinition<?>> getAllBeanDefinitions() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding all bean definitions");
        }

        if (!beanDefinitionsClasses.isEmpty()) {
            return beanDefinitionsClasses
                    .stream()
                    .filter(p -> p.isDefinitionEnabled(this))
                    .map(p -> p.getDefinition(this))
                    .collect(Collectors.toList());
        }

        return (Collection<BeanDefinition<?>>) Collections.emptyMap();
    }

    @Override
    public Collection<DisabledBean<?>> getDisabledBeans() {
        return disabledBeans.values().stream()
            .map(producer -> (DisabledBean<?>) producer.reference)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public Collection<BeanDefinitionReference<?>> getBeanDefinitionReferences() {
        if (!beanDefinitionsClasses.isEmpty()) {
            final List refs = beanDefinitionsClasses.stream()
                    .filter(p -> p.isReferenceEnabled(this))
                    .map(BeanDefinitionProducer::getReference)
                    .toList();

            return refs;
        }
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public <B> BeanContext registerBeanDefinition(@NonNull RuntimeBeanDefinition<B> definition) {
        Objects.requireNonNull(definition, "Bean definition cannot be null");
        Class<B> beanType = definition.getBeanType();
        BeanDefinitionProducer producer = new BeanDefinitionProducer(definition);
        this.beanDefinitionsClasses.add(producer);
        for (Class<?> indexedType : indexedTypes) {
            if (indexedType == beanType || indexedType.isAssignableFrom(beanType)) {
                final Collection<BeanDefinitionProducer> indexed = resolveTypeIndex(indexedType);
                indexed.add(producer);
                break;
            }
        }
        purgeCacheForBeanType(beanType);
        return this;
    }

    private <B> void purgeCacheForBeanType(Class<B> beanType) {
        beanCandidateCache.entrySet().removeIf(entry -> entry.getKey().isAssignableFrom(beanType));
        beanConcreteCandidateCache.entrySet().removeIf(entry -> entry.getKey().beanType.isAssignableFrom(beanType));
        singletonBeanRegistrations.entrySet().removeIf(entry -> entry.getKey().beanType.isAssignableFrom(beanType));
        containsBeanCache.entrySet().removeIf(entry -> entry.getKey().beanType.isAssignableFrom(beanType));
    }

    /**
     * The definition to remove.
     * @param definition The definition to remove
     * @param <B> The bean type
     */
    @Internal
    <B> void removeBeanDefinition(RuntimeBeanDefinition<B> definition) {
        Class<B> beanType = definition.getBeanType();
        for (Class<?> indexedType : indexedTypes) {
            if (indexedType == beanType || indexedType.isAssignableFrom(beanType)) {
                resolveTypeIndex(indexedType).forEach(p -> p.disable(definition));
                break;
            }
        }
        beanDefinitionsClasses.forEach(p -> p.disable(definition));
        purgeCacheForBeanType(definition.getBeanType());
    }

    /**
     * Get a bean of the given type.
     *
     * @param resolutionContext The bean context resolution
     * @param beanType          The bean type
     * @param <T>               The bean type parameter
     * @return The found bean
     */
    @UsedByGeneratedCode
    @NonNull
    public <T> T getBean(@Nullable BeanResolutionContext resolutionContext, @NonNull Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getBean(resolutionContext, Argument.of(beanType), null);
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull BeanDefinition<T> definition) {
        ArgumentUtils.requireNonNull("definition", definition);
        return resolveBeanRegistration(null, definition).bean;
    }

    /**
     * Get a bean of the given type and qualifier.
     *
     * @param resolutionContext The bean context resolution
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found bean
     */
    @NonNull
    public <T> T getBean(@Nullable BeanResolutionContext resolutionContext,
                         @NonNull Class<T> beanType,
                         @Nullable Qualifier<T> qualifier) {
        return getBean(resolutionContext, Argument.of(beanType), qualifier);
    }

    /**
     * Get a bean of the given type and qualifier.
     *
     * @param resolutionContext The bean context resolution
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found bean
     * @since 3.0.0
     */
    @NonNull
    public <T> T getBean(@Nullable BeanResolutionContext resolutionContext,
                         @NonNull Argument<T> beanType,
                         @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return resolveBeanRegistration(resolutionContext, beanType, qualifier, true).bean;
    }

    /**
     * Get a bean of the given bean definition, type and qualifier.
     *
     * @param resolutionContext The bean context resolution
     * @param beanDefinition    The bean definition
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found bean
     * @since 3.5.0
     */
    @Internal
    @NonNull
    public <T> T getBean(@Nullable BeanResolutionContext resolutionContext,
                         @NonNull BeanDefinition<T> beanDefinition,
                         @NonNull Argument<T> beanType,
                         @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanDefinition", beanDefinition);
        ArgumentUtils.requireNonNull("beanType", beanType);
        return resolveBeanRegistration(resolutionContext, beanDefinition, beanType, qualifier).bean;
    }

    /**
     * Find an optional bean of the given type and qualifier.
     *
     * @param resolutionContext The bean context resolution
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found bean wrapped as an {@link Optional}
     */
    @NonNull
    public <T> Optional<T> findBean(@Nullable BeanResolutionContext resolutionContext,
                                    @NonNull Class<T> beanType,
                                    @Nullable Qualifier<T> qualifier) {
        return findBean(resolutionContext, Argument.of(beanType), qualifier);
    }

    /**
     * Find an optional bean of the given type and qualifier.
     *
     * @param resolutionContext The bean context resolution
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The found bean wrapped as an {@link Optional}
     * @since 3.0.0
     */
    @Internal
    @NonNull
    public <T> Optional<T> findBean(@Nullable BeanResolutionContext resolutionContext,
                                    @NonNull Argument<T> beanType,
                                    @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        // allow injection the bean context
        if (thisInterfaces.contains(beanType.getType())) {
            return Optional.of((T) this);
        }

        try {
            BeanRegistration<T> beanRegistration = resolveBeanRegistration(resolutionContext, beanType, qualifier, false);
            if (beanRegistration == null || beanRegistration.bean == null) {
                return Optional.empty();
            } else {
                return Optional.of(beanRegistration.bean);
            }
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.ConditionLog.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.ConditionLog.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getSimpleName(), e.getMessage());
            }
            return Optional.empty();
        }
    }

    @Override
    public BeanContextConfiguration getContextConfiguration() {
        return this.beanContextConfiguration;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void publishEvent(@NonNull Object event) {
        if (event != null) {
            getBean(Argument.of(ApplicationEventPublisher.class, event.getClass())).publishEvent(event);
        }
    }

    @Override
    public @NonNull
    Future<Void> publishEventAsync(@NonNull Object event) {
        Objects.requireNonNull(event, "Event cannot be null");
        return getBean(Argument.of(ApplicationEventPublisher.class, event.getClass())).publishEventAsync(event);
    }

    @NonNull
    @Override
    public <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return findProxyBeanDefinition(Argument.of(beanType), qualifier);
    }

    @NonNull
    @Override
    public <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        for (BeanDefinition<T> beanDefinition : getBeanDefinitions(beanType, qualifier)) {
            if (beanDefinition.isProxy()) {
                return Optional.of(beanDefinition);
            }
        }
        return Optional.empty();
    }

    /**
     * Invalidates the bean caches. For testing only.
     */
    @Internal
    protected void invalidateCaches() {
        beanCandidateCache.clear();
        beanConcreteCandidateCache.clear();
        singletonBeanRegistrations.clear();
    }

    /**
     * Resolves the {@link BeanDefinitionReference} class instances. Default implementation uses ServiceLoader pattern.
     *
     * @return The bean definition classes
     */
    @NonNull
    protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        if (beanDefinitionReferences == null) {
            final SoftServiceLoader<BeanDefinitionReference> definitions = SoftServiceLoader.load(BeanDefinitionReference.class, classLoader);
            beanDefinitionReferences = new ArrayList<>(300);
            definitions.collectAll(beanDefinitionReferences, BeanDefinitionReference::isPresent);
        }
        return beanDefinitionReferences;
    }

    /**
     * Resolves the {@link BeanDefinitionReference} class instances. Default implementation uses ServiceLoader pattern.
     *
     * @param predicate The filter predicate, can be null
     * @return The bean definition classes
     */
    @NonNull
    @Deprecated
    protected List<BeanDefinitionReference> resolveBeanDefinitionReferences(@Nullable Predicate<BeanDefinitionReference> predicate) {
        if (predicate != null) {
            List<BeanDefinitionReference> allRefs = resolveBeanDefinitionReferences();
            List<BeanDefinitionReference> newRefs = new ArrayList<>(allRefs.size());
            for (BeanDefinitionReference reference : allRefs) {
                if (predicate.test(reference)) {
                    newRefs.add(reference);
                }
            }
            return newRefs;
        }
        return resolveBeanDefinitionReferences();
    }

    /**
     * Resolves the {@link BeanConfiguration} class instances. Default implementation uses ServiceLoader pattern.
     *
     * @return The bean definition classes
     */
    @NonNull
    protected Iterable<BeanConfiguration> resolveBeanConfigurations() {
        if (beanConfigurationsList == null) {
            final SoftServiceLoader<BeanConfiguration> definitions = SoftServiceLoader.load(BeanConfiguration.class, classLoader);
            beanConfigurationsList = new ArrayList<>(300);
            definitions.collectAll(beanConfigurationsList, null);
        }
        return beanConfigurationsList;
    }

    /**
     * Initialize the event listeners.
     */
    protected void initializeEventListeners() {
        this.beanCreationEventListeners = loadListeners(BeanCreatedEventListener.class);
        // Keep anonymous class to avoid lambda overhead during the startup
        this.beanCreationEventListeners.add(new AbstractMap.SimpleEntry<>(AnnotationProcessor.class, new ListenersSupplier<BeanCreatedEventListener>() {
            @Override
            public Iterable<BeanCreatedEventListener> get(BeanResolutionContext beanResolutionContext) {
                return Collections.singletonList(new AnnotationProcessorListener());
            }

        }));
        this.beanInitializedEventListeners = loadListeners(BeanInitializedEventListener.class);
    }

    @NonNull
    private <T extends EventListener> List<Map.Entry<Class<?>, ListenersSupplier<T>>> loadListeners(@NonNull Class<T> listenerType) {
        final Map<Class<?>, List<BeanDefinition<T>>> typeToListener = getTypeToListenerMap(listenerType);
        if (typeToListener.isEmpty()) {
            return new ArrayList<>(1);
        }
        List<Map.Entry<Class<?>, ListenersSupplier<T>>> eventToListeners = new ArrayList<>(typeToListener.size());
        for (Map.Entry<Class<?>, List<BeanDefinition<T>>> e : typeToListener.entrySet()) {
            eventToListeners.add(new AbstractMap.SimpleEntry<>(e.getKey(), new ListenersSupplier<>() {

                // The supplier can be triggered concurrently.
                // We allow for the listeners collection to be initialized multiple times.
                @SuppressWarnings("java:S3077")
                private volatile List<T> listeners;

                @Override
                public Iterable<T> get(BeanResolutionContext beanResolutionContext) {
                    if (listeners == null) {
                        List<BeanDefinition<T>> listenersDefinitions = e.getValue();
                        List<T> listeners = new ArrayList<>(listenersDefinitions.size());
                        for (BeanDefinition<T> listenersDefinition : listenersDefinitions) {
                            T listener;
                            if (beanResolutionContext == null) {
                                try (BeanResolutionContext context = newResolutionContext(listenersDefinition, null)) {
                                    listener = resolveBeanRegistration(context, listenersDefinition).bean;
                                }
                            } else {
                                listener = resolveBeanRegistration(beanResolutionContext, listenersDefinition).bean;
                            }
                            listeners.add(listener);
                        }
                        OrderUtil.sort(listeners);
                        this.listeners = listeners;
                    }
                    return listeners;
                }
            }));
        }
        return eventToListeners;
    }

    @NonNull
    private <T extends EventListener> Map<Class<?>, List<BeanDefinition<T>>> getTypeToListenerMap(@NonNull Class<T> listenerType) {
        final Collection<BeanDefinition<T>> beanDefinitions = getBeanDefinitions(listenerType);
        if (beanDefinitions.isEmpty()) {
            return Collections.emptyMap();
        }
        final HashMap<Class<?>, List<BeanDefinition<T>>> typeToListener = CollectionUtils.newHashMap(beanDefinitions.size());
        for (BeanDefinition<T> beanCreatedDefinition : beanDefinitions) {
            List<Argument<?>> typeArguments = beanCreatedDefinition.getTypeArguments(listenerType);
            Argument<?> argument = CollectionUtils.last(typeArguments);
            if (argument == null) {
                argument = Argument.OBJECT_ARGUMENT;
            }
            typeToListener.computeIfAbsent(argument.getType(), aClass -> new ArrayList<>(10))
                    .add(beanCreatedDefinition);
        }
        return typeToListener;
    }

    /**
     * Initialize the context with the given {@link io.micronaut.context.annotation.Context} scope beans.
     *
     * @param eagerInitBeans The context scope beans
     * @param processedBeans    The beans that require {@link ExecutableMethodProcessor} handling
     * @param parallelBeans     The parallel bean definitions
     */
    @Internal
    protected void initializeContext(
            @NonNull List<BeanDefinitionProducer> eagerInitBeans,
            @NonNull List<BeanDefinitionProducer> processedBeans,
            @NonNull List<BeanDefinitionProducer> parallelBeans) {

        if (CollectionUtils.isNotEmpty(eagerInitBeans)) {
            final List<BeanDefinition<Object>> eagerInit = new ArrayList<>(eagerInitBeans.size());
            for (BeanDefinitionProducer contextScopeBean : eagerInitBeans) {
                try {
                    loadEagerBeans(contextScopeBean, eagerInit);
                } catch (Throwable e) {
                    throw new BeanInstantiationException(MSG_BEAN_DEFINITION + contextScopeBean.getReference().getName() + MSG_COULD_NOT_BE_LOADED + e.getMessage(), e);
                }
            }
            filterReplacedBeans(null, eagerInit);
            OrderUtil.sort(eagerInit);
            for (BeanDefinition eagerInitDefinition : eagerInit) {
                try {
                    initializeEagerBean(eagerInitDefinition);
                } catch (DisabledBeanException e) {
                    if (AbstractBeanContextConditional.ConditionLog.LOG.isDebugEnabled()) {
                        AbstractBeanContextConditional.ConditionLog.LOG.debug("Bean of type [{}] disabled for reason: {}", eagerInitDefinition.getBeanType().getSimpleName(), e.getMessage());
                    }
                } catch (Throwable e) {
                    throw new BeanInstantiationException(MSG_BEAN_DEFINITION + eagerInitDefinition.getName() + MSG_COULD_NOT_BE_LOADED + e.getMessage(), e);
                }
            }
        }

        if (!processedBeans.isEmpty()) {
            List<BeanDefinitionMethodReference<Object, Object>> methodsToProcess = new ArrayList<>();
            for (BeanDefinitionProducer processedBeanProducer : processedBeans) {
                if (!processedBeanProducer.isDefinitionEnabled(this)) {
                    continue;
                }
                BeanDefinition<Object> definition = processedBeanProducer.getDefinition(this);
                for (ExecutableMethod<Object, ?> method : definition.getExecutableMethods()) {
                    if (method.hasStereotype(Executable.class)) {
                        methodsToProcess.add(BeanDefinitionMethodReference.of(definition, (ExecutableMethod<Object, Object>) method));
                    }
                }
            }

            Map<Class<? extends Annotation>, List<BeanDefinitionMethodReference<?, ?>>> byAnnotation = CollectionUtils.newHashMap(methodsToProcess.size());
            // group the method references by annotation type such that we have a map of Annotation -> MethodReference
            // ie. Class<Scheduled> -> @Scheduled void someAnnotation()
            for (BeanDefinitionMethodReference<?, ?> executableMethod : methodsToProcess) {
                List<Class<? extends Annotation>> annotations = executableMethod.getAnnotationTypesByStereotype(Executable.class);
                for (Class<? extends Annotation> annotation : annotations) {
                    List<BeanDefinitionMethodReference<?, ?>> references = byAnnotation.get(annotation);
                    if (references == null) {
                        references = new ArrayList<>(10);
                        byAnnotation.put(annotation, references);
                    }
                    references.add(executableMethod);
                }
            }

            // Find ExecutableMethodProcessor for each annotation and process the BeanDefinitionMethodReference
            for (Map.Entry<Class<? extends Annotation>, List<BeanDefinitionMethodReference<?, ?>>> entry : byAnnotation.entrySet()) {
                Class<? extends Annotation> annotationType = entry.getKey();
                List<BeanDefinitionMethodReference<?, ?>> methods = entry.getValue();
                streamOfType(ExecutableMethodProcessor.class, Qualifiers.byTypeArguments(annotationType))
                    .forEach(processor -> {
                        if (processor instanceof LifeCycle<?> cycle) {
                            cycle.start();
                        }
                        for (BeanDefinitionMethodReference<?, ?> method : methods) {

                            BeanDefinition<?> beanDefinition = method.getBeanDefinition();

                            // Only process the method if the annotation is not declared at the class level
                            // If declared at the class level it will already have been processed by AnnotationProcessorListener
                            if (!beanDefinition.hasStereotype(annotationType)) {
                                if (method.hasDeclaredStereotype(Parallel.class)) {
                                    ForkJoinPool.commonPool().execute(() -> {
                                        try {
                                            processor.process(beanDefinition, method);
                                        } catch (Throwable e) {
                                            if (LOG.isErrorEnabled()) {
                                                LOG.error("Error processing bean method " + beanDefinition + "." + method + " with processor (" + processor + "): " + e.getMessage(), e);
                                            }
                                            Boolean shutdownOnError = method.booleanValue(Parallel.class, "shutdownOnError").orElse(true);
                                            if (shutdownOnError) {
                                                stop();
                                            }
                                        }
                                    });
                                } else {
                                    processor.process(beanDefinition, method);
                                }
                            }
                        }

                        if (processor instanceof LifeCycle<?> cycle) {
                            cycle.stop();
                        }

                    });
            }
        }

        if (CollectionUtils.isNotEmpty(parallelBeans)) {
            processParallelBeans(parallelBeans);
        }
        ForkJoinPool.commonPool().execute(() -> beanDefinitionsClasses.forEach(p -> p.isReferenceEnabled(this)));
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param <T>               The bean generic type
     * @param resolutionContext The current resolution context
     * @param beanType          The bean type
     * @param filter            A bean definition to filter out
     * @return The candidates
     */
    @NonNull
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(@Nullable BeanResolutionContext resolutionContext,
                                                                   @NonNull Argument<T> beanType,
                                                                   @Nullable BeanDefinition<?> filter) {
        Predicate<BeanDefinition<T>> predicate = filter == null ? null : definition -> !definition.equals(filter);
        return findBeanCandidates(resolutionContext, beanType, true, predicate);
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param <T>               The bean generic type
     * @param resolutionContext The current resolution context
     * @param beanType          The bean type
     * @param collectIterables  Whether iterables should be collected
     * @param predicate         The predicate to filter candidates
     * @return The candidates
     */
    @NonNull
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(@Nullable BeanResolutionContext resolutionContext,
                                                                   @NonNull Argument<T> beanType,
                                                                   boolean collectIterables,
                                                                   Predicate<BeanDefinition<T>> predicate) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        final Class<T> beanClass = beanType.getType();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for type: {}", beanType);
        }
        // first traverse component definition classes and load candidates

        Collection<BeanDefinitionProducer> beanDefinitionsClasses;

        if (indexedTypes.contains(beanClass)) {
            beanDefinitionsClasses = beanIndex.get(beanClass);
            if (beanDefinitionsClasses == null) {
                beanDefinitionsClasses = Collections.emptyList();
            }
        } else {
            beanDefinitionsClasses = this.beanDefinitionsClasses;
        }

        return collectBeanCandidates(
            resolutionContext,
            beanType,
            collectIterables,
            predicate,
            beanDefinitionsClasses
        );
    }

    @NonNull
    private <T> Set<BeanDefinition<T>> collectBeanCandidates(
        BeanResolutionContext resolutionContext,
        Argument<T> beanType,
        boolean collectIterables,
        @Nullable
        Predicate<BeanDefinition<T>> predicate,
        Collection<BeanDefinitionProducer> beanDefinitionProducers) {
        Set<BeanDefinition<T>> candidates;
        if (!beanDefinitionProducers.isEmpty()) {

            candidates = new HashSet<>();
            for (BeanDefinitionProducer producer : beanDefinitionProducers) {
                if (producer.isDisabled() || !producer.isReferenceCandidateBean(beanType) || !producer.isReferenceEnabled(this, resolutionContext)) {
                    continue;
                }
                BeanDefinition<T> loadedBean = producer.getDefinition(this);
                if (!loadedBean.isCandidateBean(beanType)) {
                    continue;
                }
                if (predicate != null && !predicate.test(loadedBean)) {
                    continue;
                }
                if (!producer.isDefinitionEnabled(this, resolutionContext)) {
                    continue;
                }

                if (collectIterables && loadedBean.isConfigurationProperties()) {
                    collectIterableBeans(resolutionContext, loadedBean, candidates);
                } else {
                    candidates.add(loadedBean);
                }
            }

            if (!candidates.isEmpty()) {
                filterReplacedBeans(resolutionContext, candidates);
            }
        } else {
            candidates = Collections.emptySet();
        }

        if (LOG.isDebugEnabled()) {
            if (candidates.isEmpty()) {
                LOG.debug("No bean candidates found for type: {}", beanType);
            } else {
                for (BeanDefinition<?> candidate : candidates) {
                    LOG.debug("  {} {} {}", candidate.getBeanType(), candidate.getDeclaredQualifier(), candidate);
                }
            }
        }
        return candidates;
    }

    /**
     * Collects iterable beans from a given iterable.
     * @param resolutionContext The resolution context
     * @param iterableBean The iterable
     * @param targetSet The target set
     * @param <T> The bean type
     */
    protected <T> void collectIterableBeans(@Nullable BeanResolutionContext resolutionContext, @NonNull BeanDefinition<T> iterableBean, Set<BeanDefinition<T>> targetSet) {
        // no-op
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param instance The bean instance
     * @param <T>      The bean generic type
     * @return The candidates
     */
    @NonNull
    protected <T> Collection<BeanDefinition<T>> findBeanCandidatesForInstance(@NonNull T instance) {
        ArgumentUtils.requireNonNull("instance", instance);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for instance: {}", instance);
        }
        Collection<BeanDefinitionProducer> beanProducers = this.beanDefinitionsClasses;
        final Class<?> beanClass = instance.getClass();
        Argument<?> beanType = Argument.of(beanClass);
        Collection<BeanDefinition<T>> beanDefinitions = (Collection<BeanDefinition<T>>) ((Map) beanCandidateCache).get(beanType);
        if (beanDefinitions != null) {
            return beanDefinitions;
        }
        // first traverse component definition classes and load candidates
        if (!beanDefinitionsClasses.isEmpty()) {
            List<BeanDefinition<T>> candidates = new ArrayList<>();
            for (BeanDefinitionProducer producer : beanProducers) {
                if (producer.isDisabled() || !producer.isReferenceEnabled(this)) {
                    continue;
                }
                BeanDefinitionReference<T> reference = producer.getReference();
                Class<?> candidateType = reference.getBeanType();
                if (candidateType == null || !candidateType.isInstance(instance)) {
                    continue;
                }
                BeanDefinition<T> candidate = reference.load(this);
                if (!candidate.isEnabled(this)) {
                    continue;
                }
                candidates.add(candidate);
            }

            if (candidates.size() > 1) {
                // try narrow to exact type
                candidates = candidates
                        .stream()
                        .filter(candidate ->
                            candidate.getBeanType() == beanClass
                        )
                        .collect(Collectors.toList());
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolved bean candidates {} for instance: {}", candidates, instance);
            }
            beanDefinitions = candidates;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No bean candidates found for instance: {}", instance);
            }
            beanDefinitions = Collections.emptySet();
        }
        beanCandidateCache.put(beanType, (Collection) beanDefinitions);
        return beanDefinitions;
    }

    /**
     * Registers an active configuration.
     *
     * @param configuration The configuration to register
     */
    protected synchronized void registerConfiguration(@NonNull BeanConfiguration configuration) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        beanConfigurations.put(configuration.getName(), configuration);
    }

    /**
     * Execution the creation of a bean. The returned value can be null if a
     * factory method returned null.
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param beanDefinition    The {@link BeanDefinition}
     * @param qualifier         The {@link Qualifier}
     * @param argumentValues    Any argument values passed to create the bean
     * @param <T>               The bean generic type
     * @return The created bean
     */
    @Internal
    @NonNull
    private <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                               @NonNull BeanDefinition<T> beanDefinition,
                               @Nullable Qualifier<T> qualifier,
                               @Nullable Map<String, Object> argumentValues) {
        T bean;
        if (beanDefinition instanceof InstantiatableBeanDefinition<T> instantiatableBeanDefinition) {
            bean = resolveByBeanFactory(resolutionContext, instantiatableBeanDefinition, qualifier, argumentValues);
        } else {
            throw new BeanInstantiationException("BeanDefinition doesn't support creating a new instance of the bean");
        }
        return postBeanCreated(resolutionContext, beanDefinition, qualifier, bean);
    }

    @Internal
    @NonNull
    private <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                               @NonNull BeanDefinition<T> beanDefinition,
                               @Nullable Qualifier<T> qualifier) {
        return doCreateBean(resolutionContext, beanDefinition, qualifier, Collections.emptyMap());
    }

    @NonNull
    private <T> T resolveByBeanFactory(@NonNull BeanResolutionContext resolutionContext,
                                       @NonNull BeanDefinition<T> beanDefinition,
                                       @Nullable Qualifier<T> qualifier,
                                       @Nullable Map<String, Object> argumentValues) {
        Qualifier<T> declaredQualifier = beanDefinition.getDeclaredQualifier();
        Qualifier<?> prevQualifier = resolutionContext.getCurrentQualifier();
        try {
            resolutionContext.setCurrentQualifier(declaredQualifier != null && !AnyQualifier.INSTANCE.equals(declaredQualifier) ? declaredQualifier : qualifier);
            T bean;
            if (beanDefinition instanceof ParametrizedInstantiatableBeanDefinition<T> parametrizedInstantiatableBeanDefinition) {
                Argument<Object>[] requiredArguments = parametrizedInstantiatableBeanDefinition.getRequiredArguments();
                Map<String, Object> convertedValues = getRequiredArgumentValues(resolutionContext, requiredArguments, argumentValues, beanDefinition);
                bean = parametrizedInstantiatableBeanDefinition.instantiate(resolutionContext, this, convertedValues);
            } else if (beanDefinition instanceof InstantiatableBeanDefinition<T> instantiatableBeanDefinition) {
                bean = instantiatableBeanDefinition.instantiate(resolutionContext, this);
            } else {
                throw new BeanInstantiationException(resolutionContext, "Expected InstantiatableBeanDefinition [" + beanDefinition + "]");
            }
            if (bean == null) {
                throw new BeanInstantiationException(resolutionContext, "InstantiatableBeanDefinition [" + beanDefinition + "] returned null");
            }
            if (bean instanceof Qualified qualified) {
                qualified.$withBeanQualifier(declaredQualifier);
            }
            return bean;
        } catch (DependencyInjectionException | DisabledBeanException | BeanInstantiationException e) {
            throw e;
        } catch (Throwable e) {
            if (!resolutionContext.getPath().isEmpty()) {
                throw new BeanInstantiationException(resolutionContext, e);
            }
            throw new BeanInstantiationException(beanDefinition, e);
        } finally {
            resolutionContext.setCurrentQualifier(prevQualifier);
        }
    }

    @NonNull
    private <T> T postBeanCreated(@NonNull BeanResolutionContext resolutionContext,
                                  @NonNull BeanDefinition<T> beanDefinition,
                                  @Nullable Qualifier<T> qualifier,
                                  @NonNull T bean) {
        Qualifier<T> finalQualifier = qualifier != null ? qualifier : beanDefinition.getDeclaredQualifier();

        bean = triggerBeanCreatedEventListener(resolutionContext, beanDefinition, bean, finalQualifier);

        if (beanDefinition instanceof ValidatedBeanDefinition<T> validatedBeanDefinition) {
            bean = validatedBeanDefinition.validate(resolutionContext, bean);
        }
        if (LOG_LIFECYCLE.isDebugEnabled()) {
            LOG_LIFECYCLE.debug("Created bean [{}] from definition [{}] with qualifier [{}]", bean, beanDefinition, finalQualifier);
        }
        return bean;
    }

    @NonNull
    private <T> T triggerBeanCreatedEventListener(@NonNull BeanResolutionContext resolutionContext,
                                                  @NonNull BeanDefinition<T> beanDefinition,
                                                  @NonNull T bean,
                                                  @Nullable Qualifier<T> finalQualifier) {
        if (!(beanDefinition instanceof AbstractProviderDefinition<?>)) {
            Class<T> beanType = beanDefinition.getBeanType();
            if (!(bean instanceof BeanCreatedEventListener) && CollectionUtils.isNotEmpty(beanCreationEventListeners)) {
                for (Map.Entry<Class<?>, ListenersSupplier<BeanCreatedEventListener>> entry : beanCreationEventListeners) {
                    if (entry.getKey().isAssignableFrom(beanType)) {
                        BeanKey<T> beanKey = new BeanKey<>(beanDefinition, finalQualifier);
                        for (BeanCreatedEventListener<?> listener : entry.getValue().get(resolutionContext)) {
                            bean = (T) listener.onCreated(new BeanCreatedEvent(this, beanDefinition, beanKey, bean));
                            if (bean == null) {
                                throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onCreated event");
                            }
                        }
                    }
                }
            }
        }
        return bean;
    }

    @NonNull
    private <T> Map<String, Object> getRequiredArgumentValues(@NonNull BeanResolutionContext resolutionContext,
                                                              @NonNull Argument<?>[] requiredArguments,
                                                              @Nullable Map<String, Object> argumentValues,
                                                              @NonNull BeanDefinition<T> beanDefinition) {
        Map<String, Object> convertedValues;
        if (argumentValues == null) {
            convertedValues = requiredArguments.length == 0 ? null : CollectionUtils.newLinkedHashMap(requiredArguments.length);
            argumentValues = Collections.emptyMap();
        } else {
            convertedValues = CollectionUtils.newLinkedHashMap(requiredArguments.length);
        }
        if (convertedValues == null) {
            return Collections.emptyMap();
        }
        MutableConversionService conversionService = getConversionService();
        for (Argument<?> requiredArgument : requiredArguments) {
            String argumentName = requiredArgument.getName();
            Object val = argumentValues.get(argumentName);
            if (val == null) {
                if (!requiredArgument.isDeclaredNullable()) {
                    throw new BeanInstantiationException(resolutionContext, "Missing bean argument [" + requiredArgument + "] for type: " + beanDefinition.getBeanType().getName() + ". Required arguments: " + ArrayUtils.toString(requiredArguments));
                }
            } else {
                Object convertedValue;
                if (requiredArgument.getType().isInstance(val)) {
                    convertedValue = val;
                } else {
                    convertedValue = conversionService.convert(val, requiredArgument).orElseThrow(() ->
                            new BeanInstantiationException(resolutionContext, "Invalid bean argument [" + requiredArgument + "]. Cannot convert object [" + val + "] to required type: " + requiredArgument.getType())
                    );
                }
                convertedValues.put(argumentName, convertedValue);
            }
        }
        return convertedValues;
    }

    /**
     * Fall back method to attempt to find a candidate for the given definitions.
     *
     * @param beanType   The bean type
     * @param qualifier  The qualifier
     * @param candidates The candidates, always more than 1
     * @param <T>        The generic time
     * @return The concrete bean definition
     */
    @NonNull
    protected <T> BeanDefinition<T> findConcreteCandidate(@NonNull Class<T> beanType,
                                                          @Nullable Qualifier<T> qualifier,
                                                          @NonNull Collection<BeanDefinition<T>> candidates) {
        if (qualifier instanceof AnyQualifier) {
            return candidates.iterator().next();
        } else {
            throw new NonUniqueBeanException(beanType, candidates.iterator());
        }
    }

    /**
     * Processes parallel bean definitions.
     *
     * @param parallelBeans The parallel beans
     */
    @Internal
    protected void processParallelBeans(List<BeanDefinitionProducer> parallelBeans) {
        if (!parallelBeans.isEmpty()) {
            List<BeanDefinitionProducer> finalParallelBeans = parallelBeans.stream()
                    .filter(p -> p.isReferenceEnabled(this))
                    .toList();
            if (!finalParallelBeans.isEmpty()) {
                new Thread(() -> {
                    Collection<BeanDefinition<Object>> parallelDefinitions = new ArrayList<>();
                    finalParallelBeans.forEach(producer -> {
                        try {
                            loadEagerBeans(producer, parallelDefinitions);
                        } catch (Throwable e) {
                            BeanDefinitionReference<Object> beanDefinitionReference = producer.getReference();
                            LOG.error("Parallel Bean definition [" + beanDefinitionReference.getName() + MSG_COULD_NOT_BE_LOADED + e.getMessage(), e);
                            Boolean shutdownOnError = beanDefinitionReference.getAnnotationMetadata().booleanValue(Parallel.class, "shutdownOnError").orElse(true);
                            if (shutdownOnError) {
                                stop();
                            }
                        }
                    });

                    filterReplacedBeans(null, parallelDefinitions);

                    parallelDefinitions.forEach(beanDefinition -> ForkJoinPool.commonPool().execute(() -> {
                        try {
                            initializeEagerBean(beanDefinition);
                        } catch (Throwable e) {
                            LOG.error("Parallel Bean definition [" + beanDefinition.getName() + MSG_COULD_NOT_BE_LOADED + e.getMessage(), e);
                            Boolean shutdownOnError = beanDefinition.getAnnotationMetadata().booleanValue(Parallel.class, "shutdownOnError").orElse(true);
                            if (shutdownOnError) {
                                stop();
                            }
                        }
                    }));
                    parallelDefinitions.clear();

                }).start();
            }
        }
    }

    private <T> void filterReplacedBeans(BeanResolutionContext resolutionContext, Collection<BeanDefinition<T>> candidates) {
        if (candidates.size() > 1) {
            List<BeanDefinition<T>> replacementTypes = new ArrayList<>(2);
            for (BeanDefinition<T> candidate : candidates) {
                if (candidate.getAnnotationMetadata().hasStereotype(REPLACES_ANN)) {
                    replacementTypes.add(candidate);
                }
            }
            if (!replacementTypes.isEmpty()) {
                candidates.removeIf(definition -> checkIfReplacementExists(resolutionContext, replacementTypes, definition));
            }
        }
    }

    private <T> boolean checkIfReplacementExists(BeanResolutionContext resolutionContext,
                                                 List<BeanDefinition<T>> replacementTypes,
                                                 BeanDefinition<T> definitionToBeReplaced) {
        if (!definitionToBeReplaced.isEnabled(this, resolutionContext)) {
            return true;
        }
        final AnnotationMetadata annotationMetadata = definitionToBeReplaced.getAnnotationMetadata();
        if (annotationMetadata.hasDeclaredStereotype(Infrastructure.class)) {
            return false;
        }
        for (BeanDefinition<T> replacementType : replacementTypes) {
            if (isNotTheSameDefinition(replacementType, definitionToBeReplaced) &&
                    isNotProxy(replacementType, definitionToBeReplaced) &&
                    checkIfReplaces(replacementType, definitionToBeReplaced, annotationMetadata)) {
                return true;
            }
        }
        return false;
    }

    private <T> boolean isNotTheSameDefinition(BeanDefinition<T> replacingCandidate, BeanDefinition<T> definitionToBeReplaced) {
        if (replacingCandidate instanceof BeanDefinitionDelegate<T> beanDefinitionDelegate) {
            replacingCandidate = beanDefinitionDelegate.getDelegate();
        }
        if (definitionToBeReplaced instanceof BeanDefinitionDelegate<T> beanDefinitionDelegate) {
            definitionToBeReplaced = beanDefinitionDelegate.getDelegate();
        }
        return replacingCandidate != definitionToBeReplaced;
    }

    private <T> boolean isNotProxy(BeanDefinition<T> replacingCandidate, BeanDefinition<T> definitionToBeReplaced) {
        return !(replacingCandidate instanceof ProxyBeanDefinition &&
                ((ProxyBeanDefinition<T>) replacingCandidate).getTargetDefinitionType() == definitionToBeReplaced.getClass());
    }

    private <T> boolean checkIfReplaces(BeanDefinition<T> replacingCandidate, BeanDefinition<T> definitionToBeReplaced, AnnotationMetadata annotationMetadata) {
        final AnnotationValue<Replaces> replacesAnnotation = replacingCandidate.getAnnotation(Replaces.class);
        final Class replacedBeanType = replacesAnnotation.classValue(Replaces.MEMBER_BEAN).orElse(getCanonicalBeanType(replacingCandidate));
        final Optional<String> named = replacesAnnotation.stringValue(Replaces.MEMBER_NAMED);
        final Optional<AnnotationClassValue<?>> qualifier = replacesAnnotation.annotationClassValue(Replaces.MEMBER_QUALIFIER);

        if (named.isPresent() && qualifier.isPresent()) {
            throw new ConfigurationException("Both \"named\" and \"qualifier\" should not be present: " + replacesAnnotation);
        }

        if (named.isPresent()) {
            final String name = named.get();
            if (qualifiedByNamed(definitionToBeReplaced, replacedBeanType, name)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Bean [{}] replaces existing bean of type [{}] qualified by name [{}]",
                            replacingCandidate.getBeanType(), definitionToBeReplaced.getBeanType(), name);
                }
                return true;
            }
            return false;
        }

        if (qualifier.isPresent()) {
            final AnnotationClassValue<?> qualifierClassValue = qualifier.get();
            if (qualifiedByQualifier(definitionToBeReplaced, replacedBeanType, qualifierClassValue)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Bean [{}] replaces existing bean of type [{}] qualified by qualifier [{}]",
                            replacingCandidate.getBeanType(), definitionToBeReplaced.getBeanType(), qualifierClassValue);
                }
                return true;
            }
            return false;
        }

        Optional<Class<?>> factory = replacesAnnotation.classValue(Replaces.MEMBER_FACTORY);
        if (factory.isPresent()) {
            Optional<Class<?>> declaringType = definitionToBeReplaced.getDeclaringType();
            if (declaringType.isPresent()) {
                Class<?> factoryClass = factory.get();
                final boolean factoryReplaces = factoryClass == declaringType.get() &&
                        checkIfTypeMatches(definitionToBeReplaced, annotationMetadata, replacedBeanType);
                if (factoryReplaces) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Bean [{}] replaces existing bean of type [{}] in factory type [{}]",
                                replacingCandidate.getBeanType(), replacedBeanType, factoryClass);
                    }
                    return true;
                }
            }
            return false;
        }

        final boolean isTypeMatches = checkIfTypeMatches(definitionToBeReplaced, annotationMetadata, replacedBeanType);
        if (isTypeMatches && LOG.isDebugEnabled()) {
            LOG.debug("Bean [{}] replaces existing bean of type [{}]", replacingCandidate.getBeanType(), replacedBeanType);
        }
        return isTypeMatches;
    }

    private <T> boolean qualifiedByQualifier(BeanDefinition<T> definitionToBeReplaced,
                                             Class<T> replacedBeanType,
                                             AnnotationClassValue<?> qualifier) {
        @SuppressWarnings("unchecked") final Class<? extends Annotation> qualifierClass =
                (Class<? extends Annotation>) qualifier.getType().orElse(null);
        if (qualifierClass != null && !qualifierClass.isAssignableFrom(Annotation.class)) {
            return Qualifiers.<T>byStereotype(qualifierClass).qualify(replacedBeanType, Stream.of(definitionToBeReplaced))
                .isPresent();
        } else {
            throw new ConfigurationException("Default qualifier value was used while replacing %s".formatted(replacedBeanType));
        }
    }

    private <T> boolean qualifiedByNamed(BeanType<T> definitionToBeReplaced, Class replacedBeanType, String named) {
        return Qualifiers.<T>byName(named).qualify(replacedBeanType, Stream.of(definitionToBeReplaced))
            .isPresent();
    }

    private <T> Class<T> getCanonicalBeanType(BeanDefinition<T> beanDefinition) {
        if (beanDefinition instanceof BeanDefinitionDelegate<T> beanDefinitionDelegate) {
            beanDefinition = beanDefinitionDelegate.getDelegate();
        }
        if (beanDefinition instanceof AdvisedBeanType<?> advisedBeanType) {
            return (Class<T>) advisedBeanType.getInterceptedType();
        }
        if (beanDefinition instanceof ProxyBeanDefinition<T> proxyBeanDefinition) {
            return proxyBeanDefinition.getTargetType();
        }
        return beanDefinition.getBeanType();
    }

    private <T> boolean checkIfTypeMatches(BeanDefinition<T> definitionToBeReplaced,
                                           AnnotationMetadata annotationMetadata,
                                           Class replacingCandidate) {
        Class<T> bt = getCanonicalBeanType(definitionToBeReplaced);
        if (annotationMetadata.hasAnnotation(DefaultImplementation.class)) {
            Optional<Class> defaultImpl = annotationMetadata.classValue(DefaultImplementation.class);
            if (defaultImpl.isEmpty()) {
                defaultImpl = annotationMetadata.classValue(DefaultImplementation.class, "name");
            }
            if (defaultImpl.filter(impl -> impl == bt).isPresent()) {
                return replacingCandidate.isAssignableFrom(bt);
            } else {
                return replacingCandidate == bt;
            }
        }
        return replacingCandidate != Object.class && replacingCandidate.isAssignableFrom(bt);
    }

    private <T> void doInjectAndInitialize(BeanResolutionContext resolutionContext, T instance, BeanDefinition<T> beanDefinition) {
        if (beanDefinition instanceof InjectableBeanDefinition<T> injectableBeanDefinition) {
            injectableBeanDefinition.inject(resolutionContext, this, instance);
            if (beanDefinition instanceof InitializingBeanDefinition<T> initializingBeanDefinition) {
                initializingBeanDefinition.initialize(resolutionContext, this, instance);
            }
        } else {
            throw new BeanContextException(MSG_BEAN_DEFINITION + beanDefinition + "] doesn't support injection!");
        }
    }

    private void loadEagerBeans(BeanDefinitionProducer producer, Collection<BeanDefinition<Object>> collector) {
        if (producer.isReferenceEnabled(this)) {
            BeanDefinitionReference<Object> reference = producer.getReference();
            BeanDefinition<Object> beanDefinition = reference.load(this);
            try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
                if (beanDefinition.isEnabled(this, resolutionContext)) {
                    collector.add(beanDefinition);
                }
            }
        }
    }

    private void initializeEagerBean(BeanDefinition<Object> beanDefinition) {
        if (beanDefinition.isIterable() || beanDefinition.hasStereotype(ConfigurationReader.class.getName())) {
            Set<BeanDefinition<Object>> beanCandidates = new HashSet<>(5);

            collectIterableBeans(
                null,
                beanDefinition,
                beanCandidates
            );
            for (BeanDefinition beanCandidate : beanCandidates) {
                findOrCreateSingletonBeanRegistration(
                        null,
                        beanCandidate,
                        beanCandidate.asArgument(),
                        beanCandidate.hasAnnotation(Context.class) ? null : beanDefinition.getDeclaredQualifier()
                );
            }

        } else {
            findOrCreateSingletonBeanRegistration(null, beanDefinition, beanDefinition.asArgument(), null);
        }
    }

    /**
     * Resolve the {@link BeanRegistration} by an argument and a qualifier.
     *
     * @param resolutionContext The resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param throwNoSuchBean   Throw if it doesn't exist
     * @param <T>               The type
     * @return The bean registration
     */
    @Nullable
    private <T> BeanRegistration<T> resolveBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                            @NonNull Argument<T> beanType,
                                                            @Nullable Qualifier<T> qualifier,
                                                            boolean throwNoSuchBean) {
        // allow injection the bean context
        final Class<T> beanClass = beanType.getType();
        if (thisInterfaces.contains(beanClass)) {
            return new BeanRegistration<>(BeanIdentifier.of(beanClass.getName()), null, (T) this);
        }
        if (InjectionPoint.class.isAssignableFrom(beanClass)) {
            return provideInjectionPoint(resolutionContext, beanType, qualifier, throwNoSuchBean);
        }
        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Looking up existing bean for key: {}", beanKey);
        }

        BeanRegistration<T> inFlightBeanRegistration = resolutionContext != null ? resolutionContext.getInFlightBean(beanKey) : null;
        if (inFlightBeanRegistration != null) {
            return inFlightBeanRegistration;
        }

        // Fast singleton lookup
        BeanRegistration<T> beanRegistration = singletonScope.findCachedSingletonBeanRegistration(beanType, qualifier);
        if (beanRegistration != null) {
            return beanRegistration;
        }

        Optional<BeanDefinition<T>> concreteCandidate = findBeanDefinition(resolutionContext, beanType, qualifier);

        BeanRegistration<T> registration;

        if (concreteCandidate.isPresent()) {
            BeanDefinition<T> definition = concreteCandidate.get();

            if (definition.isContainerType() && beanClass != definition.getBeanType()) {
                throw new NonUniqueBeanException(beanClass, Collections.singletonList(definition).iterator());
            }
            registration = resolveBeanRegistration(resolutionContext, definition, beanType, qualifier);
        } else {
            registration = null;
        }
        if ((registration == null || registration.bean == null) && throwNoSuchBean) {
            throw newNoSuchBeanException(resolutionContext, beanType, qualifier, null);
        }
        return registration;
    }

    private <T> Optional<BeanDefinition<T>> findBeanDefinition(BeanResolutionContext resolutionContext, Argument<T> beanType, Qualifier<T> qualifier) {
        BeanDefinition<T> beanDefinition = singletonScope.findCachedSingletonBeanDefinition(beanType, qualifier);
        if (beanDefinition != null) {
            return Optional.of(beanDefinition);
        }
        return findConcreteCandidate(resolutionContext, beanType, qualifier, true);
    }

    /**
     * Trigger a no such bean exception. Subclasses can improve the exception with downstream diagnosis as necessary.
     *
     * @param <T>               The type of the bean
     * @param resolutionContext The resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param message           A message to use
     * @return A no such bean exception
     */
    @Internal
    @NonNull
    protected <T> NoSuchBeanException newNoSuchBeanException(
        @Nullable BeanResolutionContext resolutionContext,
        @NonNull Argument<T> beanType,
        @NonNull Qualifier<T> qualifier,
        @Nullable String message) {
        if (message != null) {
            return new NoSuchBeanException(beanType, qualifier, message);
        } else {
            String disabledMessage = resolveDisabledBeanMessage(resolutionContext, beanType, qualifier);

            if (disabledMessage != null) {
                return new NoSuchBeanException(beanType, qualifier, disabledMessage);
            } else {
                return new NoSuchBeanException(beanType, qualifier);
            }
        }
    }

    /**
     * Resolves the message to use for a disabled bean.
     * @param resolutionContext The resolution context
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @return The message or null if none exists
     * @param <T> The bean type
     */
    @Nullable
    protected  <T> String resolveDisabledBeanMessage(BeanResolutionContext resolutionContext, Argument<T> beanType, Qualifier<T> qualifier) {
        String disabledMessage = null;
        for (Map.Entry<String, List<String>> entry : disabledConfigurations.entrySet()) {
            String pkg = entry.getKey();
            if (beanType.getTypeName().startsWith(pkg + ".")) {
                StringBuilder messageBuilder = new StringBuilder();
                String ls = CachedEnvironment.getProperty("line.separator");
                messageBuilder.append("The bean [")
                              .append(beanType.getTypeString(true))
                              .append("] is disabled because it is within the package [")
                              .append(pkg)
                               .append("] which is disabled due to bean requirements: ")
                              .append(ls);
                for (String failure : entry.getValue()) {
                    messageBuilder.append("* ").append(failure).append(ls);
                }

                disabledMessage = messageBuilder.toString();
                break;
            }
        }

        if (disabledMessage == null) {

            Set<BeanDefinition<T>> beanDefinitions = collectBeanCandidates(
                resolutionContext,
                beanType,
                false,
                null,
                disabledBeans.values()
            );
            if (qualifier != null) {
                beanDefinitions = qualifier
                    .reduce(beanType.getType(), beanDefinitions.stream())
                    .collect(Collectors.toSet());
            }

            if (!beanDefinitions.isEmpty()) {
                StringBuilder messageBuilder = new StringBuilder();
                String ls = CachedEnvironment.getProperty("line.separator");
                messageBuilder.append("The following matching beans are disabled by bean requirements: ").append(ls);
                for (BeanDefinition<T> beanDefinition : beanDefinitions) {
                    messageBuilder.append("* Bean of type [").append(beanDefinition.asArgument().getTypeString(false))
                        .append("] is disabled because: ").append(ls);
                    if (beanDefinition instanceof DisabledBean<T> disabledBean) {
                        for (String failure : disabledBean.reasons()) {
                            messageBuilder.append("   - ").append(failure).append(ls);
                        }
                    }
                }

                disabledMessage = messageBuilder.toString();
            }
        }
        return disabledMessage;
    }

    @Nullable
    private <T> BeanRegistration<T> provideInjectionPoint(BeanResolutionContext resolutionContext,
                                                          Argument<T> beanType,
                                                          Qualifier<T> qualifier,
                                                          boolean throwNoSuchBean) {
        final BeanResolutionContext.Path path = resolutionContext != null ? resolutionContext.getPath() : null;
        BeanResolutionContext.Segment<?, ?> injectionPointSegment = null;
        if (CollectionUtils.isNotEmpty(path)) {
            @SuppressWarnings("java:S2259") // false positive
            final Iterator<BeanResolutionContext.Segment<?, ?>> i = path.iterator();
            injectionPointSegment = i.next();
            BeanResolutionContext.Segment<?, ?> segment = null;
            if (i.hasNext()) {
                segment = i.next();
                if (segment.getDeclaringType().hasStereotype(INTRODUCTION_TYPE)) {
                    segment = i.hasNext() ? i.next() : null;
                }
            }
            if (segment != null) {
                T ip = (T) segment.getInjectionPoint();
                if (ip != null && beanType.isInstance(ip)) {
                    return new BeanRegistration<>(BeanIdentifier.of(InjectionPoint.class.getName()), null, ip);
                }
            }
        }
        if (injectionPointSegment == null || !injectionPointSegment.getArgument().isNullable()) {
            throw new BeanContextException("Failed to obtain injection point. No valid injection path present in path: " + path);
        } else if (throwNoSuchBean) {
            throw newNoSuchBeanException(
                resolutionContext,
                beanType,
                qualifier,
                null
            );
        }
        return null;
    }

    /**
     * Resolve the {@link BeanRegistration} by a {@link BeanDefinition}.
     *
     * @param resolutionContext The resolution context
     * @param definition        The bean type
     * @param <T>               The type
     * @return The bean registration or {@link NoSuchBeanException}
     */
    @NonNull
    private <T> BeanRegistration<T> resolveBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                            @NonNull BeanDefinition<T> definition) {
        return resolveBeanRegistration(resolutionContext, definition, definition.asArgument(), definition.getDeclaredQualifier());
    }

    /**
     * Resolve the {@link BeanRegistration} by a {@link BeanDefinition}.
     *
     * @param resolutionContext The resolution context
     * @param definition        The bean type
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The type
     * @return The bean registration
     */
    @NonNull
    private <T> BeanRegistration<T> resolveBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                            @NonNull BeanDefinition<T> definition,
                                                            @NonNull Argument<T> beanType,
                                                            @Nullable Qualifier<T> qualifier) {
        final boolean isScopedProxyDefinition = definition.hasStereotype(SCOPED_PROXY_ANN);

        if (qualifier != null && AnyQualifier.INSTANCE.equals(definition.getDeclaredQualifier())) {
            // Any factory bean should be stored as a new bean definition with a qualifier
            definition = BeanDefinitionDelegate.create(definition, qualifier);
        }

        if (definition.isSingleton() && !isScopedProxyDefinition) {
            return findOrCreateSingletonBeanRegistration(resolutionContext, definition, beanType, qualifier);
        }

        final boolean isProxy = definition.isProxy();

        if (isProxy && isScopedProxyDefinition) {
            // AOP proxy
            Qualifier<T> q = qualifier;
            if (q == null) {
                q = definition.getDeclaredQualifier();
            }
            BeanRegistration<T> registration = createRegistration(resolutionContext, beanType, q, definition, true);
            T bean = registration.bean;
            if (bean instanceof Qualified) {
                ((Qualified<T>) bean).$withBeanQualifier(q);
            }
            return registration;
        }

        CustomScope<?> customScope = findCustomScope(resolutionContext, definition, isProxy, isScopedProxyDefinition);
        if (customScope != null) {
            if (isProxy) {
                definition = getProxyTargetBeanDefinition(beanType, qualifier);
            }
            return getOrCreateScopedRegistration(resolutionContext, customScope, qualifier, beanType, definition);
        }
        // Unknown scope, prototype scope etc
        return createRegistration(resolutionContext, beanType, qualifier, definition, true);
    }

    @NonNull
    private <T> BeanRegistration<T> findOrCreateSingletonBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                                          @NonNull BeanDefinition<T> definition,
                                                                          @NonNull Argument<T> beanType,
                                                                          @Nullable Qualifier<T> qualifier) {
        BeanRegistration<T> beanRegistration = singletonScope.findBeanRegistration(definition, beanType, qualifier);
        if (beanRegistration != null) {
            return beanRegistration;
        }
        return singletonScope.getOrCreate(this, resolutionContext, definition, beanType, qualifier);
    }

    @Nullable
    private <T> CustomScope<?> findCustomScope(@Nullable BeanResolutionContext resolutionContext,
                                               @NonNull BeanDefinition<T> definition,
                                               boolean isProxy,
                                               boolean isScopedProxyDefinition) {
        Optional<Class<? extends Annotation>> scope = definition.getScope();
        if (scope.isPresent()) {
            Class<? extends Annotation> scopeAnnotation = scope.get();
            if (scopeAnnotation == Prototype.class) {
                return null;
            }
            CustomScope<?> customScope = customScopeRegistry.findScope(scopeAnnotation).orElse(null);
            if (customScope != null) {
                return customScope;
            }
        } else {
            Optional<String> scopeName = definition.getScopeName();
            if (scopeName.isPresent()) {
                String scopeAnnotation = scopeName.get();
                if (Prototype.class.getName().equals(scopeAnnotation)) {
                    return null;
                }
                CustomScope<?> customScope = customScopeRegistry.findScope(scopeAnnotation).orElse(null);
                if (customScope != null) {
                    return customScope;
                }
            }
        }

        if (resolutionContext != null) {
            BeanResolutionContext.Segment<?, ?> currentSegment = resolutionContext
                    .getPath()
                    .currentSegment()
                    .orElse(null);
            if (currentSegment != null) {
                Argument<?> argument = currentSegment.getArgument();
                CustomScope<?> customScope = customScopeRegistry.findDeclaredScope(argument).orElse(null);
                if (customScope != null) {
                    return customScope;
                }
            }
        }

        if (!isScopedProxyDefinition || !isProxy) {
            return customScopeRegistry.findDeclaredScope(definition).orElse(null);
        }
        return null;
    }

    @NonNull
    private <T> BeanRegistration<T> getOrCreateScopedRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                                  @NonNull CustomScope<?> registeredScope,
                                                                  @Nullable Qualifier<T> qualifier,
                                                                  @NonNull Argument<T> beanType,
                                                                  @NonNull BeanDefinition<T> definition) {
        BeanKey<T> beanKey = new BeanKey<>(definition.asArgument(), qualifier);
        T bean = registeredScope.getOrCreate(
                new BeanCreationContext<T>() {
                    @NonNull
                    @Override
                    public BeanDefinition<T> definition() {
                        return definition;
                    }

                    @NonNull
                    @Override
                    public BeanIdentifier id() {
                        return beanKey;
                    }

                    @NonNull
                    @Override
                    public CreatedBean<T> create() throws BeanCreationException {
                        return createRegistration(resolutionContext == null ? null : resolutionContext.copy(), beanKey.beanType, qualifier, definition, true);
                    }
                }
        );
        return BeanRegistration.of(this, beanKey, definition, bean);
    }

    @NonNull
    @Internal
    final <T> BeanRegistration<T> createRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                     @NonNull Argument<T> beanType,
                                                     @Nullable Qualifier<T> qualifier,
                                                     @NonNull BeanDefinition<T> definition,
                                                     boolean dependent) {
        try (BeanResolutionContext context = newResolutionContext(definition, resolutionContext)) {
            final BeanResolutionContext.Path path = context.getPath();
            final boolean isNewPath = path.isEmpty();
            if (isNewPath) {
                path.pushBeanCreate(definition, beanType);
            }
            try {
                List<BeanRegistration<?>> parentDependentBeans = context.popDependentBeans();
                T bean = doCreateBean(context, definition, qualifier);
                BeanRegistration<?> dependentFactoryBean = context.getAndResetDependentFactoryBean();
                if (dependentFactoryBean != null) {
                    destroyBean(dependentFactoryBean);
                }
                BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);
                List<BeanRegistration<?>> dependentBeans = context.getAndResetDependentBeans();
                BeanRegistration<T> beanRegistration = BeanRegistration.of(this, beanKey, definition, bean, dependentBeans);
                context.pushDependentBeans(parentDependentBeans);
                if (dependent) {
                    context.addDependentBean(beanRegistration);
                }
                return beanRegistration;
            } finally {
                if (isNewPath) {
                    path.pop();
                }
            }
        }
    }

    /**
     * Find a concrete candidate for the given qualifier.
     *
     * @param beanType       The bean type
     * @param qualifier      The qualifier
     * @param throwNonUnique Whether to throw an exception if the bean is not found
     * @param <T>            The bean generic type
     * @return The concrete bean definition candidate
     */
    @SuppressWarnings({"unchecked", "rawtypes", "java:S2789"}) // performance optimization
    private <T> Optional<BeanDefinition<T>> findConcreteCandidate(@Nullable BeanResolutionContext resolutionContext,
                                                                  @NonNull Argument<T> beanType,
                                                                  @Nullable Qualifier<T> qualifier,
                                                                  boolean throwNonUnique) {
        if (beanType.getType() == Object.class && qualifier == null) {
            return Optional.empty();
        }
        BeanCandidateKey bk = new BeanCandidateKey(beanType, qualifier, throwNonUnique);
        Optional beanDefinition = beanConcreteCandidateCache.get(bk);
        if (beanDefinition == null) {
            beanDefinition = findConcreteCandidateNoCache(
                    resolutionContext,
                    beanType,
                    qualifier,
                    throwNonUnique);
            beanConcreteCandidateCache.put(bk, beanDefinition);
        }
        return beanDefinition;
    }

    private <T> Optional<BeanDefinition<T>> findConcreteCandidateNoCache(@Nullable BeanResolutionContext resolutionContext,
                                                                         @NonNull Argument<T> beanType,
                                                                         @Nullable Qualifier<T> qualifier,
                                                                         boolean throwNonUnique) {

        Predicate<BeanDefinition<T>> predicate = candidate -> !candidate.isAbstract();
        Collection<BeanDefinition<T>> candidates = findBeanCandidates(resolutionContext, beanType, true, predicate);
        return pickOneBean(beanType, qualifier, throwNonUnique, candidates);
    }

    private <T> Optional<BeanDefinition<T>> findProxyTargetNoCache(@Nullable BeanResolutionContext resolutionContext,
                                                                         @NonNull Argument<T> beanType,
                                                                         @Nullable Qualifier<T> qualifier) {

        Collection<BeanDefinition<T>> candidates = collectBeanCandidates(
            resolutionContext,
            beanType,
            true,
            null,
            proxyTargetBeans
        );
        return pickOneBean(beanType, qualifier, false, candidates);
    }

    @NonNull
    private <T> Optional<BeanDefinition<T>> pickOneBean(
        Argument<T> beanType,
        Qualifier<T> qualifier,
        boolean throwNonUnique,
        Collection<BeanDefinition<T>> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        BeanDefinition<T> definition;
        if (qualifier != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Qualifying bean [{}] for qualifier: {} ", beanType.getName(), qualifier);
            }

            Stream<BeanDefinition<T>> qualified = qualifier.reduce(beanType.getType(), candidates.stream());
            List<BeanDefinition<T>> beanDefinitionList = qualified.toList();
            if (beanDefinitionList.isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No qualifying beans of type [{}] found for qualifier: {} ", beanType.getName(), qualifier);
                }
                return Optional.empty();
            }

            definition = lastChanceResolve(
                beanType,
                qualifier,
                throwNonUnique,
                beanDefinitionList
            );
        } else {
            if (candidates.size() == 1) {
                definition = candidates.iterator().next();
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Searching for @Primary for type [{}] from candidates: {} ", beanType.getName(), candidates);
                }
                definition = lastChanceResolve(
                    beanType,
                    null,
                    throwNonUnique,
                    candidates
                );
            }
        }
        if (LOG.isDebugEnabled() && definition != null) {
            if (qualifier != null) {
                LOG.debug("Found concrete candidate [{}] for type: {} {} ", definition, qualifier, beanType.getName());
            } else {
                LOG.debug("Found concrete candidate [{}] for type: {} ", definition, beanType.getName());
            }
        }
        return Optional.ofNullable(definition);
    }

    private <T> BeanDefinition<T> lastChanceResolve(Argument<T> beanType,
                                                    Qualifier<T> qualifier,
                                                    boolean throwNonUnique,
                                                    Collection<BeanDefinition<T>> candidates) {
        final Class<T> beanClass = beanType.getType();

        if (candidates.size() > 1) {
            List<BeanDefinition<T>> primary = candidates.stream()
                    .filter(BeanDefinition::isPrimary)
                    .toList();
            if (!primary.isEmpty()) {
                candidates = primary;
            }
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        BeanDefinition<T> definition = null;
        candidates = candidates.stream().filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class)).toList();
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        } else if (candidates.stream().anyMatch(candidate -> candidate.hasAnnotation(Order.class))) {
            // pick the bean with the highest priority
            final Iterator<BeanDefinition<T>> i = candidates.stream()
                    .sorted((bean1, bean2) -> {
                        int order1 = OrderUtil.getOrder(bean1.getAnnotationMetadata());
                        int order2 = OrderUtil.getOrder(bean2.getAnnotationMetadata());
                        return Integer.compare(order1, order2);
                    })
                    .iterator();
            if (i.hasNext()) {
                final BeanDefinition<T> bean = i.next();
                if (i.hasNext()) {
                    // check there are not 2 beans with the same order
                    final BeanDefinition<T> next = i.next();
                    if (OrderUtil.getOrder(bean.getAnnotationMetadata()) == OrderUtil.getOrder(next.getAnnotationMetadata())) {
                        throw new NonUniqueBeanException(beanType.getType(), candidates.iterator());
                    }
                }

                LOG.debug("Picked bean {} with the highest precedence for type {} and qualifier {}", bean, beanType, qualifier);
                return bean;
            }
            throw new NonUniqueBeanException(beanType.getType(), candidates.iterator());
        }

        Collection<BeanDefinition<T>> exactMatches = filterExactMatch(beanClass, candidates);
        if (exactMatches.size() == 1) {
            definition = exactMatches.iterator().next();
        } else if (throwNonUnique) {
            definition = findConcreteCandidate(beanClass, qualifier, candidates);
        }
        return definition;
    }

    private void readAllBeanConfigurations() {
        Iterable<BeanConfiguration> beanConfigurations = resolveBeanConfigurations();
        for (BeanConfiguration beanConfiguration : beanConfigurations) {
            registerConfiguration(beanConfiguration);
        }
    }

    private <T> Collection<BeanDefinition<T>> filterExactMatch(final Class<T> beanType, Collection<BeanDefinition<T>> candidates) {
        List<BeanDefinition<T>> list = new ArrayList<>(candidates.size());
        for (BeanDefinition<T> candidate : candidates) {
            if (candidate.getBeanType() == beanType) {
                list.add(candidate);
            }
        }
        return list;
    }

    private void readAllBeanDefinitionClasses() {
        List<BeanDefinitionProducer> eagerInitBeans = new ArrayList<>(20);
        List<BeanDefinitionProducer> processedBeans = new ArrayList<>(10);
        List<BeanDefinitionProducer> parallelBeans = new ArrayList<>(10);

        List<BeanDefinitionReference> beanDefinitionReferences = resolveBeanDefinitionReferences();

        List<BeanDefinitionProducer> producers = new ArrayList<>(beanDefinitionReferences.size());
        List<BeanDefinitionProducer> proxyTargetBeans = new ArrayList<>(beanDefinitionReferences.size());
        for (BeanDefinitionReference beanDefinitionReference : beanDefinitionReferences) {
            producers.add(new BeanDefinitionProducer(beanDefinitionReference));
        }
        beanDefinitionsClasses.addAll(producers);

        Collection<BeanConfiguration> allConfigurations = beanConfigurations.values();
        List<BeanConfiguration> configurationsDisabled = new ArrayList<>(allConfigurations.size());
        for (BeanConfiguration bc : allConfigurations) {
            if (!bc.isEnabled(this)) {
                configurationsDisabled.add(bc);
            }
        }

        reference:
        for (BeanDefinitionProducer beanDefinitionProducer : producers) {
            if (beanDefinitionProducer.isDisabled()) {
                continue;
            }
            BeanDefinitionReference beanDefinitionReference = beanDefinitionProducer.reference;
            for (BeanConfiguration disableConfiguration : configurationsDisabled) {
                if (disableConfiguration.isWithin(beanDefinitionReference)) {
                    beanDefinitionProducer.referenceEnabled = false;
                    continue reference;
                }
            }

            if (beanDefinitionReference.isProxiedBean()) {
                beanDefinitionProducer.referenceEnabled = false;
                BeanDefinitionProducer proxyBeanProducer = new BeanDefinitionProducer(beanDefinitionReference);
                if (beanDefinitionReference.requiresMethodProcessing()) {
                    processedBeans.add(proxyBeanProducer);
                }
                // retain only if proxy target otherwise the target is never used
                if (beanDefinitionReference.isProxyTarget()) {
                    proxyTargetBeans.add(proxyBeanProducer);
                }
                continue;
            }

            final AnnotationMetadata annotationMetadata = beanDefinitionReference.getAnnotationMetadata();
            Class<?>[] indexes = annotationMetadata.classValues(INDEXES_TYPE);
            if (indexes.length > 0) {
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < indexes.length; i++) {
                    Class<?> indexedType = indexes[i];
                    resolveTypeIndex(indexedType).add(beanDefinitionProducer);
                }
            } else {
                if (annotationMetadata.hasStereotype(ADAPTER_TYPE)) {
                    final Class<?> aClass = annotationMetadata.classValue(ADAPTER_TYPE, AnnotationMetadata.VALUE_MEMBER).orElse(null);
                    if (indexedTypes.contains(aClass)) {
                        resolveTypeIndex(aClass).add(beanDefinitionProducer);
                    }
                }
            }
            if (isEagerInit(beanDefinitionReference)) {
                eagerInitBeans.add(beanDefinitionProducer);
            } else if (annotationMetadata.hasDeclaredStereotype(PARALLEL_TYPE)) {
                parallelBeans.add(beanDefinitionProducer);
            }

            if (beanDefinitionReference.requiresMethodProcessing()) {
                processedBeans.add(beanDefinitionProducer);
            }

        }

        this.beanDefinitionReferences = null;
        this.beanConfigurationsList = null;

        this.proxyTargetBeans.addAll(proxyTargetBeans);

        initializeEventListeners();
        initializeContext(eagerInitBeans, processedBeans, parallelBeans);
    }

    private boolean isEagerInit(BeanDefinitionReference beanDefinitionReference) {
        return beanDefinitionReference.isContextScope() ||
                (eagerInitSingletons && beanDefinitionReference.isSingleton()) ||
                (eagerInitStereotypesPresent && beanDefinitionReference.getAnnotationMetadata().hasDeclaredStereotype(eagerInitStereotypes));
    }

    @NonNull
    private Collection<BeanDefinitionProducer> resolveTypeIndex(Class<?> indexedType) {
        return beanIndex.computeIfAbsent(indexedType, aClass -> {
            indexedTypes.add(indexedType);
            return new ArrayList<>(20);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<BeanDefinition<T>> findBeanCandidatesInternal(BeanResolutionContext resolutionContext, Argument<T> beanType) {
        @SuppressWarnings("rawtypes")
        Collection beanDefinitions = beanCandidateCache.get(beanType);
        if (beanDefinitions == null) {
            beanDefinitions = findBeanCandidates(resolutionContext, beanType, true, null);
            beanCandidateCache.put(beanType, beanDefinitions);
        }
        return beanDefinitions;
    }

    /**
     * Obtains the bean registration for the given type and qualifier.
     *
     * @param resolutionContext The resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The generic type
     * @return A {@link BeanRegistration}
     */
    @Internal
    public <T> BeanRegistration<T> getBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                       @NonNull Argument<T> beanType,
                                                       @Nullable Qualifier<T> qualifier) {
        return resolveBeanRegistration(resolutionContext, beanType, qualifier, true);
    }

    /**
     * Obtains the bean registrations for the given type and qualifier.
     *
     * @param resolutionContext The resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The generic type
     * @return A collection of {@link BeanRegistration}
     */
    @SuppressWarnings("unchecked")
    @Internal
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(@Nullable BeanResolutionContext resolutionContext,
                                                                    @NonNull Argument<T> beanType,
                                                                    @Nullable Qualifier<T> qualifier) {
        boolean hasQualifier = qualifier != null;
        if (LOG.isDebugEnabled()) {
            if (hasQualifier) {
                LOG.debug("Resolving beans for type: {} {} ", qualifier, beanType.getTypeName());
            } else {
                LOG.debug("Resolving beans for type: {}", beanType.getTypeName());
            }
        }

        BeanKey<T> key = new BeanKey<>(beanType, qualifier);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Looking up existing beans for key: {}", key);
        }
        CollectionHolder<T> existing = singletonBeanRegistrations.get(key);
        if (existing != null && existing.registrations != null) {
            logResolvedExistingBeanRegistrations(beanType, qualifier, existing.registrations);
            return existing.registrations;
        }

        Collection<BeanDefinition<T>> beanDefinitions = findBeanCandidatesInternal(resolutionContext, beanType);
        if (!beanDefinitions.isEmpty()) {
            Stream<BeanDefinition<T>> candidateStream = applyBeanResolutionFilters(resolutionContext, beanDefinitions.stream());
            if (qualifier != null) {
                candidateStream = qualifier.reduce(beanType.getType(), candidateStream);
            }
            beanDefinitions = candidateStream.toList();
        }

        Collection<BeanRegistration<T>> beanRegistrations;
        if (beanDefinitions.isEmpty()) {
            beanRegistrations = Collections.emptySet();
        } else {
            boolean allCandidatesAreSingleton = true;
            for (BeanDefinition<T> definition : beanDefinitions) {
                if (!definition.isSingleton()) {
                    allCandidatesAreSingleton = false;
                }
            }
            if (allCandidatesAreSingleton) {
                CollectionHolder<T> holder = singletonBeanRegistrations.computeIfAbsent(key, beanKey -> new CollectionHolder<T>());
                synchronized (holder) {
                    if (holder.registrations != null) {
                        logResolvedExistingBeanRegistrations(beanType, qualifier, holder.registrations);
                        return holder.registrations;
                    }
                    holder.registrations = resolveBeanRegistrations(resolutionContext, beanDefinitions, beanType, qualifier);
                    return holder.registrations;
                }
            } else {
                beanRegistrations = resolveBeanRegistrations(resolutionContext, beanDefinitions, beanType, qualifier);
            }
        }
        if (LOG.isDebugEnabled() && !beanRegistrations.isEmpty()) {
            if (hasQualifier) {
                LOG.debug("Found {} bean registrations for type [{} {}]", beanRegistrations.size(), qualifier, beanType.getName());
            } else {
                LOG.debug("Found {} bean registrations for type [{}]", beanRegistrations.size(), beanType.getName());
            }
            for (BeanRegistration<?> beanRegistration : beanRegistrations) {
                LOG.debug("  {} {}", beanRegistration.definition(), beanRegistration.definition().getDeclaredQualifier());
            }
        }
        return beanRegistrations;
    }

    private <T> Collection<BeanRegistration<T>> resolveBeanRegistrations(BeanResolutionContext resolutionContext,
                                                                         Collection<BeanDefinition<T>> beanDefinitions,
                                                                         Argument<T> beanType,
                                                                         Qualifier<T> qualifier) {
        boolean hasOrderAnnotation = false;
        Set<BeanRegistration<T>> beansOfTypeList = new HashSet<>();
        for (BeanDefinition<T> definition : beanDefinitions) {
            if (!hasOrderAnnotation && definition.hasAnnotation(Order.class)) {
                hasOrderAnnotation = true;
            }
            addCandidateToList(resolutionContext, definition, beanType, qualifier, beansOfTypeList);
        }
        if (beansOfTypeList != Collections.EMPTY_SET) {
            if (Ordered.class.isAssignableFrom(beanType.getType())) {
                return beansOfTypeList.stream().sorted(OrderUtil.COMPARATOR).toList();
            }
            if (hasOrderAnnotation) {
                return beansOfTypeList.stream().sorted(BEAN_REGISTRATION_COMPARATOR).toList();
            }
        }
        return beansOfTypeList;
    }

    private <T> void logResolvedExistingBeanRegistrations(Argument<T> beanType, Qualifier<T> qualifier, Collection<BeanRegistration<T>> existing) {
        if (LOG.isDebugEnabled()) {
            if (qualifier == null) {
                LOG.debug("Found {} existing beans for type [{}]: {} ", existing.size(), beanType.getName(), existing);
            } else {
                LOG.debug("Found {} existing beans for type [{} {}]: {} ", existing.size(), qualifier, beanType.getName(), existing);
            }
        }
    }

    private <T> Stream<BeanDefinition<T>> applyBeanResolutionFilters(@Nullable BeanResolutionContext resolutionContext, Stream<BeanDefinition<T>> candidateStream) {
        BeanResolutionContext.Segment<?, ?> segment = resolutionContext != null ? resolutionContext.getPath().peek() : null;
        if (segment instanceof AbstractBeanResolutionContext.ConstructorSegment || segment instanceof AbstractBeanResolutionContext.MethodSegment) {
            BeanDefinition<?> declaringBean = segment.getDeclaringType();
            // if the currently injected segment is a constructor argument and the type to be constructed is the
            // same as the candidate, then filter out the candidate to avoid a circular injection problem
            candidateStream = candidateStream.filter(c -> {
                if (c.equals(declaringBean)) {
                    return false;
                } else if (declaringBean instanceof ProxyBeanDefinition<?> proxyBeanDefinition) {
                    return !proxyBeanDefinition.getTargetDefinitionType().equals(c.getClass());
                }
                return true;
            });
        }
        return candidateStream.filter(c -> !c.isAbstract());
    }

    private <T> void addCandidateToList(@Nullable BeanResolutionContext resolutionContext,
                                        @NonNull BeanDefinition<T> candidate,
                                        @NonNull Argument<T> beanType,
                                        @Nullable Qualifier<T> qualifier,
                                        @NonNull Collection<BeanRegistration<T>> beansOfTypeList) {
        BeanRegistration<T> beanRegistration = null;
        try {
            beanRegistration = resolveBeanRegistration(resolutionContext, candidate);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found a registration {} for candidate: {} with qualifier: {}", beanRegistration, candidate, qualifier);
            }
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.ConditionLog.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.ConditionLog.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getTypeName(), e.getMessage());
            }
        }

        if (beanRegistration != null) {
            if (candidate.isContainerType()) {
                Object container = beanRegistration.bean;
                if (container instanceof Object[] array) {
                    container = Arrays.asList(array);
                }
                if (container instanceof Iterable iterable) {
                    int i = 0;
                    for (Object o : iterable) {
                        if (o == null || !beanType.isInstance(o)) {
                            continue;
                        }
                        beansOfTypeList.add(BeanRegistration.of(
                            this,
                            new BeanKey<>(beanType, Qualifiers.byQualifiers(Qualifiers.byName(String.valueOf(i++)), qualifier)),
                            candidate,
                            (T) o
                        ));
                    }
                }
            } else {
                beansOfTypeList.add(beanRegistration);
            }
        }
    }

    private <T> boolean isCandidatePresent(Argument<T> beanType, Qualifier<T> qualifier) {
        final Collection<BeanDefinition<T>> candidates = findBeanCandidates(null, beanType, true, null);
        if (!candidates.isEmpty()) {
            filterReplacedBeans(null, candidates);
            Stream<BeanDefinition<T>> stream = candidates.stream();
            if (qualifier != null && !(qualifier instanceof AnyQualifier)) {
                stream = qualifier.reduce(beanType.getType(), stream);
            }
            return stream.findAny().isPresent();
        }
        return false;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    private List<BeanRegistration> topologicalSort(Collection<BeanRegistration> beans) {
        Map<Boolean, List<BeanRegistration>> initial = beans.stream()
                .sorted(Comparator.comparing(s -> s.getBeanDefinition().getRequiredComponents().size()))
                .collect(Collectors.groupingBy(b -> b.getBeanDefinition().getRequiredComponents().isEmpty()));
        List<BeanRegistration> sorted = new ArrayList<>(nullSafe(initial.get(true)));
        List<BeanRegistration> unsorted = new ArrayList<>(nullSafe(initial.get(false)));
        // Optimization which knows about types which are already in the sorted list
        Set<Class<?>> satisfied = new HashSet<>();

        // Optimization for types which we know are already unsatisified
        // in a single iteration, allowing to skip the loop on unsorted elements
        Set<Class<?>> unsatisfied = new HashSet<>();

        //loop until all items have been sorted
        while (!unsorted.isEmpty()) {
            boolean acyclic = false;

            unsatisfied.clear();
            Iterator<BeanRegistration> i = unsorted.iterator();
            while (i.hasNext()) {
                BeanRegistration bean = i.next();
                boolean found = false;

                //determine if any components are in the unsorted list
                Collection<Class<?>> components = bean.getBeanDefinition().getRequiredComponents();
                for (Class<?> clazz : components) {
                    if (satisfied.contains(clazz)) {
                        continue;
                    }
                    if (unsatisfied.contains(clazz) || unsorted.stream()
                            .map(BeanRegistration::getBeanDefinition)
                            .map(BeanDefinition::getBeanType)
                            .anyMatch(clazz::isAssignableFrom)) {
                        found = true;
                        unsatisfied.add(clazz);
                        break;
                    }
                    satisfied.add(clazz);
                }

                //none of the required components are in the unsorted list,
                //so it can be added to the sorted list
                if (!found) {
                    acyclic = true;
                    i.remove();
                    sorted.add(0, bean);
                }
            }

            //rather than throw an exception here because there is a cyclical dependency
            //just add the first item to the list and keep trying. It may be possible to
            //see a cycle here because qualifiers are not taken into account.
            if (!acyclic) {
                sorted.add(0, unsorted.remove(0));
            }
        }

        return sorted;
    }

    @NonNull
    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return MutableConvertibleValues.of(attributes);
    }

    @NonNull
    @Override
    public Optional<Object> getAttribute(CharSequence name) {
        if (name != null) {
            return Optional.ofNullable(attributes.get(name));
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    @Override
    public <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        if (name != null) {
            final Object o = attributes.get(name);
            if (type.isInstance(o)) {
                return Optional.of((T) o);
            } else if (o != null) {
                return getConversionService().convert(o, type);
            }
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public BeanContext setAttribute(@NonNull CharSequence name, @Nullable Object value) {
        if (name != null) {
            if (value != null) {
                attributes.put(name, value);
            } else {
                attributes.remove(name);
            }
        }
        return this;
    }

    @NonNull
    @Override
    public <T> Optional<T> removeAttribute(@NonNull CharSequence name, @NonNull Class<T> type) {
        final Object o = attributes.remove(name);
        if (type.isInstance(o)) {
            return Optional.of((T) o);
        }
        return Optional.empty();
    }

    @Override
    public void finalizeConfiguration() {
        readAllBeanConfigurations();
        readAllBeanDefinitionClasses();
    }

    @Override
    public MutableConversionService getConversionService() {
        return conversionService;
    }

    /**
     * @param <T> The type
     * @param <R> The return type
     */
    private abstract static class AbstractExecutionHandle<T, R> implements MethodExecutionHandle<T, R> {
        protected final ExecutableMethod<T, R> method;

        /**
         * @param method The method
         */
        AbstractExecutionHandle(ExecutableMethod<T, R> method) {
            this.method = method;
        }

        @NonNull
        @Override
        public ExecutableMethod<T, R> getExecutableMethod() {
            return method;
        }

        @Override
        public Argument[] getArguments() {
            return method.getArguments();
        }

        @Override
        public String toString() {
            return method.toString();
        }

        @Override
        public String getMethodName() {
            return this.method.getMethodName();
        }

        @Override
        public ReturnType<R> getReturnType() {
            return method.getReturnType();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return method.getAnnotationMetadata();
        }
    }

    /**
     * @param <T> The targe type
     * @param <R> The return type
     */
    private static final class ObjectExecutionHandle<T, R> extends AbstractExecutionHandle<T, R> implements UnsafeExecutionHandle<T, R> {

        @Nullable
        private final UnsafeExecutable<T, R> unsafeExecutable;
        private final T target;

        /**
         * @param target The target type
         * @param method The method
         */
        ObjectExecutionHandle(T target, ExecutableMethod<T, R> method) {
            super(method);
            this.target = target;
            if (method instanceof UnsafeExecutable unsafeExecutable) {
                this.unsafeExecutable = unsafeExecutable;
            } else {
                this.unsafeExecutable = null;
            }
        }

        @Override
        public T getTarget() {
            return target;
        }

        @Override
        public R invoke(Object... arguments) {
            return method.invoke(target, arguments);
        }

        @Override
        public R invokeUnsafe(Object... arguments) {
            if (unsafeExecutable == null) {
                return invoke(arguments);
            }
            return unsafeExecutable.invokeUnsafe(target, arguments);
        }

        @Override
        public Method getTargetMethod() {
            return method.getTargetMethod();
        }

        @Override
        public Class getDeclaringType() {
            return target.getClass();
        }

    }

    /**
     * @param <T>
     * @param <R>
     */
    private static final class BeanExecutionHandle<T, R> extends AbstractExecutionHandle<T, R> {
        private final BeanContext beanContext;
        private final Class<T> beanType;
        private final Qualifier<T> qualifier;
        private final boolean isSingleton;

        private T target;

        /**
         * @param beanContext The bean context
         * @param beanType    The bean type
         * @param qualifier   The qualifier
         * @param method      The method
         */
        BeanExecutionHandle(BeanContext beanContext, Class<T> beanType, Qualifier<T> qualifier, ExecutableMethod<T, R> method) {
            super(method);
            this.beanContext = beanContext;
            this.beanType = beanType;
            this.qualifier = qualifier;
            this.isSingleton = beanContext.findBeanDefinition(beanType, qualifier).map(BeanDefinition::isSingleton).orElse(false);
        }

        @Override
        public T getTarget() {
            T target = this.target;
            if (target == null) {
                synchronized (this) { // double check
                    target = this.target;
                    if (target == null) {
                        target = beanContext.getBean(beanType, qualifier);
                        this.target = target;
                    }
                }
            }
            return target;
        }

        @Override
        public Method getTargetMethod() {
            return method.getTargetMethod();
        }

        @Override
        public Class getDeclaringType() {
            return beanType;
        }

        @Override
        public R invoke(Object... arguments) {
            if (isSingleton) {
                T target = getTarget();

                return method.invoke(target, arguments);
            } else {
                return method.invoke(beanContext.getBean(beanType, qualifier), arguments);
            }
        }
    }

    /**
     * Internal supplier of listeners.
     *
     * @param <T> The listener type
     *
     * @author Denis Stepanov
     * @since 4.0.0
     */
    @Internal
    interface ListenersSupplier<T extends EventListener> {

        /**
         * Retrieved the listeners lazily.
         *
         * @param beanResolutionContext The bean resolution context
         * @return the collection of listeners
         */
        @NonNull
        Iterable<T> get(@Nullable BeanResolutionContext beanResolutionContext);

    }

    /**
     * Class used as a bean key.
     *
     * @param <T> The bean type
     */
    @SuppressWarnings("java:S1948")
    static final class BeanKey<T> implements BeanIdentifier {
        final Argument<T> beanType;
        private final Qualifier<T> qualifier;
        private final int hashCode;

        /**
         * A bean key for the given bean definition.
         *
         * @param definition The definition
         * @param qualifier  The qualifier
         */
        BeanKey(BeanDefinition<T> definition, Qualifier<T> qualifier) {
            this(definition.asArgument(), qualifier);
        }

        /**
         * A bean key for the given bean definition.
         *
         * @param argument  The argument
         * @param qualifier The qualifier
         */
        BeanKey(Argument<T> argument, Qualifier<T> qualifier) {
            this.beanType = argument;
            this.qualifier = qualifier;
            this.hashCode = argument.typeHashCode();
        }

        /**
         * @param beanType      The bean type
         * @param qualifier     The qualifier
         * @param typeArguments The type arguments
         */
        BeanKey(Class<T> beanType, Qualifier<T> qualifier, @Nullable Class<?>... typeArguments) {
            this(Argument.of(beanType, typeArguments), qualifier);
        }

        @Override
        public int length() {
            return toString().length();
        }

        @Override
        public char charAt(int index) {
            return toString().charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toString().subSequence(start, end);
        }

        @Override
        public String toString() {
            return (qualifier != null ? qualifier + " " : "") + beanType.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BeanKey<?> beanKey = (BeanKey<?>) o;
            return beanType.equalsType(beanKey.beanType) &&
                    Objects.equals(qualifier, beanKey.qualifier);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String getName() {
            if (qualifier instanceof Named named) {
                return named.getName();
            }
            return Primary.SIMPLE_NAME;
        }
    }

    /**
     * Class used as a bean candidate key.
     *
     * @param <T> The bean candidate type
     */
    static final class BeanCandidateKey<T> {
        private final Argument<T> beanType;
        private final Qualifier<T> qualifier;
        private final boolean throwNonUnique;
        private final int hashCode;

        /**
         * A bean key for the given bean definition.
         *
         * @param argument       The argument
         * @param qualifier      The qualifier
         * @param throwNonUnique The throwNonUnique
         */
        BeanCandidateKey(Argument<T> argument, Qualifier<T> qualifier, boolean throwNonUnique) {
            this.beanType = argument;
            this.qualifier = qualifier;
            this.hashCode = argument.typeHashCode();
            this.throwNonUnique = throwNonUnique;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BeanCandidateKey<?> beanKey = (BeanCandidateKey<?>) o;
            return beanType.equalsType(beanKey.beanType) &&
                    Objects.equals(qualifier, beanKey.qualifier) && throwNonUnique == beanKey.throwNonUnique;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

    }

    private class SingletonBeanResolutionContext extends AbstractBeanResolutionContext {

        public SingletonBeanResolutionContext(BeanDefinition<?> beanDefinition) {
            super(DefaultBeanContext.this, beanDefinition);
        }

        @Override
        public BeanResolutionContext copy() {
            SingletonBeanResolutionContext copy = new SingletonBeanResolutionContext(rootDefinition);
            copy.copyStateFrom(this);
            return copy;
        }

        @Override
        public <T> void addInFlightBean(BeanIdentifier beanIdentifier, BeanRegistration<T> beanRegistration) {
            singlesInCreation.put(beanIdentifier, beanRegistration);
        }

        @Override
        public void removeInFlightBean(BeanIdentifier beanIdentifier) {
            singlesInCreation.remove(beanIdentifier);
        }

        @Nullable
        @Override
        public <T> BeanRegistration<T> getInFlightBean(BeanIdentifier beanIdentifier) {
            return (BeanRegistration<T>) singlesInCreation.get(beanIdentifier);
        }
    }

    private static final class CollectionHolder<T> {
        Collection<BeanRegistration<T>> registrations;
    }

    /**
     * The class adds the caching of the enabled decision + the definition instance.
     * NOTE: The class can be accessed in multiple threads, we do allow for the fields to be possibly initialized concurrently - multiple times.
     *
     * @since 4.0.0
     */
    @Internal
    static final class BeanDefinitionProducer {

        @Nullable
        @SuppressWarnings("java:S3077")
        private volatile BeanDefinitionReference reference;
        @Nullable
        @SuppressWarnings("java:S3077")
        private volatile BeanDefinition definition;
        @Nullable
        private volatile Boolean referenceEnabled;
        @Nullable
        private volatile Boolean definitionEnabled;

        BeanDefinitionProducer(@NonNull BeanDefinitionReference reference) {
            this.reference = reference;
        }

        public boolean isReferenceEnabled(DefaultBeanContext context) {
            return isReferenceEnabled(context, null);
        }

        public boolean isReferenceEnabled(DefaultBeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            BeanDefinitionReference<?> ref = reference;
            // The reference needs to be assigned to a new variable as it can change between checks
            if (ref == null) {
                return false;
            }
            if (referenceEnabled == null) {
                if (ref.isEnabled(context, resolutionContext)) {
                    referenceEnabled = true;
                } else {
                    referenceEnabled = false;
                    reference = null;
                }
            }
            return referenceEnabled;
        }

        public boolean isDisabled() {
            if (reference == null) {
                return true;
            }
            Boolean refEnabled = referenceEnabled;
            // The reference needs to be assigned to a new variable as it can change between checks
            if (refEnabled != null && !refEnabled) {
                return true;
            }
            Boolean defEnabled = definitionEnabled;
            // The reference needs to be assigned to a new variable as it can change between checks
            return defEnabled != null && !defEnabled;
        }

        public boolean isDefinitionEnabled(DefaultBeanContext defaultBeanContext) {
            return isDefinitionEnabled(defaultBeanContext, null);
        }

        public boolean isDefinitionEnabled(DefaultBeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            if (definitionEnabled == null) {
                if (isReferenceEnabled(context, resolutionContext)) {
                    BeanDefinition <?> def = getDefinition(context);
                    if (def.isEnabled(context, resolutionContext)) {
                        definition = def;
                        definitionEnabled = true;
                    } else {
                        definitionEnabled = false;
                    }
                } else {
                    definitionEnabled = false;
                }
            }
            return definitionEnabled;
        }

        public <T> BeanDefinitionReference<T> getReference() {
            // The reference needs to be assigned to a new variable as it can change between checks
            Boolean refEnabled = referenceEnabled;
            if (reference == null || refEnabled == null || !refEnabled) {
                throw new IllegalStateException("The reference is not enabled");
            }
            return reference;
        }

        public <T> BeanDefinition<T> getDefinition(BeanContext beanContext) {
            // The reference needs to be assigned to a new variable as it can change between checks
            Boolean defEnabled = definitionEnabled;
            if (defEnabled != null && !defEnabled) {
                throw new IllegalStateException("The definition is not enabled");
            }
            try {
                BeanDefinition def = definition;
                if (def == null) {
                    def = getReference().load(beanContext);
                    definition = def;
                }
                return def;
            } catch (Throwable e) {
                throw new BeanInstantiationException(MSG_BEAN_DEFINITION + reference.getName() + MSG_COULD_NOT_BE_LOADED + e.getMessage(), e);
            }
        }

        public <T> boolean isReferenceCandidateBean(Argument<T> beanType) {
            // The reference needs to be assigned to a new variable as it can change between checks
            BeanDefinitionReference ref = reference;
            return ref != null && ref.isCandidateBean(beanType);
        }

        public void disable(BeanDefinitionReference<?> reference) {
            // The reference needs to be assigned to a new variable as it can change between checks
            BeanDefinitionReference ref = this.reference;
            if (ref != null && ref.equals(reference)) {
                this.reference = null;
            }
        }
    }

    private final class BeanContextUnsafeExecutionHandle extends BeanContextExecutionHandle implements UnsafeExecutionHandle<Object, Object> {

        private final UnsafeExecutable<Object, Object> unsafeExecutionHandle;

        public BeanContextUnsafeExecutionHandle(ExecutableMethod<Object, ?> method, BeanDefinition<?> beanDefinition, UnsafeExecutable<Object, Object> unsafeExecutionHandle) {
            super(method, beanDefinition);
            this.unsafeExecutionHandle = unsafeExecutionHandle;
        }

        @Override
        public Object invokeUnsafe(Object... arguments) {
            return unsafeExecutionHandle.invokeUnsafe(getTarget(), arguments);
        }

        @Override
        public String toString() {
            return unsafeExecutionHandle.toString();
        }
    }

    private sealed class BeanContextExecutionHandle implements MethodExecutionHandle<Object, Object> {

        private final ExecutableMethod<Object, ?> method;
        private final BeanDefinition<?> beanDefinition;
        private Object target;

        public BeanContextExecutionHandle(ExecutableMethod<Object, ?> method, BeanDefinition<? extends Object> beanDefinition) {
            this.method = method;
            this.beanDefinition = beanDefinition;
        }

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return method.getAnnotationMetadata();
        }

        @Override
        public Object getTarget() {
            Object target = this.target;
            if (target == null) {
                synchronized (this) { // double check
                    target = this.target;
                    if (target == null) {
                        target = getBean(beanDefinition);
                        this.target = target;
                    }
                }
            }
            return target;
        }

        @Override
        public Class getDeclaringType() {
            return beanDefinition.getBeanType();
        }

        @Override
        public String getMethodName() {
            return method.getMethodName();
        }

        @Override
        public Argument[] getArguments() {
            return method.getArguments();
        }

        @Override
        public Method getTargetMethod() {
            return method.getTargetMethod();
        }

        @Override
        public ReturnType getReturnType() {
            return method.getReturnType();
        }

        @Override
        public Object invoke(Object... arguments) {
            return method.invoke(getTarget(), arguments);
        }

        @NonNull
        @Override
        public ExecutableMethod<Object, Object> getExecutableMethod() {
            return (ExecutableMethod<Object, Object>) method;
        }

        @Override
        public String toString() {
            return method.toString();
        }
    }
}
