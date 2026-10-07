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
import io.micronaut.context.annotation.DependsOn;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.beans.BeanDefinitionService;
import io.micronaut.context.beans.DefaultBeanDefinitionService;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.Failure;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.context.env.PropertyPlaceholderResolver;
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
import io.micronaut.context.exceptions.ConstructorAdviceException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.context.scope.CustomScopeRegistry;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
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
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DelegatingBeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanIdentifier;
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
import io.micronaut.inject.ReplacesDefinition;
import io.micronaut.inject.UnsafeExecutionHandle;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.provider.AbstractProviderDefinition;
import io.micronaut.inject.proxy.InterceptedBean;
import io.micronaut.inject.proxy.InterceptedBeanProxy;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.FilteringQualifier;
import io.micronaut.inject.qualifiers.Qualified;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.qualifiers.TypeArgumentQualifier;
import io.micronaut.inject.validation.BeanDefinitionValidator;
import org.jspecify.annotations.Nullable;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The default context implementations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@NextMajorVersion("Remove public in v6")
@SuppressWarnings("MagicNumber")
public sealed class DefaultBeanContext implements ConfigurableBeanContext permits DefaultApplicationContext {

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultBeanContext.class);
    protected static final Logger LOG_LIFECYCLE = LoggerFactory.getLogger(DefaultBeanContext.class.getPackage().getName() + ".lifecycle");
    private static final String SCOPED_PROXY_ANN = "io.micronaut.runtime.context.scope.ScopedProxy";
    private static final String ARGUMENT_DEFINITION = "definition";
    private static final String AROUND_TYPE = "io.micronaut.aop.Around";
    private static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction";
    /**
     * The maximum number of additional destruction passes performed during {@link #stop()} to destroy
     * singletons created by {@code @PreDestroy} hooks, bounding a hook that always creates a new bean.
     */
    private static final int MAX_SHUTDOWN_PASSES = 10;

    private static final Predicate<BeanDefinition<?>> FILTER_OUT_ANY_PROVIDERS = new Predicate<BeanDefinition<?>>() { // Keep anonymous for hot path
        @Override
        public boolean test(BeanDefinition<?> candidate) {
            return candidate.getDeclaredQualifier() == null || !candidate.getDeclaredQualifier().equals(AnyQualifier.INSTANCE);
        }
    };

    private static final String MSG_COULD_NOT_BE_LOADED = "] could not be loaded: ";
    public static final String MSG_BEAN_DEFINITION = "Bean definition [";

    private static final String PARALLEL_BEAN_DISCOVERY_THREAD = "micronaut-parallel-bean-discovery";

    /**
     * How long {@link #stop()} waits for the parallel bean discovery thread and any in-flight
     * parallel bean initializations before it proceeds to destroy the singletons.
     */
    private static final long PARALLEL_SHUTDOWN_TIMEOUT_MS = 10_000L;

    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean configured = new AtomicBoolean(false);
    protected final AtomicBoolean initializing = new AtomicBoolean(false);
    protected final AtomicBoolean terminating = new AtomicBoolean(false);
    /**
     * The thread running {@link #stop()} until the singletons are destroyed. Lookups made on it are made on behalf
     * of a shutdown event listener or a destruction callback, and may resolve through existing dependency groups.
     */
    @SuppressWarnings("java:S3077") // only the reference is published and compared with the current thread
    private volatile @Nullable Thread shutdownThread;
    /** The thread publishing the {@link ShutdownEvent}, whose listeners may open new dependency groups. */
    @SuppressWarnings("java:S3077") // only the reference is published and compared with the current thread
    private volatile @Nullable Thread shutdownEventThread;
    /** What dependency groups created during shutdown, destroyed before it completes. Confined to the shutdown thread. */
    private final List<ShutdownDependent> shutdownDependents = new ArrayList<>();

    final BeanResolutionTraceMode traceMode;
    final Set<String> tracePatterns;
    final Map<BeanIdentifier, BeanRegistration<?>> singlesInCreation = new ConcurrentHashMap<>(5);

    protected final SingletonScope singletonScope = new SingletonScope();

    private final BeanContextConfiguration beanContextConfiguration;

    private final Map<String, List<String>> disabledConfigurations = new ConcurrentHashMap<>(5);
    private final Map<String, BeanConfiguration> beanConfigurations = new HashMap<>(10);

    final Map<BeanKey, Boolean> containsBeanCache = new ConcurrentHashMap<>(30);
    private final Map<CharSequence, Object> attributes = Collections.synchronizedMap(new HashMap<>(5));

    private final Map<BeanKey, CollectionHolder> singletonBeanRegistrations = new ConcurrentHashMap<>(50);

    private final Map<BeanCandidateKey, Optional<BeanDefinition>> beanConcreteCandidateCache =
        new ConcurrentLinkedHashMap.Builder<BeanCandidateKey, Optional<BeanDefinition>>().maximumWeightedCapacity(30).build();

    private final Map<BeanCandidateKey, Optional<BeanDefinition>> beanProxyTargetCache =
        new ConcurrentLinkedHashMap.Builder<BeanCandidateKey, Optional<BeanDefinition>>().maximumWeightedCapacity(30).build();

    private final Map<Argument, Collection<BeanDefinition>> beanCandidateCache = new ConcurrentLinkedHashMap.Builder<Argument, Collection<BeanDefinition>>().maximumWeightedCapacity(30).build();

    /**
     * Whether the compile-time index of a self-indexed annotation holds every bean carrying it, by annotation type,
     * with the {@link #beanDefinitionsEpoch} it was computed at.
     */
    private final Map<Argument<?>, IndexExhaustiveness> indexExhaustiveCache = new ConcurrentHashMap<>(5);

    /**
     * Advanced after bean definitions are added, so that an index exhaustiveness computed before is not used.
     */
    private final AtomicLong beanDefinitionsEpoch = new AtomicLong();

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

    private final CustomScopeRegistry customScopeRegistry;
    // the interceptors of targets this context holds no registration for, by the definition of the target
    private final BeanResolutionCustomizer beanResolutionCustomizer;
    private final RuntimeBeanDefinition<BeanDependencyResolver> dependencyResolverDefinition = RuntimeBeanDefinition
        .<BeanDependencyResolver>builder(BeanDependencyResolver.class, () -> new DefaultBeanDependencyResolver(this))
        .build();

    private @Nullable BeanDefinitionValidator beanValidator;
    private @Nullable List<BeanConfiguration> beanConfigurationsList;

    @Nullable List<Map.Entry<Class<?>, ListenersSupplier<BeanInitializedEventListener>>> beanInitializedEventListeners;
    private @Nullable List<Map.Entry<Class<?>, ListenersSupplier<BeanCreatedEventListener>>> beanCreationEventListeners;
    private @Nullable List<Map.Entry<Class<?>, ListenersSupplier<BeanPreDestroyEventListener>>> beanPreDestroyEventListeners;
    private @Nullable List<Map.Entry<Class<?>, ListenersSupplier<BeanDestroyedEventListener>>> beanDestroyedEventListeners;

    private final boolean eventsEnabled;
    private final boolean eagerBeansEnabled;
    /**
     * The recorded dependency graph, null when the context does not track dependencies. Decided by the configuration
     * as the context is constructed, or by its environment as it starts (see {@link #isBeanDependencyTrackingEnabledOnStart()}),
     * in both cases before the context creates a bean; it only ever goes from null to a graph.
     */
    @Nullable
    private DefaultBeanDependencyGraph dependencyGraph;
    /**
     * Registrations of a previous context to adopt on the first start, released once adopted so that
     * neither the previous context nor its classloader stays reachable through them.
     */
    private Collection<BeanRegistration<?>> registrationsToAdopt;
    /**
     * While a {@link #stopRetaining(RetentionCriteria)} is in progress, which registrations it keeps.
     */
    @Nullable
    private volatile RetentionCriteria retentionCriteria;
    private final List<BeanRegistration<?>> retainedOnStop = new ArrayList<>();
    /**
     * Retained registrations this context has no definition for, destroyed once its listeners exist.
     */
    private final List<BeanRegistration<?>> rejectedRetainedRegistrations = new ArrayList<>();
    /**
     * Instances adopted under at least one registration, so a rejection under another does not destroy them.
     */
    private Set<Object> adoptedRetainedBeans = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * While retained registrations are adopted, the instances this context adopts, by identity.
     */
    private Set<Object> adoptingRetainedBeans = Set.of();
    /**
     * Adopted registrations of instances that bean created listeners replaced in the previous context, to wrap again
     * with this context's listeners once they are known.
     */
    private List<BeanRegistration<?>> adoptedToWrap = new ArrayList<>();

    private @Nullable ForkJoinTask<?> checkEnabledBeans;

    /**
     * The thread that discovers {@link Parallel} beans, retained so that {@link #stop()} can
     * interrupt and join it instead of letting it outlive the context. An {@link AtomicReference}
     * so that the shutdown claims the thread and clears the field in one step, and a discovery
     * started concurrently cannot have its thread dropped without being joined.
     */
    private final AtomicReference<Thread> parallelBeanDiscoveryThread = new AtomicReference<>();

    /**
     * The in-flight parallel bean initializations. Each entry is completed by its worker when the
     * initialization finishes, successfully or not.
     */
    private final Set<ParallelInitialization> parallelInitializationTasks = ConcurrentHashMap.newKeySet();

    /**
     * The parallel initialization a worker thread is currently running, so that a shutdown
     * triggered from that worker does not wait for the worker itself.
     */
    private final ThreadLocal<ParallelInitialization> currentParallelInitialization = new ThreadLocal<>();

    protected MutableConversionService conversionService;

    protected final BeanDefinitionService beanDefinitionProvider;

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
    public DefaultBeanContext(ClassLoader classLoader) {
        this(new BeanContextConfiguration() {
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
    public DefaultBeanContext(ClassPathResourceLoader resourceLoader) {
        this(new BeanContextConfiguration() {
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
    public DefaultBeanContext(BeanContextConfiguration contextConfiguration) {
        ArgumentUtils.requireNonNull("contextConfiguration", contextConfiguration);
        // enable classloader logging
        System.setProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING, "true");
        this.classLoader = contextConfiguration.getClassLoader();
        this.beanContextConfiguration = contextConfiguration;
        this.beanResolutionCustomizer = Objects.requireNonNull(contextConfiguration.beanResolutionCustomizer(), "Bean resolution customizer cannot be null");
        this.customScopeRegistry = Objects.requireNonNull(createCustomScopeRegistry(), "Scope registry cannot be null");
        Set<Class<? extends Annotation>> eagerInitAnnotated = contextConfiguration.getEagerInitAnnotated();
        List<String> configuredEagerSingletonAnnotations = new ArrayList<>(eagerInitAnnotated.size());
        for (Class<? extends Annotation> ann : eagerInitAnnotated) {
            configuredEagerSingletonAnnotations.add(ann.getName());
        }
        BeanResolutionTraceConfiguration traceConfiguration = beanContextConfiguration
            .getTraceConfiguration();
        this.traceMode = traceConfiguration.mode();
        this.tracePatterns = traceConfiguration.classPatterns();
        this.eventsEnabled = contextConfiguration.eventsEnabled();
        this.eagerBeansEnabled = contextConfiguration.eagerBeansEnabled();
        this.dependencyGraph = contextConfiguration.beanDependencyTrackingEnabled() ? new DefaultBeanDependencyGraph() : null;
        this.registrationsToAdopt = List.copyOf(contextConfiguration.getRetainedRegistrations());
        this.conversionService = MutableConversionService.create();
        beanDefinitionProvider = new DefaultBeanDefinitionService(beanContextConfiguration);
    }

    /**
     * Allows customizing the custom scope registry.
     *
     * @return The custom scope registry to use.
     * @since 3.0.0
     */
    protected CustomScopeRegistry createCustomScopeRegistry() {
        CustomScopeRegistryFactory factory = beanContextConfiguration.customScopeRegistryFactory();
        if (factory != null) {
            return Objects.requireNonNull(factory.create(this), "Custom scope registry cannot be null");
        }
        return new DefaultCustomScopeRegistry(this);
    }

    /**
     * @return The custom scope registry
     */
    @Internal
    CustomScopeRegistry getCustomScopeRegistry() {
        return customScopeRegistry;
    }

    BeanResolutionCustomizer getBeanResolutionCustomizer() {
        return beanResolutionCustomizer;
    }

    @SuppressWarnings("unchecked")
    private <T> Argument<T> resolveBeanLookupArgument(Argument<T> beanType) {
        Argument<?> resolvedBeanType = beanResolutionCustomizer.resolveBeanLookupArgument(beanType);
        return (Argument<T>) Objects.requireNonNull(resolvedBeanType, "Resolved bean lookup argument cannot be null");
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
                if (dependencyGraph == null && isBeanDependencyTrackingEnabledOnStart()) {
                    // development mode was switched on by configuration rather than by system property: the
                    // environment is started and no bean has been created yet, so the graph sees every bean
                    dependencyGraph = new DefaultBeanDependencyGraph();
                }
                configureAndStartContext();
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
     * Whether the context, which was not configured to track bean dependencies, tracks them after all as it
     * starts, before it reads its definitions and creates any bean. An application context does so when its
     * environment switches development mode on by configuration.
     *
     * @return True to start recording the dependency graph
     */
    boolean isBeanDependencyTrackingEnabledOnStart() {
        return false;
    }

    /**
     * Registers conversion service.
     */
    protected void registerConversionService() {
        //noinspection resource
        registerSingleton(MutableConversionService.class, conversionService, null, false);
    }

    /**
     * Tracks when a bean or configuration is disabled.
     *
     * @param conditionContext The conditional context
     * @param <C>              The component type
     */
    @Internal
    <C extends AnnotationMetadataProvider> void trackDisabledComponent(ConditionContext<C> conditionContext) {
        C component = conditionContext.getComponent();
        List<String> reasons = conditionContext.getFailures().stream().map(Failure::getMessage).toList();
        if (component instanceof QualifiedBeanType<?> beanType) {
            beanDefinitionProvider.trackDisabled(beanType, reasons);
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
        // Only mark as terminating if a shutdown is actually going to run, otherwise the flag would be left set forever
        if (isRunning() && terminating.compareAndSet(false, true)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping BeanContext");
            }
            shutdownThread = Thread.currentThread();
            try {
                shutdownEventThread = Thread.currentThread();
                try {
                    publishEvent(new ShutdownEvent(this));
                } finally {
                    shutdownEventThread = null;
                }
                attributes.clear();

                // wait for parallel bean startup to finish so that the singletons it creates are
                // included in the destruction pass below instead of being registered behind it
                awaitParallelStartupTermination();

                // dedup by identity: identity hash codes are not unique across live objects
                Set<Object> processed = Collections.newSetFromMap(new IdentityHashMap<>());
                destroySingletons(singletonScope.getBeanRegistrations(), processed);

                // a @PreDestroy hook is free to resolve beans, which may create brand-new singletons
                // registered after the snapshot above was taken, or dependents of a group that outlives
                // the context. Destroy those stragglers too, with a bound so a pathological hook that
                // always creates a bean cannot spin forever
                for (int pass = 0; ; pass++) {
                    destroyShutdownDependents();
                    List<BeanRegistration> stragglers = singletonScope.getBeanRegistrations()
                        .stream()
                        .filter(br -> !processed.contains(br.bean))
                        .toList();
                    if (stragglers.isEmpty() && shutdownDependents.isEmpty()) {
                        break;
                    }
                    if (pass == MAX_SHUTDOWN_PASSES) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Beans are still being created during shutdown after {} destruction passes. "
                                + "Giving up, {} bean(s) will not be destroyed.", MAX_SHUTDOWN_PASSES,
                                stragglers.size() + shutdownDependents.size());
                        }
                        shutdownDependents.clear();
                        break;
                    }
                    destroySingletons(stragglers, processed);
                }
            } finally {
                shutdownThread = null;
            }

            if (checkEnabledBeans != null) {
                checkEnabledBeans.cancel(true);
            }

            singlesInCreation.clear();
            singletonBeanRegistrations.clear();
            beanConcreteCandidateCache.clear();
            beanCandidateCache.clear();
            beanProxyTargetCache.clear();
            containsBeanCache.clear();
            indexExhaustiveCache.clear();
            beanConfigurations.clear();
            disabledConfigurations.clear();
            singletonScope.clear();
            attributes.clear();
            beanInitializedEventListeners = null;
            beanCreationEventListeners = null;
            beanPreDestroyEventListeners = null;
            beanDestroyedEventListeners = null;
            terminating.set(false);
            running.set(false);
            configured.set(false);
            if (traceMode != BeanResolutionTraceMode.NONE) {
                traceMode.getTracer().ifPresent(tracer -> {
                    tracer.traceContextShutdown(this);
                });
            }
            beanDefinitionProvider.reset();
            // a restarted context reads its configurations and validator again, as it does its definitions
            beanConfigurationsList = null;
            beanValidator = null;
            if (dependencyGraph != null) {
                dependencyGraph.clear();
            }
        }
        return this;
    }

    @Override
    public AnnotationMetadata resolveMetadata(@Nullable Class<?> type) {
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
    public <T> void refreshBean(BeanRegistration<T> beanRegistration) {
        Objects.requireNonNull(beanRegistration, "BeanRegistration cannot be null");
        T bean = beanRegistration.bean;
        if (bean != null) {
            BeanDefinition<T> definition = beanRegistration.definition();
            if (definition instanceof InjectableBeanDefinition<T> injectableBeanDefinition) {
                if (dependencyGraph != null) {
                    // the injections about to run replace the ones recorded, they do not join them
                    dependencyGraph.removeReinjectable(definition);
                }
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
    public <T> BeanRegistration<T> getBeanRegistration(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistration(null, Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        if (beanType == null) {
            return Collections.emptyList();
        }
        return getBeanRegistrations(null, Argument.of(beanType), null);
    }

    @Override
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistrations(
            null,
            Objects.requireNonNull(beanType, "Bean type cannot be null"),
            qualifier
        );
    }

    @Override
    public <T> BeanRegistration<T> getBeanRegistration(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    public <T> BeanRegistration<T> getBeanRegistration(BeanDefinition<? extends T> definition, Argument<T> beanType) {
        return getBeanRegistration(null, definition, beanType);
    }

    /**
     * Resolves the given definition as the given bean type, as {@link #getBeanRegistration(BeanDefinition, Argument)}
     * does, on behalf of the bean the resolution context resolves for: a bean the resolution creates that has no
     * scope of its own is recorded as a dependent of the resolution context, as one a lookup by type creates is.
     *
     * @param resolutionContext The resolution context, or {@code null}
     * @param definition        The bean definition
     * @param beanType          The potentially parameterized bean type to resolve the definition as
     * @param <T>               The bean type
     * @return The bean registration
     * @throws NoSuchBeanException if the definition is not a candidate for the bean type
     * @since 5.3.0
     */
    @SuppressWarnings("unchecked")
    final <T> BeanRegistration<T> getBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                      BeanDefinition<? extends T> definition,
                                                      Argument<T> beanType) {
        ArgumentUtils.requireNonNull(ARGUMENT_DEFINITION, definition);
        ArgumentUtils.requireNonNull("beanType", beanType);
        // resolved as the requested type, of which the definition's own type is a subtype
        BeanDefinition<T> beanDefinition = (BeanDefinition<T>) definition;
        // the definition is already chosen: only whether it is a candidate for the type is checked, as the lookup
        // would check it, and the candidate lookup and its caches are skipped
        Argument<T> resolvedBeanType = resolveCandidateBeanType(beanType, beanDefinition);
        if (!isInjectableCandidate(resolvedBeanType, beanDefinition)) {
            throw new NoSuchBeanException(beanType, null, "The bean definition [" + beanDefinition + "] is not a candidate for that type.");
        }
        BeanRegistration<T> registration = resolveBeanRegistration(resolutionContext, beanDefinition, resolvedBeanType, beanDefinition.getDeclaredQualifier());
        if (registration.bean == null) {
            // only a nullable definition gets here: any other fails to instantiate when it produces no bean
            registration = resolveNullBeanRegistration(beanType, resolvedBeanType, registration);
        }
        return registration;
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
        if (method instanceof UnsafeExecutable<?, ?>) {
            return new BeanContextUnsafeExecutionHandle(method, beanDefinition, (UnsafeExecutable<Object, Object>) method);
        }
        return new BeanContextExecutionHandle(method, beanDefinition);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(Class<T> beanType, @Nullable Qualifier<?> q, String method, Class<?>... arguments) {
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
        return foundMethod.map(executableMethod -> new BeanExecutionHandle<>(this, beanDefinition, beanType, qualifier, executableMethod));
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findExecutableMethod(Class<T> beanType, String method, Class<?>... arguments) {
        if (beanType == null) {
            return Optional.empty();
        }
        Argument<T> beanArgument = Argument.of(beanType);
        Collection<BeanDefinition<T>> definitions = getBeanDefinitions(beanArgument);
        // a bean enumerable by this type only because it is @Indexed by it does not have the type's methods
        BeanDefinition<T> beanDefinition = definitions.stream()
            .filter(definition -> isInjectableCandidate(beanArgument, definition))
            .findFirst()
            .orElse(null);
        if (beanDefinition == null) {
            return Optional.empty();
        }
        Optional<ExecutableMethod<T, R>> foundMethod = beanDefinition.findMethod(method, arguments);
        if (foundMethod.isPresent()) {
            return foundMethod;
        }
        return beanDefinition.<R>findPossibleMethods(method).findFirst();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(T bean, String method, Class<?>... arguments) {
        if (bean != null) {
            Class<T> aClass = (Class<T>) bean.getClass();
            return findExecutionHandle(aClass, method, arguments);
        }
        return Optional.empty();
    }

    @Override
    public <T> BeanContext registerSingleton(Class<T> type, T singleton, @Nullable Qualifier<T> qualifier, boolean inject) {
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
            if (inject) {
                try (BeanResolutionContext context = newResolutionContext(beanDefinition, null)) {
                    doInjectAndInitialize(context, singleton, beanDefinition);
                }
            }
        } else {
            RuntimeBeanDefinition<T> runtimeBeanDefinition = RuntimeBeanDefinition.builder(type, () -> singleton)
                .singleton(true)
                .exposedTypes(ReflectionUtils.getAllClassesInHierarchy(type).toArray(Class<?>[]::new))
                .qualifier(qualifier)
                .build();

            registerBeanDefinition(runtimeBeanDefinition);
            beanDefinition = runtimeBeanDefinition;
        }
        var registration = BeanRegistration.of(
            this,
            new BeanKey<>(beanDefinition, qualifier),
            beanDefinition,
            singleton
        );
        singletonScope.registerSingletonBean(registration, qualifier);
        return this;
    }

    private <T> void purgeCacheForBeanInstance(T singleton) {
        beanCandidateCache.entrySet().removeIf(entry -> entry.getKey().isInstance(singleton));
        beanConcreteCandidateCache.entrySet().removeIf(entry -> entry.getKey().beanType.isInstance(singleton));
        singletonBeanRegistrations.entrySet().removeIf(entry -> entry.getKey().beanType.isInstance(singleton));
        containsBeanCache.entrySet().removeIf(entry -> entry.getKey().beanType.isInstance(singleton));
    }

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
        BeanConfiguration configuration = beanConfigurations.get(configurationName);
        if (configuration != null) {
            return Optional.of(configuration);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public <T> BeanDefinition<T> getBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findBeanDefinition(beanType, qualifier)
            .orElseThrow(() -> newNoSuchBeanException(null, beanType, qualifier, null));
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        BeanDefinition<T> beanDefinition = singletonScope.findCachedSingletonBeanDefinition(beanType, qualifier);
        if (beanDefinition != null) {
            return Optional.of(beanDefinition);
        }
        return findConcreteCandidate(null, beanType, qualifier, true);
    }

    private <T> Optional<BeanDefinition<T>> findBeanDefinitionInternal(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findConcreteCandidate(null, beanType, qualifier, false);
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeanDefinitions(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(null, beanType);
        if (qualifier != null) {
            candidates = qualifier.filterQualified(beanType.getType(), candidates);
        }
        return Collections.unmodifiableCollection(candidates);
    }

    @Override
    public <T> boolean containsBean(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return containsBean(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> boolean containsBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);
        Boolean result = containsBeanCache.get(beanKey);
        if (result != null) {
            return result;
        }
        Argument<T> lookupBeanType = resolveBeanLookupArgument(beanType);
        result = singletonScope.containsBean(beanType, qualifier) ||
            singletonScope.containsBean(lookupBeanType, qualifier) ||
            isCandidatePresent(beanKey.beanType, qualifier) ||
            (!lookupBeanType.equals(beanType) && isCandidatePresent(lookupBeanType, qualifier));

        containsBeanCache.put(beanKey, result);
        return result;
    }

    @Override
    public <T> T getBean(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBean(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> T getBean(Class<T> beanType) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBean(Argument.of(beanType), null);
    }

    @Override
    public <T> T getBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        try {
            return getBean(null, beanType, qualifier);
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.ConditionLog.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.ConditionLog.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getSimpleName(), e.getMessage(), e);
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
    public <T> Optional<T> findBean(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findBean(null, beanType, qualifier);
    }

    @Override
    public <T> Optional<T> findBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findBean(null, beanType, qualifier);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType) {
        return getBeansOfType(null, Argument.of(beanType));
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBeansOfType(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Argument<T> beanType) {
        return getBeansOfType(null, beanType);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBeansOfType(null, beanType, qualifier);
    }

    @Override
    public <T> Stream<T> streamOfType(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return streamOfType((BeanResolutionContext) null, beanType, qualifier);
    }

    @Override
    public <T> Stream<T> streamOfType(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return streamOfType((BeanResolutionContext) null, beanType, qualifier);
    }

    @Override
    public <V> Map<String, V> mapOfType(Argument<V> beanType, @Nullable Qualifier<V> qualifier) {
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
    protected <T> Stream<T> streamOfType(@Nullable BeanResolutionContext resolutionContext, Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return streamOfType(resolutionContext, Argument.of(beanType), qualifier);
    }

    /**
     * Obtains a map of beans of the given type and qualifier.
     *
     * @param resolutionContext The resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <V>               The bean type
     * @return A map of beans, never {@code null}.
     * @since 4.0.0
     */
    protected <V> Map<String, V> mapOfType(@Nullable BeanResolutionContext resolutionContext, Argument<V> beanType, @Nullable Qualifier<V> qualifier) {
        // try and find a bean that implements the map with the generics
        Argument<Map<String, V>> mapType = Argument.mapOf(Argument.STRING, beanType);
        @SuppressWarnings("unchecked") Qualifier<Map<String, V>> mapQualifier = (Qualifier<Map<String, V>>) qualifier;
        BeanDefinition<Map<String, V>> existingBean = findBeanDefinitionInternal(mapType, mapQualifier).orElse(null);
        if (existingBean != null) {
            return getBean(existingBean);
        }
        Collection<BeanRegistration<V>> beanRegistrations = getBeanRegistrations(resolutionContext, beanType, qualifier);
        if (beanRegistrations.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return beanRegistrations.stream().collect(Collectors.toUnmodifiableMap(
                DefaultBeanContext::resolveKey,
                reg -> reg.bean
            ));
        } catch (IllegalStateException e) { // occurs for duplicate keys
            throw new BeanInstantiationException(
                "Injecting a map of beans requires `@Named` qualifier. Multiple beans were found missing a qualifier resulting in duplicate keys: " + e.getMessage(),
                new NonUniqueBeanException(
                    beanType.getType(),
                    beanRegistrations.stream().map(reg -> reg.beanDefinition).iterator()
                )
            );
        }
    }

    private static String resolveKey(BeanRegistration<?> reg) {
        BeanDefinition<?> definition = reg.beanDefinition;
        if (definition instanceof NameResolver resolver && resolver.resolveName().isPresent()) {
            return resolver.resolveName().get();
        }
        Qualifier<?> declaredQualifier = definition.getDeclaredQualifier();
        if (declaredQualifier != null) {
            String name = Qualifiers.findName(declaredQualifier);
            if (name != null) {
                return name;
            }
        }
        // Must be the primary or a single bean
        Class<?> candidateType = reg.beanDefinition.getBeanType();
        String candidateSimpleName = candidateType.getSimpleName();
        return NameUtils.decapitalize(candidateSimpleName);
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
    public <T> Stream<T> streamOfType(@Nullable BeanResolutionContext resolutionContext, Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeanRegistrations(resolutionContext, beanType, qualifier).stream()
            .map(BeanRegistration::getBean);
    }

    @Override
    public <T> T inject(T instance) {
        Objects.requireNonNull(instance, "Instance cannot be null");

        Collection<BeanDefinition<T>> candidates = findBeanCandidatesForInstance(instance);
        BeanDefinition<T> beanDefinition;
        if (candidates.size() == 1) {
            beanDefinition = candidates.iterator().next();
        } else if (!candidates.isEmpty()) {
            beanDefinition = lastChanceResolve(Argument.of((Class<T>) instance.getClass()), null, true, candidates);
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

    @Override
    public <T> T createBean(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return createBean(null, beanType, qualifier);
    }

    @Override
    public <T> T createBean(Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Map<String, Object> argumentValues) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        Argument<T> beanArg = Argument.of(beanType);
        Optional<BeanDefinition<T>> candidate = findBeanDefinition(beanArg, qualifier);
        if (candidate.isPresent()) {
            BeanDefinition<T> beanDefinition = candidate.get();
            try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
                if (beanDefinition instanceof InstantiatableBeanDefinition<T> instantiatableBeanDefinition) {
                    T bean = resolveByBeanFactory(resolutionContext, instantiatableBeanDefinition, qualifier, argumentValues);
                    return postBeanCreated(resolutionContext, beanDefinition, beanArg, qualifier, bean);
                }
            }
        }
        throw newNoSuchBeanException(
            null,
            beanArg,
            qualifier,
            null
        );
    }

    @Override
    public <T> T createBean(Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Object... args) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        final Argument<T> beanArg = Argument.of(beanType);
        Optional<BeanDefinition<T>> candidate = findBeanDefinition(beanArg, qualifier);
        if (candidate.isPresent()) {
            BeanDefinition<T> definition = candidate.get();
            try (BeanResolutionContext resolutionContext = newResolutionContext(definition, null)) {
                return doCreateBeanWithArguments(resolutionContext, definition, beanArg, qualifier, args);
            }
        }
        throw newNoSuchBeanException(
            null,
            Argument.of(beanType),
            qualifier,
            null
        );
    }

    @Override
    public <T> T createBean(BeanDefinition<T> definition, @Nullable Object... args) {
        ArgumentUtils.requireNonNull(ARGUMENT_DEFINITION, definition);
        try (BeanResolutionContext resolutionContext = newResolutionContext(definition, null)) {
            return doCreateBeanWithArguments(resolutionContext, definition, Argument.of(definition.getBeanType()), null, args);
        }
    }

    private <T> T doCreateBeanWithArguments(BeanResolutionContext resolutionContext,
                                            BeanDefinition<T> definition,
                                            Argument<T> beanType,
                                            @Nullable Qualifier<T> qualifier,
                                            @Nullable Object... args) {
        Map<String, Object> argumentValues = resolveArgumentValues(resolutionContext, definition, args);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Computed bean argument values: {}", argumentValues);
        }
        if (definition instanceof InstantiatableBeanDefinition<T> instantiatableBeanDefinition) {
            T bean = resolveByBeanFactory(resolutionContext, instantiatableBeanDefinition, qualifier, argumentValues);
            return postBeanCreated(resolutionContext, definition, beanType, qualifier, bean);
        } else {
            throw new BeanInstantiationException("BeanDefinition doesn't support creating a new instance of the bean");
        }
    }

    @Nullable
    private <T> Map<String, Object> resolveArgumentValues(BeanResolutionContext resolutionContext, BeanDefinition<T> definition, @Nullable Object[] args) {
        Argument<?>[] requiredArguments;
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
    public <T> T destroyBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return findBeanDefinition(beanType, qualifier)
            .map(this::destroyBean)
            .orElse(null);
    }

    @Override
    public <T> T destroyBean(T bean) {
        ArgumentUtils.requireNonNull("bean", bean);
        Optional<BeanRegistration<T>> beanRegistration = findBeanRegistration(bean);
        if (beanRegistration.isPresent()) {
            destroyBean(beanRegistration.get());
        } else {
            Optional<BeanDefinition<T>> beanDefinition = findBeanDefinition((Class<T>) bean.getClass());
            if (beanDefinition.isPresent()) {
                BeanDefinition<T> definition = beanDefinition.get();
                BeanKey<T> key = new BeanKey<>(definition, definition.getDeclaredQualifier());
                destroyBean(BeanRegistration.of(this, key, definition, bean, retainedInterceptorDependents(bean)));
            }
        }
        return bean;
    }

    /**
     * The interceptor registrations a proxy retained, narrowed to the ones that live and die with their target.
     *
     * <p>{@link #destroyBean(Object)} builds a registration of its own only when the context tracks none for the
     * instance, which is the case for a bean created with {@code createBean}. The dependents the tracked path would
     * have carried were never recorded, so on that path a {@code @Prototype} interceptor was never destroyed with
     * its target. A generated proxy that retained its interceptor registrations for lifecycle interception is the
     * one thing that survived from creation to destruction, and those registrations stand in for the missing
     * dependents.</p>
     *
     * <p>Singleton interceptors are left out, because a singleton is never a dependent of one target: the tracked
     * path resolves it from the singleton scope instead of creating it for the bean, and destroying it here would
     * take it away from every other bean it advises. This is the same rule
     * {@link #destroyBean(BeanRegistration, boolean)} applies when it skips a singleton proxy definition destroyed
     * as a dependent; here it has to cover plain definitions too, since an interceptor is rarely proxied itself.</p>
     *
     * @param bean The bean being destroyed
     * @return The non-singleton interceptor registrations to destroy with the bean, or {@code null} if there are none
     */
    @Nullable
    private static List<BeanRegistration<?>> retainedInterceptorDependents(Object bean) {
        if (!(bean instanceof InterceptedBean intercepted)) {
            return null;
        }
        List<? extends BeanRegistration<?>> registrations = intercepted.$interceptorRegistrations();
        if (registrations.isEmpty()) {
            return null;
        }
        List<BeanRegistration<?>> dependents = new ArrayList<>(registrations.size());
        for (BeanRegistration<?> registration : registrations) {
            if (!registration.beanDefinition.isSingleton()) {
                dependents.add(registration);
            }
        }
        return dependents.isEmpty() ? null : dependents;
    }

    @Override
    @Nullable
    public <T> T destroyBean(Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return destroyBean(Argument.of(beanType), null);
    }

    @Nullable
    private <T> T destroyBean(BeanDefinition<T> beanDefinition) {
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
    public <T> void destroyBean(BeanRegistration<T> registration) {
        if (registration instanceof RetainedRegistration<T> retained) {
            // a registration stopRetaining returned that no context adopted: destroyed with what it owned, once
            // for an instance returned under several registrations
            if (retained.destroyed.compareAndSet(false, true)) {
                destroyBean(retained.original, false);
            }
            return;
        }
        destroyBean(registration, false);
    }

    @Override
    public <T> void destroyDependentBean(BeanRegistration<T> registration) {
        ArgumentUtils.requireNonNull("registration", registration);
        destroyBean(registration, true);
    }

    private <T> void destroyBean(BeanRegistration<T> registration, boolean dependent) {
        if (registration instanceof BeanDisposingRegistration<?> disposing && !disposing.beginDestruction()) {
            return;
        }
        try (DefaultBeanResolutionContext resolutionContext =
                 new DefaultBeanResolutionContext(this, registration.getBeanDefinition(), null, true)) {
            destroyRegistration(resolutionContext, registration, dependent);
        }
    }

    @SuppressWarnings("java:S1181") // Release dependents even when destruction fails with an Error, then rethrow it.
    private <T> void destroyRegistration(DefaultBeanResolutionContext resolutionContext,
                                         BeanRegistration<T> registration, boolean dependent) {
        stopDependencyResolution(resolutionContext, registration, Collections.newSetFromMap(new IdentityHashMap<>()));
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
        if (dependencyGraph != null) {
            // what a destroyed bean held is released with it; what held the bean stays recorded until that is destroyed.
            // A singleton definition may have a fresh registration beside the scoped instance, and both record under it
            dependencyGraph.destroyed(registration, definition.isSingleton() && singletonScope.findBeanRegistration(definition) != null);
        }
        try {
            beanToDestroy = triggerPreDestroyListeners(resolutionContext, definition, beanToDestroy);
            if (definition instanceof DisposableBeanDefinition<T> disposable) {
                disposeWithLogging(resolutionContext, disposable, registration, beanToDestroy);
            }
            stopLifecycleIfRequired(beanToDestroy, definition, dependent);
        } catch (RuntimeException | Error failure) {
            releaseDependents(registration, failure);
            throw failure;
        }
        releaseDependents(registration, null);
        triggerBeanDestroyedListeners(definition, beanToDestroy);
    }

    private <T> void disposeWithLogging(DefaultBeanResolutionContext resolutionContext,
                                       DisposableBeanDefinition<T> definition,
                                       BeanRegistration<T> registration, T beanToDestroy) {
        try {
            disposeBean(resolutionContext, definition, registration, beanToDestroy);
        } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error disposing bean [{}]... Continuing...", beanToDestroy, e);
            }
        }
    }

    @SuppressWarnings("java:S1181") // Preserve a destruction failure and attach cleanup Errors instead of replacing it.
    private void releaseDependents(BeanRegistration<?> registration, @Nullable Throwable failure) {
        try {
            if (registration instanceof BeanDisposingRegistration<?> disposing) {
                disposing.getDependencies().close(this);
            } else {
                registration.close();
            }
        } catch (RuntimeException | Error cleanup) {
            if (failure == null) {
                throw cleanup;
            }
            if (failure != cleanup) {
                failure.addSuppressed(cleanup);
            }
        }
    }

    private <T> void stopLifecycleIfRequired(T bean, BeanDefinition<T> definition, boolean dependent) {
        if (bean instanceof LifeCycle<?> cycle && !dependent) {
            destroyLifeCycleBean(cycle, definition);
        }
    }

    /**
     * Disposes of the bean, exposing the registrations it owns so that {@code @PreDestroy} advice can reuse
     * interceptor instances that were already created for the same bean.
     *
     * <p>The scenario is a bean with lifecycle advice but no around proxy, so it has no instance field in which
     * interceptor registrations could have been retained. A bean is destroyed long after it was created and with a
     * fresh resolution context, so without this the pre-destroy advice would resolve a new interceptor and a
     * {@code @Prototype} interceptor could not release the state it set up in {@code @PostConstruct}.</p>
     *
     * <p>The registrations are retained by the bean registration created alongside the bean. Beans created through
     * {@code createBean} and destroyed through {@code destroyBean(Object)} have no bean registration and therefore
     * retain the existing fresh-resolution behaviour, unless a generated proxy retained the registrations itself,
     * in which case {@code destroyBean(Object)} destroys the non-singleton ones with the target.</p>
     *
     * @param resolutionContext The destruction invocation
     * @param definition    The disposable definition
     * @param registration  The registration of the bean being destroyed
     * @param beanToDestroy The bean
     * @param <T>           The bean type
     * @since 5.2.0
     */
    private <T> void disposeBean(DefaultBeanResolutionContext resolutionContext,
                                 DisposableBeanDefinition<T> definition,
                                 BeanRegistration<T> registration,
                                 T beanToDestroy) {
        List<BeanRegistration<?>> dependents = registration.dependentBeans();
        InterceptorCandidates candidates = registration instanceof BeanDisposingRegistration<?> disposingRegistration
            ? disposingRegistration.getInterceptorCandidates()
            : InterceptorCandidates.Unresolved.INSTANCE;
        if (!dependents.isEmpty()) {
            resolutionContext.setAttribute(BeanResolutionContext.EXISTING_DEPENDENT_BEANS, dependents);
        }
        if (candidates instanceof InterceptorCandidates.Resolved resolved) {
            // An explicitly resolved empty set must also prevent discovery during destruction.
            resolutionContext.setBeanInterceptors(definition, resolved.registrations());
        }
        definition.dispose(resolutionContext, this, beanToDestroy);
    }

    /**
     * Destroy a lifecycle bean.
     *
     * @param cycle      The cycle
     * @param definition The definition
     * @param <T>        The bean type
     */
    @Internal
    protected <T> void destroyLifeCycleBean(LifeCycle<?> cycle, BeanDefinition<T> definition) {
        try {
            cycle.stop();
        } catch (Exception e) {
            throw new BeanDestructionException(definition, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T triggerPreDestroyListeners(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition, T bean) {
        if (!configured.get()) {
            // An in-flight resolution can roll back after shutdown completed. Its beans still need disposal,
            // but the context's listeners have already been destroyed and must not be resolved again.
            return bean;
        }
        if (beanPreDestroyEventListeners == null) {
            beanPreDestroyEventListeners = loadBeanEventListeners(BeanPreDestroyEventListener.class);
        }
        if (!beanPreDestroyEventListeners.isEmpty()) {
            BeanPreDestroyEvent<T> event = new BeanPreDestroyEvent<>(this, beanDefinition, bean, resolutionContext);
            Class<T> beanType = getBeanType(beanDefinition);
            List<ListenersSupplier.ListenerAndOrder<BeanPreDestroyEventListener>> listeners = new ArrayList<>();
            for (Map.Entry<Class<?>, ListenersSupplier<BeanPreDestroyEventListener>> entry : beanPreDestroyEventListeners) {
                if (entry.getKey().isAssignableFrom(beanType)) {
                    for (ListenersSupplier.ListenerAndOrder<BeanPreDestroyEventListener> listener : entry.getValue().get(null)) {
                        listeners.add(listener);
                    }
                }
            }
            if (listeners.size() > 1) {
                listeners.sort(OrderUtil.COMPARATOR_ZERO);
            }
            for (ListenersSupplier.ListenerAndOrder<BeanPreDestroyEventListener> listener : listeners) {
                try {
                    bean = (T) Objects.requireNonNull(
                        listener.bean.onPreDestroy(event),
                        "PreDestroy event listener illegally returned null: " + listener.getClass()
                    );
                } catch (Exception e) {
                    throw new BeanDestructionException(beanDefinition, e);
                }
            }
        }
        return bean;
    }

    @SuppressWarnings("java:S1181") // Remove the scoped target even when a dependent fails with an Error, then rethrow it.
    private <T> void destroyProxyTargetBean(BeanRegistration<T> registration, boolean dependent) {
        BeanDefinition<T> proxyTargetBeanDefinition = findProxyTargetBeanDefinition(registration.beanDefinition)
            .orElseThrow(() -> new IllegalStateException("Cannot find a proxy target bean definition for: " + registration.beanDefinition));
        Optional<CustomScope<?>> declaredScope = customScopeRegistry.findDeclaredScope(proxyTargetBeanDefinition);
        if (registration.bean instanceof InterceptedBeanProxy<?> proxy
            && proxy.$beanDependencies() != null) {
            // The proxy retains the original owner even if the caller only retained the bean instance.
            // Its prototype target and advice are ordinary dependents; scoped and swapped-in targets are borrowed.
            Throwable failure = null;
            try {
                proxy.$beanDependencies().close();
            } catch (RuntimeException | Error e) {
                failure = e;
            } finally {
                proxy.clearCachedInterceptedTarget();
            }
            if (!dependent && declaredScope.isPresent()) {
                // Runs even when a dependent failed to be destroyed, or the scope would keep the target alive.
                // Removal by definition waits for a creation in flight, so a target about to be published is not missed.
                try {
                    declaredScope.get().remove(proxyTargetBeanDefinition);
                } catch (RuntimeException | Error e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure instanceof RuntimeException exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            return;
        }
        List<BeanRegistration<?>> proxyDependents = registration instanceof BeanDisposingRegistration<?> disposingRegistration
            ? disposingRegistration.dependentBeans()
            : null;
        if (declaredScope.isEmpty()
            && !proxyTargetBeanDefinition.isSingleton()
            && registration.bean instanceof InterceptedBeanProxy
            && ((InterceptedBeanProxy<T>) registration.bean).hasCachedInterceptedTarget()) {
            // Scope is not present, try to get the actual target bean and destroy it
            InterceptedBeanProxy<T> interceptedProxy = (InterceptedBeanProxy<T>) registration.bean;
            T interceptedTarget = interceptedProxy.interceptedTarget();
            BeanRegistration<T> targetRegistration = interceptedProxy.interceptedTargetRegistration();
            if (!(targetRegistration instanceof BeanDisposingRegistration && targetRegistration.bean == interceptedTarget)
                && destroyProxyTargetBeforeProxyDependents(registration, proxyTargetBeanDefinition, interceptedTarget, proxyDependents)) {
                interceptedProxy.clearCachedInterceptedTarget();
                return;
            }
        }
        Set<Object> destroyed = Collections.emptySet();
        if (proxyDependents != null) {
            destroyed = Collections.newSetFromMap(new IdentityHashMap<>());
            for (BeanRegistration<?> beanRegistration : proxyDependents) {
                destroyDependentBean(beanRegistration);
                destroyed.add(beanRegistration.bean);
            }
        }
        if (declaredScope.isEmpty()) {
            if (proxyTargetBeanDefinition.isSingleton()) {
                return;
            }
            if (registration.bean instanceof InterceptedBeanProxy) {
                InterceptedBeanProxy<T> interceptedProxy = (InterceptedBeanProxy<T>) registration.bean;
                if (interceptedProxy.hasCachedInterceptedTarget()) {
                    T interceptedTarget = interceptedProxy.interceptedTarget();
                    if (destroyed.contains(interceptedTarget)) {
                        return;
                    }
                    BeanRegistration<T> targetRegistration = interceptedProxy.interceptedTargetRegistration();
                    if (targetRegistration instanceof BeanDisposingRegistration<T> disposingTargetRegistration && targetRegistration.bean == interceptedTarget) {
                        // resolved after the proxy was created, so not among its dependents: destroyed through its own
                        // registration, with the interceptors created for it, unless it was destroyed already
                        destroyBean(targetRegistration);
                        interceptedProxy.clearCachedInterceptedTarget();
                    }
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
            if (registration.bean instanceof InterceptedBeanProxy<?> interceptedProxy) {
                interceptedProxy.clearCachedInterceptedTarget();
            }
        }
    }

    /**
     * Destroys the target of a proxy that resolved its interceptors for itself before the dependents of the proxy.
     *
     * <p>The target is destroyed with the dependents of the proxy and its own as one: its pre-destroy is intercepted
     * by the interceptors the proxy held, which are still alive then, and every dependent is destroyed once, after
     * it. An interceptor created with the target gives way to the one of the same definition the proxy held, so that
     * the pre-destroy is intercepted once, and is destroyed after the target too.</p>
     *
     * @return {@code false} when the target is among the dependents of the proxy with a registration that cannot
     * be marked destroyed, and is left to be destroyed with them
     */
    private <T> boolean destroyProxyTargetBeforeProxyDependents(BeanRegistration<T> proxyRegistration,
                                                               BeanDefinition<T> targetDefinition,
                                                               T target,
                                                               @Nullable List<BeanRegistration<?>> proxyDependents) {
        BeanRegistration<?> tracked = null;
        if (proxyDependents != null) {
            for (BeanRegistration<?> dependent : proxyDependents) {
                if (dependent.bean == target) {
                    tracked = dependent;
                    break;
                }
            }
        }
        List<BeanRegistration<?>> targetDependents;
        List<?> targetInterceptorRegistrations = null;
        if (tracked == null) {
            // the dependents created with a target a lazy proxy caches are kept on the context the proxy retains
            targetDependents = takeCachedProxyTargetDependents(proxyRegistration);
        } else if (tracked instanceof BeanDisposingRegistration<?> trackedRegistration) {
            if (!trackedRegistration.beginDestruction()) {
                // destroyed already: only the dependents of the proxy are left
                return false;
            }
            targetDependents = trackedRegistration.dependentBeans();
            targetInterceptorRegistrations = trackedRegistration.getInterceptorCandidates().legacyRegistrations();
        } else {
            return false;
        }
        List<BeanRegistration<?>> dependents = new ArrayList<>();
        List<BeanRegistration<?>> shadowed = null;
        if (proxyDependents != null) {
            for (BeanRegistration<?> dependent : proxyDependents) {
                if (dependent != tracked) {
                    dependents.add(dependent);
                }
            }
        }
        if (targetDependents != null) {
            for (BeanRegistration<?> dependent : targetDependents) {
                if (isInterceptorOfDefinitionIn(dependent, proxyDependents)) {
                    if (shadowed == null) {
                        shadowed = new ArrayList<>(2);
                    }
                    shadowed.add(dependent);
                } else {
                    dependents.add(dependent);
                }
            }
        }
        BeanRegistration<T> targetRegistration = BeanRegistration.of(this,
            new BeanKey<>(targetDefinition, targetDefinition.getDeclaredQualifier()),
            targetDefinition,
            target,
            dependents,
            targetInterceptorRegistrations
        );
        // a target found among the dependents of the proxy is destroyed as one of them, as before
        destroyBean(targetRegistration, tracked != null);
        if (shadowed != null) {
            for (int i = shadowed.size() - 1; i >= 0; i--) {
                destroyDependentBean(shadowed.get(i));
            }
        }
        return true;
    }

    private static boolean isInterceptorOfDefinitionIn(BeanRegistration<?> registration, @Nullable List<BeanRegistration<?>> proxyDependents) {
        if (proxyDependents != null
            && registration instanceof BeanDisposingRegistration<?> disposingRegistration && disposingRegistration.isCreatedAsInterceptor()) {
            for (BeanRegistration<?> dependent : proxyDependents) {
                if (dependent != registration && dependent.beanDefinition.equals(registration.beanDefinition)) {
                    return true;
                }
            }
        }
        return false;
    }

    private <T> void triggerBeanDestroyedListeners(BeanDefinition<T> beanDefinition, T bean) {
        if (!configured.get()) {
            return;
        }
        if (beanDestroyedEventListeners == null) {
            beanDestroyedEventListeners = loadBeanEventListeners(BeanDestroyedEventListener.class);
        }
        if (!beanDestroyedEventListeners.isEmpty()) {
            BeanDestroyedEvent<T> event = new BeanDestroyedEvent<>(this, beanDefinition, bean);
            Class<T> beanType = getBeanType(beanDefinition);
            List<ListenersSupplier.ListenerAndOrder<BeanDestroyedEventListener>> listeners = new ArrayList<>();
            for (Map.Entry<Class<?>, ListenersSupplier<BeanDestroyedEventListener>> entry : beanDestroyedEventListeners) {
                if (entry.getKey().isAssignableFrom(beanType)) {
                    for (ListenersSupplier.ListenerAndOrder<BeanDestroyedEventListener> listener : entry.getValue().get(null)) {
                        listeners.add(listener);
                    }
                }
            }
            if (listeners.size() > 1) {
                listeners.sort(OrderUtil.COMPARATOR_ZERO);
            }
            for (ListenersSupplier.ListenerAndOrder<BeanDestroyedEventListener> listener : listeners) {
                try {
                    listener.bean.onDestroyed(event);
                } catch (Exception e) {
                    throw new BeanDestructionException(beanDefinition, e);
                }
            }
        }
    }

    private <T> Class<T> getBeanType(BeanDefinition<T> beanDefinition) {
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
    protected <T> T createBean(@Nullable BeanResolutionContext resolutionContext,
                               Class<T> beanType,
                               @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);

        Optional<BeanDefinition<T>> concreteCandidate = findBeanDefinition(beanType, qualifier);
        if (concreteCandidate.isPresent()) {
            BeanDefinition<T> candidate = concreteCandidate.get();
            try (BeanResolutionContext context = newResolutionContext(candidate, resolutionContext)) {
                if (candidate instanceof InstantiatableBeanDefinition<T> instantiatableBeanDefinition) {
                    T bean = resolveByBeanFactory(context, instantiatableBeanDefinition, qualifier, Collections.emptyMap());
                    return postBeanCreated(context, candidate, Argument.of(beanType), qualifier, bean);
                } else {
                    throw new BeanInstantiationException("BeanDefinition doesn't support creating a new instance of the bean");
                }
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
    protected <T> T inject(BeanResolutionContext resolutionContext,
                           @Nullable BeanDefinition<?> requestingBeanDefinition,
                           T instance) {
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
    protected <T> Collection<T> getBeansOfType(@Nullable BeanResolutionContext resolutionContext, Argument<T> beanType) {
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
    public <T> Collection<T> getBeansOfType(@Nullable BeanResolutionContext resolutionContext,
                                            Argument<T> beanType,
                                            @Nullable Qualifier<T> qualifier) {
        Collection<BeanRegistration<T>> beanRegistrations = getBeanRegistrations(resolutionContext, beanType, qualifier);
        List<T> list = new ArrayList<>(beanRegistrations.size());
        for (BeanRegistration<T> beanRegistration : beanRegistrations) {
            list.add(beanRegistration.getBean());
        }
        return list;
    }

    @Override
    public <T> T getProxyTargetBean(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getProxyTargetBean(null, Argument.of(beanType), qualifier);
    }

    @Override
    public <T> T getProxyTargetBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    @UsedByGeneratedCode
    public <T> T getProxyTargetBean(@Nullable BeanResolutionContext resolutionContext,
                                    Argument<T> beanType,
                                    @Nullable Qualifier<T> qualifier) {
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return getProxyTargetBean(resolutionContext, definition, beanType, qualifier);
    }

    /**
     * Resolves the proxy target for a given proxy bean definition. If the bean has no proxy then the original bean is returned.
     *
     * @param resolutionContext The bean resolution context
     * @param definition        The proxy bean definition
     * @param beanType          The bean type
     * @param qualifier         The bean qualifier
     * @param <T>               The generic type
     * @return The proxied instance
     * @since 4.3.0
     */
    @Internal
    @UsedByGeneratedCode
    public <T> T getProxyTargetBean(@Nullable BeanResolutionContext resolutionContext,
                                    BeanDefinition<T> definition,
                                    Argument<T> beanType,
                                    @Nullable Qualifier<T> qualifier) {
        BeanRegistration<T> registration = Objects.requireNonNull(resolveBeanRegistration(resolutionContext, definition, beanType, qualifier));
        if (registration.bean == null) {
            // let the customizer decide what a null target becomes, as bean lookup does
            registration = resolveNullBeanRegistration(beanType, beanType, registration);
        }
        if (registration.bean != null
            && registration instanceof BeanDisposingRegistration<T> disposingRegistration
            && !registration.beanDefinition.isSingleton()) {
            // the proxy keeps the target and not its registration: remember what was created with the target,
            // so that it is destroyed with the target when the proxy is
            // they are kept on the context the proxy retains, and only for a proxy that caches its target: a proxy
            // that resolves a new target on every call must leave nothing behind
            List<BeanRegistration<?>> dependents = disposingRegistration.dependentBeans();
            if (!dependents.isEmpty()
                && resolutionContext instanceof AbstractBeanResolutionContext retained && retained.isLazyProxyTarget()
                && definition.booleanValue(AROUND_TYPE, "cacheableLazyTarget").orElse(false)) {
                retained.setCachedProxyTargetDependents(dependents);
            }
        }
        return registration.bean;
    }

    /**
     * Resolves the proxy target for a given proxy bean definition together with the registration the context holds
     * for it: the singleton scope's for a singleton, the one a custom scope stores for a scoped bean, or the one
     * created for a prototype. It carries the non-singleton interceptors created for the target.
     *
     * @param resolutionContext The bean resolution context
     * @param definition        The proxy target bean definition
     * @param beanType          The bean type
     * @param qualifier         The bean qualifier
     * @param <T>               The generic type
     * @return The registration of the proxy target
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    public <T> BeanRegistration<T> getProxyTargetBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                                 BeanDefinition<T> definition,
                                                                 Argument<T> beanType,
                                                                 @Nullable Qualifier<T> qualifier) {
        if (resolutionContext instanceof AbstractBeanResolutionContext retained
            && retained.isLazyProxyTarget() && retained.lazyProxyDependencies != null
            && definition.booleanValue(AROUND_TYPE, "cacheableLazyTarget").orElse(false)
            && isUnscoped(definition)) {
            return retained.lazyProxyDependencies.resolve(this, null, resolution -> {
                resolution.copyStateFrom(retained);
                return resolveTargetRegistration(resolution, definition, beanType, qualifier);
            });
        }
        return resolveTargetRegistration(resolutionContext, definition, beanType, qualifier);
    }

    private <T> BeanRegistration<T> resolveTargetRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                              BeanDefinition<T> definition,
                                                              Argument<T> beanType,
                                                              @Nullable Qualifier<T> qualifier) {
        BeanRegistration<T> registration = Objects.requireNonNull(resolveBeanRegistration(resolutionContext, definition, beanType, qualifier, true));
        if (registration.bean == null) {
            registration = resolveNullBeanRegistration(beanType, beanType, registration);
        }
        return registration;
    }

    /**
     * Takes the dependents created with the target a lazy proxy caches, which are kept on the resolution context
     * the proxy retains.
     *
     * @param proxyRegistration The registration of the proxy
     * @return The dependents of the cached target, or {@code null} if there are none
     */
    @Nullable
    private static List<BeanRegistration<?>> takeCachedProxyTargetDependents(BeanRegistration<?> proxyRegistration) {
        if (proxyRegistration instanceof BeanDisposingRegistration<?> disposingRegistration) {
            AbstractBeanResolutionContext retained = disposingRegistration.getProxyTargetContext();
            if (retained != null) {
                return retained.takeCachedProxyTargetDependents();
            }
        }
        return null;
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(Class<T> beanType, String method, Class<?>... arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, null);
        return definition.findMethod(method, arguments);
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(Class<T> beanType, Qualifier<T> qualifier, String method, Class<?>... arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return definition.findMethod(method, arguments);
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(Argument<T> beanType, Qualifier<T> qualifier, String method, Class<?>... arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return definition.findMethod(method, arguments);
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findProxyTargetBeanDefinition(Argument.of(beanType), qualifier);
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findBeanDefinitionByDefinitionClass(Class<? extends BeanDefinition<T>> definitionClass) {
        ArgumentUtils.requireNonNull("definitionClass", definitionClass);
        return Optional.ofNullable(beanDefinitionProvider.findBeanDefinitionByDefinitionClass(this, definitionClass));
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(BeanDefinition<T> proxyBeanDefinition) {
        ArgumentUtils.requireNonNull("proxyBeanDefinition", proxyBeanDefinition);
        if (proxyBeanDefinition instanceof ProxyBeanDefinition<T> proxyDefinition) {
            return findBeanDefinitionByDefinitionClass(proxyDefinition.getTargetDefinitionType());
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("java:S2789") // performance optimization
    public <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        BeanCandidateKey<T> key = new BeanCandidateKey<>(beanType, qualifier, true);

        Optional beanDefinition = beanProxyTargetCache.get(key);
        if (beanDefinition == null) {
            beanDefinition = findProxyTargetNoCache(null, beanType, qualifier);
            beanProxyTargetCache.put(key, beanDefinition);
        }
        return beanDefinition;
    }

    @Override
    public Collection<BeanDefinition<Object>> getBeanDefinitions(@Nullable Qualifier<Object> qualifier) {
        if (qualifier == null) {
            return Collections.emptyList();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for qualifier: {}", qualifier);
        }
        Collection<BeanDefinition<Object>> candidates;
        if (qualifier instanceof FilteringQualifier<Object> filteringQualifier) {
            @SuppressWarnings("unchecked")
            Argument<Object> indexedArgument = (Argument<Object>) filteringQualifier.getIndexedArgument();
            if (indexedArgument != null && isIndexExhaustive(indexedArgument, filteringQualifier)) {
                // the compile-time index holds every bean this qualifier selects, so there is nothing to filter
                return getBeanDefinitions(indexedArgument);
            }
            // Keep anonymous
            Predicate<BeanDefinitionReference<Object>> predicate = new Predicate<>() {
                @Override
                public boolean test(BeanDefinitionReference<Object> qbt) {
                    return filteringQualifier.doesQualify(Object.class, qbt);
                }
            };
            candidates = beanDefinitionProvider.getBeanDefinitions(this, predicate, null);
        } else {
            Stream<BeanDefinition<Object>> beanDefinitionsClasses = StreamSupport.stream(
                beanDefinitionProvider.getBeanDefinitions(this, Argument.OBJECT_ARGUMENT, null, null).spliterator(),
                false);
            candidates = qualifier.reduce(Object.class, beanDefinitionsClasses)
                .toList();
        }

        filterReplacedBeans(candidates);
        return candidates;
    }

    /**
     * Whether every bean the qualifier selects is indexed by the annotation type it reports. A bean compiled
     * before that annotation was indexed by itself carries the annotation without the index, as every module
     * released against an earlier version does, so only filtering every reference finds it. That is checked
     * once per annotation type, and the index is taken only when no such bean is present.
     *
     * @param indexedArgument The type the qualifier reports the beans it selects are indexed by
     * @param qualifier       The qualifier
     * @return True if the index holds every bean the qualifier selects
     */
    private boolean isIndexExhaustive(Argument<?> indexedArgument, FilteringQualifier<Object> qualifier) {
        long epoch = beanDefinitionsEpoch.get();
        IndexExhaustiveness cached = indexExhaustiveCache.get(indexedArgument);
        if (cached != null && cached.epoch() == epoch) {
            return cached.exhaustive();
        }
        boolean exhaustive = true;
        Class<?> indexedType = indexedArgument.getType();
        for (BeanDefinitionReference<Object> reference : beanDefinitionProvider.getBeanReferences()) {
            // the qualifier first: few references match it, and getIndexes() reads the annotation metadata of
            // any reference that was compiled without indexes
            if (qualifier.doesQualify(Object.class, reference) && !isIndexedBy(reference, indexedType)) {
                exhaustive = false;
                break;
            }
        }
        // stored with the epoch read before computing, so a result that raced a registration is never used
        indexExhaustiveCache.put(indexedArgument, new IndexExhaustiveness(epoch, exhaustive));
        return exhaustive;
    }

    private static boolean isIndexedBy(BeanDefinitionReference<?> reference, Class<?> indexedType) {
        for (Class<?> index : reference.getIndexes()) {
            if (index == indexedType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the compile-time index of an annotation holds every bean carrying it.
     *
     * @param epoch      The {@link #beanDefinitionsEpoch} it was computed at
     * @param exhaustive True if the index holds every bean carrying the annotation
     */
    private record IndexExhaustiveness(long epoch, boolean exhaustive) {
    }

    @Override
    public Collection<BeanDefinition<Object>> getAllBeanDefinitions() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding all bean definitions");
        }
        return beanDefinitionProvider.getBeanDefinitions(this, null);
    }

    @Override
    public Collection<DisabledBean<?>> getDisabledBeans() {
        return beanDefinitionProvider.getDisabledBeans(this);
    }

    @Override
    public Collection<BeanDefinitionReference<Object>> getBeanDefinitionReferences() {
        return beanDefinitionProvider.getBeanReferences();
    }

    @Override
    public BeanContext registerBeanConfiguration(BeanConfiguration configuration) {
        Objects.requireNonNull(configuration, "Configuration cannot be null");
        this.beanConfigurations.put(configuration.getName(), configuration);
        beanDefinitionProvider.registerConfiguration(configuration);
        return this;
    }

    @Override
    public <B> BeanContext registerBeanDefinition(RuntimeBeanDefinition<B> definition) {
        beanDefinitionProvider.addBeanDefinition(definition);
        purgeCacheForBeanDefinition(definition);
        beanDefinitionsEpoch.incrementAndGet();
        if (CustomScope.class.isAssignableFrom(definition.getBeanType())) {
            // a bean of this scope resolved earlier left the scope's absence recorded in the registry
            customScopeRegistry.invalidate();
        }
        return this;
    }

    private void purgeCacheForBeanDefinition(RuntimeBeanDefinition<?> definition) {
        if (beanCandidateCache.isEmpty() && beanConcreteCandidateCache.isEmpty() &&
            singletonBeanRegistrations.isEmpty() && containsBeanCache.isEmpty()) {
            return;
        }
        Class<?> beanType = definition.getBeanType();
        Class<?>[] indexedTypes = definition.getIndexes();
        Predicate<Argument<?>> isAffected = cachedType ->
            isAffectedByRegistration(cachedType, beanType, indexedTypes);
        beanCandidateCache.entrySet().removeIf(entry -> isAffected.test(entry.getKey()));
        beanConcreteCandidateCache.entrySet().removeIf(entry -> isAffected.test(entry.getKey().beanType));
        singletonBeanRegistrations.entrySet().removeIf(entry -> isAffected.test(entry.getKey().beanType));
        containsBeanCache.entrySet().removeIf(entry -> isAffected.test(entry.getKey().beanType));
    }

    private static boolean isAffectedByRegistration(Argument<?> cachedType,
                                                     Class<?> beanType,
                                                     Class<?>[] indexedTypes) {
        if (cachedType.isAssignableFrom(beanType)) {
            return true;
        }
        Class<?> rawCachedType = cachedType.getType();
        for (Class<?> indexedType : indexedTypes) {
            if (indexedType == rawCachedType) {
                return true;
            }
        }
        return false;
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
    public <T> T getBean(@Nullable BeanResolutionContext resolutionContext, Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getBean(resolutionContext, Argument.of(beanType), null);
    }

    @Override
    public <T> T getBean(BeanDefinition<T> definition) {
        ArgumentUtils.requireNonNull(ARGUMENT_DEFINITION, definition);
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
    public <T> T getBean(@Nullable BeanResolutionContext resolutionContext,
                         Class<T> beanType,
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
    public <T> T getBean(@Nullable BeanResolutionContext resolutionContext,
                         Argument<T> beanType,
                         @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return java.util.Objects.requireNonNull(resolveBeanRegistration(resolutionContext, beanType, qualifier, true)).bean;
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
    public <T> T getBean(@Nullable BeanResolutionContext resolutionContext,
                         BeanDefinition<T> beanDefinition,
                         Argument<T> beanType,
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
    public <T> Optional<T> findBean(@Nullable BeanResolutionContext resolutionContext,
                                    Class<T> beanType,
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
    public <T> Optional<T> findBean(@Nullable BeanResolutionContext resolutionContext,
                                    Argument<T> beanType,
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
    public void publishEvent(Object event) {
        if (eventsEnabled) {
            Objects.requireNonNull(event, "Event cannot be null");
            getBean(Argument.of(ApplicationEventPublisher.class, event.getClass())).publishEvent(event);
        }
    }

    @Override
    public Future<Void> publishEventAsync(Object event) {
        if (eventsEnabled) {
            Objects.requireNonNull(event, "Event cannot be null");
            return getBean(Argument.of(ApplicationEventPublisher.class, event.getClass())).publishEventAsync(event);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        for (BeanDefinition<T> beanDefinition : getBeanDefinitions(beanType, qualifier)) {
            // a bean enumerable by this type only because it is @Indexed by it is never a proxy of it
            if (beanDefinition.isProxy() && isInjectableCandidate(beanType, beanDefinition)) {
                return Optional.of(beanDefinition);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<BeanDependencyGraph> findDependencyGraph() {
        return Optional.ofNullable(dependencyGraph);
    }

    /**
     * Invalidates the bean caches. For testing only.
     */
    @Internal
    protected void invalidateCaches() {
        beanCandidateCache.clear();
        beanConcreteCandidateCache.clear();
        singletonBeanRegistrations.clear();
        indexExhaustiveCache.clear();
        containsBeanCache.clear();
        beanProxyTargetCache.clear();
    }

    /**
     * Resolves the {@link BeanConfiguration} class instances. Default implementation uses ServiceLoader pattern.
     *
     * @return The bean definition classes
     */
    protected Iterable<BeanConfiguration> resolveBeanConfigurations() {
        if (beanConfigurationsList == null) {
            beanConfigurationsList = MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(
                classLoader,
                BeanConfiguration.class,
                null
            );
        }
        return beanConfigurationsList;
    }

    private void initializeEventListeners() {
        if (eventsEnabled) {
            this.beanCreationEventListeners = loadBeanEventListeners(BeanCreatedEventListener.class);
            this.beanCreationEventListeners.add(new AbstractMap.SimpleEntry<>(BeanDefinitionProcessor.class, new BeanDefinitionProcessorListenerSupplier()));
            this.beanCreationEventListeners.add(new AbstractMap.SimpleEntry<>(ExecutableMethodProcessor.class, new ExecutableMethodProcessorListenerSupplier()));
            this.beanInitializedEventListeners = loadBeanEventListeners(BeanInitializedEventListener.class);
        }
    }

    private <T extends EventListener> List<Map.Entry<Class<?>, ListenersSupplier<T>>> loadBeanEventListeners(Class<T> listenerType) {
        final Map<Class<?>, List<BeanDefinition<T>>> typeToListener = getTypeToListenerMap(listenerType);
        if (typeToListener.isEmpty()) {
            return new ArrayList<>(1);
        }
        List<Map.Entry<Class<?>, ListenersSupplier<T>>> eventToListeners = new ArrayList<>(typeToListener.size());
        for (Map.Entry<Class<?>, List<BeanDefinition<T>>> e : typeToListener.entrySet()) {
            eventToListeners.add(new AbstractMap.SimpleEntry<>(e.getKey(), new EventListenerListenersSupplier<>(
                Argument.of(listenerType, e.getKey()),
                e.getValue()
            )));
        }
        return eventToListeners;
    }

    private <T extends EventListener> Map<Class<?>, List<BeanDefinition<T>>> getTypeToListenerMap(Class<T> listenerType) {
        Argument<T> listenerArgument = Argument.of(listenerType);
        final Collection<BeanDefinition<T>> beanDefinitions = getBeanDefinitions(listenerArgument);
        if (beanDefinitions.isEmpty()) {
            return Collections.emptyMap();
        }
        final HashMap<Class<?>, List<BeanDefinition<T>>> typeToListener = CollectionUtils.newHashMap(beanDefinitions.size());
        for (BeanDefinition<T> beanCreatedDefinition : beanDefinitions) {
            // A bean only indexed by the listener type, without implementing it, is enumerable but is not a listener
            if (!isInjectableCandidate(listenerArgument, beanCreatedDefinition)) {
                continue;
            }
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

    private void initializeContext() {
        if (!eagerBeansEnabled) {
            return;
        }
        processExecutableMethodsProcessAtStartup();
        initializeEagerBeans();
        processParallelBeans();
        checkEnabledBeans = ForkJoinPool.commonPool().submit(new ForkJoinTask<Boolean>() {

            @Override
            public Boolean getRawResult() {
                return Boolean.TRUE;
            }

            @Override
            protected void setRawResult(Boolean value) {
            }

            @Override
            protected boolean exec() {
                for (BeanDefinitionReference<Object> unused : beanDefinitionProvider.getBeanReferences(DefaultBeanContext.this)) {
                    if (isCancelled()) {
                        return true;
                    }
                }
                return true;
            }
        });
    }

    private void processExecutableMethodsProcessAtStartup() {
        Map<Class<? extends Annotation>, Collection<ExecutableMethodProcessor>> processorsByAnnotation = CollectionUtils.newLinkedHashMap(10);
        List<BeanDefinition<Object>> processedBeans = new ArrayList<>(100);
        beanDefinitionProvider.getProcessedBeans(this).forEach(processedBeans::add);
        filterReplacedBeans(processedBeans);
        for (BeanDefinition<Object> definition : processedBeans) {
            for (ExecutableMethod<Object, ?> method : definition.getExecutableMethodsForProcessing()) {
                AnnotationMetadata methodAnnotations = method.getAnnotationMetadata();
                for (Class<? extends Annotation> annotation : methodAnnotations.getAnnotationTypesByStereotype(Executable.class)) {
                    Collection<ExecutableMethodProcessor> processors = processorsByAnnotation.get(annotation);
                    if (processors == null) {
                        processors = getBeansOfType(ExecutableMethodProcessor.class, Qualifiers.byTypeArguments(annotation));
                        processorsByAnnotation.put(annotation, processors);
                        for (ExecutableMethodProcessor<?> processor : processors) {
                            if (processor instanceof LifeCycle<?> cycle) {
                                cycle.start();
                            }
                        }
                    }
                    for (ExecutableMethodProcessor<?> processor : processors) {
                        processor.process(definition, method);
                    }
                }
            }
        }

        for (Collection<ExecutableMethodProcessor> processors : processorsByAnnotation.values()) {
            for (ExecutableMethodProcessor<?> processor : processors) {
                if (processor instanceof LifeCycle<?> cycle) {
                    cycle.stop();
                }
            }
        }
    }

    private void initializeEagerBeans() {
        Iterable<BeanDefinition<Object>> eagerInitBeans = beanDefinitionProvider.getEagerInitBeans(this);
        if (eagerInitBeans.iterator().hasNext()) {
            final List<BeanDefinition<Object>> eagerInit = new ArrayList<>(20);
            for (BeanDefinition<Object> contextScopeBean : eagerInitBeans) {
                try {
                    loadEagerBeans(contextScopeBean, eagerInit);
                } catch (Throwable e) {
                    throw new BeanInstantiationException(MSG_BEAN_DEFINITION + (contextScopeBean.getName()) + MSG_COULD_NOT_BE_LOADED + e.getMessage(), e);
                }
            }
            filterReplacedBeans(eagerInit);
            OrderUtil.sortOrdered(eagerInit);
            for (BeanDefinition<Object> eagerInitDefinition : eagerInit) {
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
    @Internal
    public final <T> Collection<BeanDefinition<T>> findBeanCandidates(@Nullable BeanResolutionContext resolutionContext,
                                                                      Argument<T> beanType,
                                                                      @Nullable BeanDefinition<?> filter) {
        Predicate<BeanDefinition<T>> predicate = candidate ->
            isInjectableCandidate(beanType, candidate) && (filter == null || !candidate.equals(filter));
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
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(@Nullable BeanResolutionContext resolutionContext,
                                                                   Argument<T> beanType,
                                                                   boolean collectIterables,
                                                                   @Nullable Predicate<BeanDefinition<T>> predicate) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for type: {}", beanType);
        }
        if (beanType.getType() == Object.class) {
            // Don't include @Any providers when looking up all beans - otherwise we will add bean providers
            if (predicate == null) {
                predicate = (Predicate) FILTER_OUT_ANY_PROVIDERS;
            } else {
                predicate = predicate.and(FILTER_OUT_ANY_PROVIDERS);
            }
        }
        Iterable<BeanDefinition<T>> beanDefinitions;
        if (resolutionContext == null) {
            beanDefinitions = beanDefinitionProvider.getBeanDefinitions(this, beanType, null, predicate);
        } else {
            beanDefinitions = beanDefinitionProvider.getBeanDefinitions(resolutionContext, beanType, null, predicate);
        }
        return collectBeanCandidates(
            resolutionContext,
            beanType,
            collectIterables,
            beanDefinitions
        );
    }

    private <T> Set<BeanDefinition<T>> collectBeanCandidates(
        @Nullable BeanResolutionContext resolutionContext,
        Argument<T> beanType,
        boolean collectIterables,
        Iterable<BeanDefinition<T>> beanDefinitions) {

        Iterator<BeanDefinition<T>> iterator = beanDefinitions.iterator();
        Set<BeanDefinition<T>> candidates;
        if (iterator.hasNext()) {
            candidates = new LinkedHashSet<>();
            while (iterator.hasNext()) {
                BeanDefinition<T> candidate = iterator.next();
                if (collectIterables && candidate.isConfigurationProperties()) {
                    collectIterableBeans(resolutionContext, candidate, candidates, beanType);
                } else {
                    candidates.add(candidate);
                }
            }
            filterReplacedBeans(candidates);
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
     *
     * @param resolutionContext The resolution context
     * @param iterableBean      The iterable
     * @param targetSet         The target set
     * @param beanType          The bean type
     * @param <T>               The bean type
     */
    protected <T> void collectIterableBeans(@Nullable BeanResolutionContext resolutionContext,
                                            BeanDefinition<T> iterableBean,
                                            Set<BeanDefinition<T>> targetSet,
                                            Argument<T> beanType) {
        // no-op
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param instance The bean instance
     * @param <T>      The bean generic type
     * @return The candidates
     */
    protected <T> Collection<BeanDefinition<T>> findBeanCandidatesForInstance(T instance) {
        ArgumentUtils.requireNonNull("instance", instance);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for instance: {}", instance);
        }
        final Class<?> beanClass = instance.getClass();
        Argument<?> beanType = Argument.of(beanClass);
        Collection<BeanDefinition<T>> beanDefinitions = (Collection<BeanDefinition<T>>) ((Map) beanCandidateCache).get(beanType);
        if (beanDefinitions != null) {
            return beanDefinitions;
        }
        java.lang.Iterable<BeanDefinition<Object>> iterable = beanDefinitionProvider.getBeanDefinitions(
            this,
            Argument.OBJECT_ARGUMENT,
            ref -> ref.getBeanType().isInstance(instance),
            null
        );
        Iterator<BeanDefinition<Object>> iterator = iterable.iterator();
        List<BeanDefinition<T>> candidates;
        if (iterator.hasNext()) {
            // try narrow to exact type
            List<BeanDefinition<T>> list = new ArrayList<>(2);
            while (iterator.hasNext()) {
                BeanDefinition<Object> beanDefinition = iterator.next();
                if (beanDefinition.getBeanType() == beanClass) {
                    list.add((BeanDefinition<T>) beanDefinition);
                }
            }
            candidates = list;
        } else {
            candidates = List.of();
        }
        if (!candidates.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolved bean candidates {} for instance: {}", candidates, instance);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No bean candidates found for instance: {}", instance);
            }
        }
        beanCandidateCache.put(beanType, (Collection) candidates);
        return candidates;
    }

    /**
     * Registers an active configuration.
     *
     * @param configuration The configuration to register
     */
    protected synchronized void registerConfiguration(BeanConfiguration configuration) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        beanConfigurations.put(configuration.getName(), configuration);
        beanDefinitionProvider.registerConfiguration(configuration);
    }

    private <T> T resolveByBeanFactory(BeanResolutionContext resolutionContext,
                                       BeanDefinition<T> beanDefinition,
                                       @Nullable Qualifier<T> qualifier,
                                       @Nullable Map<String, Object> argumentValues) {
        Qualifier<T> declaredQualifier = beanDefinition.getDeclaredQualifier();
        Qualifier<?> prevQualifier = resolutionContext.getCurrentQualifier();
        try {
            resolutionContext.setCurrentQualifier(declaredQualifier != null && !AnyQualifier.INSTANCE.equals(declaredQualifier) ? declaredQualifier : qualifier);
            createDependsOnBeans(resolutionContext, beanDefinition);
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
            if (bean == null && !isNullableBeanDefinition(beanDefinition)) {
                throw new BeanInstantiationException(resolutionContext, "InstantiatableBeanDefinition [" + beanDefinition + "] returned null");
            }
            if (bean instanceof Qualified qualified && declaredQualifier != null) {
                qualified.$withBeanQualifier(declaredQualifier);
            }
            return bean;
        } catch (ConstructorAdviceException e) {
            // Advice around the constructor rejected the construction. An exception thrown by advice reaches
            // its caller as it was thrown when the advice is around a method, so it does here too.
            throw e.getAdviceCause();
        } catch (DependencyInjectionException | DisabledBeanException |
                 BeanInstantiationException e) {
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

    /**
     * Creates the beans a definition declares with {@link DependsOn} so that they exist before the bean is
     * instantiated. Being required components they are also destroyed after the bean.
     *
     * @param resolutionContext The resolution context
     * @param beanDefinition    The definition about to be instantiated
     */
    private void createDependsOnBeans(BeanResolutionContext resolutionContext, BeanDefinition<?> beanDefinition) {
        if (!beanDefinition.hasAnnotation(DependsOn.class)) {
            return;
        }
        for (Class<?> dependency : beanDefinition.classValues(DependsOn.class)) {
            if (getBeansOfType(resolutionContext, Argument.of(dependency)).isEmpty()) {
                throw new BeanInstantiationException(resolutionContext, "Bean [" + beanDefinition.getName() + "] depends on [" + dependency.getName() + "] but no bean of that type exists");
            }
        }
    }

    private <T> T postBeanCreated(BeanResolutionContext resolutionContext,
                                  BeanDefinition<T> beanDefinition,
                                  Argument<T> beanType,
                                  @Nullable Qualifier<T> qualifier,
                                  T bean) {
        Qualifier<T> finalQualifier = qualifier != null ? qualifier : beanDefinition.getDeclaredQualifier();

        bean = triggerBeanCreatedEventListener(resolutionContext, beanDefinition, bean, beanType, finalQualifier);

        if (beanDefinition instanceof ValidatedBeanDefinition<T> validatedBeanDefinition) {
            bean = validatedBeanDefinition.validate(resolutionContext, bean);
        }
        if (LOG_LIFECYCLE.isDebugEnabled()) {
            LOG_LIFECYCLE.debug("Created bean [{}] from definition [{}] with qualifier [{}]", bean, beanDefinition, finalQualifier);
        }
        return bean;
    }

    private <T> T triggerBeanCreatedEventListener(BeanResolutionContext resolutionContext,
                                                  BeanDefinition<T> beanDefinition,
                                                  T bean,
                                                  Argument<T> beanType,
                                                  @Nullable Qualifier<T> finalQualifier) {
        if (bean != null && !(beanDefinition instanceof AbstractProviderDefinition<?>)) {
            if (!(bean instanceof BeanCreatedEventListener) && CollectionUtils.isNotEmpty(beanCreationEventListeners)) {
                Class<T> beanClass = beanDefinition.getBeanType();
                List<ListenersSupplier.ListenerAndOrder<BeanCreatedEventListener>> listeners = new ArrayList<>();
                for (Map.Entry<Class<?>, ListenersSupplier<BeanCreatedEventListener>> entry : beanCreationEventListeners) {
                    if (entry.getKey().isAssignableFrom(beanClass)) {
                        for (ListenersSupplier.ListenerAndOrder<BeanCreatedEventListener> listener : entry.getValue().get(resolutionContext)) {
                            listeners.add(listener);
                        }
                    }
                }
                if (listeners.size() > 1) {
                    listeners.sort(OrderUtil.COMPARATOR_ZERO);
                }
                BeanKey<T> beanKey = new BeanKey<>(beanDefinition, finalQualifier);
                for (ListenersSupplier.ListenerAndOrder<BeanCreatedEventListener> listener : listeners) {
                    bean = (T) listener.bean.onCreated(new BeanCreatedEvent<T>(
                        this,
                        beanDefinition,
                        beanKey,
                        beanType,
                        bean,
                        resolutionContext.getRootDefinition(),
                        resolutionContext.getDependentBeans()
                    ));
                    if (bean == null) {
                        throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onCreated event");
                    }
                }
            }
        }
        return bean;
    }

    private <T> Map<String, Object> getRequiredArgumentValues(BeanResolutionContext resolutionContext,
                                                              Argument<?>[] requiredArguments,
                                                              @Nullable Map<String, Object> argumentValues,
                                                              BeanDefinition<T> beanDefinition) {
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
    protected <T> BeanDefinition<T> findConcreteCandidate(Class<T> beanType,
                                                          @Nullable Qualifier<T> qualifier,
                                                          Collection<BeanDefinition<T>> candidates) {
        if (qualifier instanceof AnyQualifier) {
            return candidates.iterator().next();
        } else {
            throw new NonUniqueBeanException(beanType, candidates.iterator());
        }
    }

    private void processParallelBeans() {
        if (!eagerBeansEnabled) {
            return;
        }
        Thread thread = new Thread(this::discoverParallelBeans, PARALLEL_BEAN_DISCOVERY_THREAD);
        // a daemon thread cannot keep the JVM alive if condition evaluation below blocks
        thread.setDaemon(true);
        parallelBeanDiscoveryThread.set(thread);
        thread.start();
    }

    private void discoverParallelBeans() {
        try {
            runParallelBeanDiscovery();
        } catch (Exception e) {
            // the discovery thread is the last handler for its own failures. the definitions are
            // iterated lazily, so a condition that throws fails in the loop header rather than in
            // the guarded body below, and would otherwise vanish with the thread
            LOG.error("Parallel bean discovery failed: {}", e.getMessage(), e);
        } finally {
            // there is nothing left to join: drop the reference rather than hold a terminated
            // thread, and its thread locals, for as long as the context runs. only this thread's
            // own registration is cleared, never one a later discovery has installed
            parallelBeanDiscoveryThread.compareAndSet(Thread.currentThread(), null);
        }
    }

    private void runParallelBeanDiscovery() {
        Iterable<BeanDefinition<Object>> parallelBeans = beanDefinitionProvider.getParallelBeans(this);
        Collection<BeanDefinition<Object>> parallelDefinitions = new ArrayList<>(20);
        for (BeanDefinition<Object> beanDefinition : parallelBeans) {
            if (isParallelStartupAborted()) {
                return;
            }
            try {
                loadEagerBeans(beanDefinition, parallelDefinitions);
            } catch (Throwable e) {
                LOG.error("Parallel Bean definition [{}{}{}]", beanDefinition.getName(), MSG_COULD_NOT_BE_LOADED, e.getMessage(), e);
                if (isShutdownOnError(beanDefinition)) {
                    stopFromParallelWorker();
                    return;
                }
            }
        }

        filterReplacedBeans(parallelDefinitions);

        for (BeanDefinition<Object> beanDefinition : parallelDefinitions) {
            if (isParallelStartupAborted()) {
                return;
            }
            submitParallelInitialization(beanDefinition);
        }
        parallelDefinitions.clear();
    }

    /**
     * Submits a parallel bean initialization, tracking it so that {@link #stop()} can wait for it.
     *
     * @param beanDefinition The definition to initialize
     */
    @SuppressWarnings("java:S1181") // an Error from a bean constructor must still honour shutdownOnError; it is logged, not swallowed
    private void submitParallelInitialization(BeanDefinition<Object> beanDefinition) {
        ParallelInitialization task = new ParallelInitialization();
        // register before submitting so that a task can never run unobserved by stop()
        parallelInitializationTasks.add(task);
        if (isParallelStartupAborted()) {
            completeParallelInitialization(task);
            return;
        }
        try {
            ForkJoinPool.commonPool().execute(() -> {
                currentParallelInitialization.set(task);
                try {
                    initializeParallelBean(beanDefinition);
                } catch (Throwable e) {
                    LOG.error("Parallel Bean definition [{}{}{}]", beanDefinition.getName(), MSG_COULD_NOT_BE_LOADED, e.getMessage(), e);
                    if (isShutdownOnError(beanDefinition)) {
                        stopFromParallelWorker();
                    }
                } finally {
                    currentParallelInitialization.remove();
                    completeParallelInitialization(task);
                }
            });
        } catch (Throwable e) {
            completeParallelInitialization(task);
            throw e;
        }
    }

    private void completeParallelInitialization(ParallelInitialization task) {
        // count down before removing, so that a task is only ever absent from the set once its
        // latch has fired. the other order leaves a window where the set looks empty to a waiter
        // that has not yet taken this task's latch
        task.completed.countDown();
        parallelInitializationTasks.remove(task);
    }

    private void initializeParallelBean(BeanDefinition<Object> beanDefinition) {
        if (isParallelStartupAborted()) {
            // shutdown began before this bean was created: never register it in the first place
            return;
        }
        List<BeanRegistration<Object>> registrations = new ArrayList<>(1);
        initializeEagerBean(beanDefinition, registrations::add);
        if (isShuttingDown()) {
            // shutdown began while this bean was being constructed. stop() waits for in-flight
            // initializations, so normally it will have seen these registrations; if it timed out
            // waiting they landed too late for the destruction pass and are destroyed here instead
            for (BeanRegistration<Object> registration : registrations) {
                destroyLateParallelBean(registration);
            }
        }
    }

    private void destroyLateParallelBean(BeanRegistration<Object> registration) {
        try {
            // only destroy a registration that is still active, so a bean the destruction pass
            // already picked up is not destroyed twice
            BeanRegistration<Object> active = singletonScope.findBeanRegistration(registration.beanDefinition);
            if (active != null) {
                destroyBean(active);
            }
        } catch (Exception e) {
            LOG.error("Error destroying parallel bean [{}] registered after the context was stopped: {}", registration.beanDefinition.getName(), e.getMessage(), e);
        }
    }

    private static boolean isShutdownOnError(BeanDefinition<?> beanDefinition) {
        return beanDefinition.getAnnotationMetadata().booleanValue(Parallel.class, "shutdownOnError").orElse(true);
    }

    /**
     * @return Whether parallel startup work should stop because the context is shutting down or has
     * already been shut down
     */
    private boolean isParallelStartupAborted() {
        return Thread.currentThread().isInterrupted() || isShuttingDown();
    }

    /**
     * @return Whether the context is shutting down or has already been shut down
     */
    private boolean isShuttingDown() {
        return terminating.get() || (!running.get() && !initializing.get());
    }

    /**
     * Stops the context from a parallel startup worker. The worker is one of the threads
     * {@link #stop()} waits for, so it must not trigger a shutdown that is already under way.
     */
    private void stopFromParallelWorker() {
        if (terminating.get()) {
            return;
        }
        stop();
    }

    /**
     * Waits for the parallel startup work to finish, so that {@link #stop()} does not race the
     * registration of parallel singletons. Bounded: a blocked discovery or initialization delays
     * shutdown by at most {@link #PARALLEL_SHUTDOWN_TIMEOUT_MS}.
     */
    private void awaitParallelStartupTermination() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PARALLEL_SHUTDOWN_TIMEOUT_MS);
        Thread discoveryThread = parallelBeanDiscoveryThread.getAndSet(null);
        if (discoveryThread != null && discoveryThread.isAlive() && discoveryThread != Thread.currentThread()) {
            discoveryThread.interrupt();
            try {
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                discoveryThread.join(Math.max(1, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (discoveryThread.isAlive() && LOG.isWarnEnabled()) {
                LOG.warn("Parallel bean discovery thread [{}] did not terminate within {}ms of the context being stopped", discoveryThread.getName(), PARALLEL_SHUTDOWN_TIMEOUT_MS);
            }
        }

        ParallelInitialization ownTask = currentParallelInitialization.get();
        if (ownTask != null) {
            // this shutdown was triggered from within a parallel initialization; that task cannot
            // complete until stop() returns, so waiting for it would deadlock
            parallelInitializationTasks.remove(ownTask);
        }
        while (true) {
            ParallelInitialization task = parallelInitializationTasks.stream().findFirst().orElse(null);
            if (task == null) {
                return;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !task.await(remaining)) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("{} parallel bean initialization(s) did not complete within {}ms of the context being stopped", parallelInitializationTasks.size(), PARALLEL_SHUTDOWN_TIMEOUT_MS);
                }
                return;
            }
            parallelInitializationTasks.remove(task);
        }
    }

    /**
     * An in-flight parallel bean initialization, tracked so that {@link #stop()} can wait for it.
     */
    private static final class ParallelInitialization {

        private final CountDownLatch completed = new CountDownLatch(1);

        /**
         * @param timeoutNanos How long to wait
         * @return Whether the initialization finished within the given time
         */
        private boolean await(long timeoutNanos) {
            try {
                return completed.await(timeoutNanos, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private <T> void filterReplacedBeans(Collection<BeanDefinition<T>> candidates) {
        if (candidates.size() > 1) {
            List<Map.Entry<ReplacesDefinition<T>, BeanDefinition<T>>> replacementTypes = new ArrayList<>(2);
            for (BeanDefinition<T> candidate : candidates) {
                ReplacesDefinition<T> beanReplacementDefinition = candidate.getReplacesDefinition();
                if (beanReplacementDefinition != null) {
                    replacementTypes.add(Map.entry(beanReplacementDefinition, candidate));
                }
            }
            if (!replacementTypes.isEmpty()) {
                candidates.removeIf(definition -> checkIfReplacementExists(replacementTypes, definition));
            }
        }
    }

    private <T> boolean checkIfReplacementExists(List<Map.Entry<ReplacesDefinition<T>, BeanDefinition<T>>> replacementTypes,
                                                 BeanDefinition<T> definitionToBeReplaced) {
        if (!definitionToBeReplaced.isCanBeReplaced()) {
            return false;
        }
        for (Map.Entry<ReplacesDefinition<T>, BeanDefinition<T>> replacement : replacementTypes) {
            BeanDefinition<T> beanDefinition = replacement.getValue();
            ReplacesDefinition<T> replacementCheck = replacement.getKey();
            if (isNotTheSameDefinition(beanDefinition, definitionToBeReplaced) &&
                isNotTheTargetOfProxy(beanDefinition, definitionToBeReplaced) &&
                replacementCheck.replaces(definitionToBeReplaced)) {
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

    private <T> boolean isNotTheTargetOfProxy(BeanDefinition<T> replacingCandidate, BeanDefinition<T> definitionToBeReplaced) {
        return !(replacingCandidate.isProxy()
            && replacingCandidate instanceof ProxyBeanDefinition<T> proxyBeanDefinition &&
            proxyBeanDefinition.getTargetDefinitionType() == definitionToBeReplaced.getClass());
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

    private void loadEagerBeans(BeanDefinition<Object> beanDefinition, Collection<BeanDefinition<Object>> collector) {
        try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
            if (beanDefinition.isEnabled(this, resolutionContext)) {
                collector.add(beanDefinition);
            }
        }
    }

    private void initializeEagerBean(BeanDefinition<Object> beanDefinition) {
        initializeEagerBean(beanDefinition, null);
    }

    @SuppressWarnings("unchecked")
    private void initializeEagerBean(BeanDefinition<Object> beanDefinition, @Nullable Consumer<BeanRegistration<Object>> registrationConsumer) {
        if (beanDefinition.isIterable() || beanDefinition.hasStereotype(ConfigurationReader.class.getName())) {
            Set<BeanDefinition<Object>> beanCandidates = new HashSet<>(5);

            collectIterableBeans(
                null,
                beanDefinition,
                beanCandidates,
                Argument.OBJECT_ARGUMENT
            );
            for (BeanDefinition beanCandidate : beanCandidates) {
                BeanRegistration<Object> registration = intializeEagerBean(
                    null,
                    beanCandidate,
                    beanCandidate.asArgument(),
                    beanCandidate.hasAnnotation(Context.class) ? null : beanDefinition.getDeclaredQualifier()
                );
                if (registrationConsumer != null) {
                    registrationConsumer.accept(registration);
                }
            }

        } else {
            BeanRegistration<Object> registration = intializeEagerBean(null, beanDefinition, beanDefinition.asArgument(), null);
            if (registrationConsumer != null) {
                registrationConsumer.accept(registration);
            }
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
                                                            Argument<T> beanType,
                                                            @Nullable Qualifier<T> qualifier,
                                                            boolean throwNoSuchBean) {
        // allow injection the bean context
        final Class<T> beanClass = beanType.getType();
        if (thisInterfaces.contains(beanClass)) {
            @SuppressWarnings("unchecked")
            BeanDefinition<T> def = (BeanDefinition<T>) new DefaultRuntimeBeanDefinition<>(
                Argument.of(beanClass),
                ctx -> (T) this,
                null,
                null,
                true,
                null,
                ReflectionUtils.EMPTY_CLASS_ARRAY,
                java.util.Collections.emptyMap(),
                new DefaultRuntimeBeanDefinition.InjectionPointSpec[0]
            );
            return BeanRegistration.of(this, BeanIdentifier.of(beanClass.getName()), def, (T) this);
        }
        if (beanClass == BeanDependencyResolver.class && qualifier == null) {
            if (resolutionContext == null || resolutionContext.getPath().isEmpty()) {
                throw new BeanContextException("BeanDependencyResolver must be injected into a managed bean");
            }
            @SuppressWarnings("unchecked")
            BeanRegistration<T> resolver = (BeanRegistration<T>) createRegistration(resolutionContext,
                Argument.of(BeanDependencyResolver.class), null, dependencyResolverDefinition, true);
            if (dependencyGraph != null && resolver.bean() instanceof DefaultBeanDependencyResolver dependencyResolver) {
                // what the bean later resolves or creates through the resolver is the bean's, as an injection would be
                dependencyResolver.owner(dependencyGraph.ownerOf(resolutionContext), true);
            }
            return resolver;
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

        if (!configured.get()) {
            // the bean definitions are unavailable before configuration and are reset on stop;
            // fail with the lifecycle error rather than an NPE from the definition lookup
            assertContextState();
        }

        Optional<BeanDefinition<T>> concreteCandidate = findBeanDefinition(resolutionContext, beanType, qualifier);

        BeanRegistration<T> registration;

        if (concreteCandidate.isPresent()) {
            BeanDefinition<T> definition = concreteCandidate.get();
            Argument<T> resolvedBeanType = resolveCandidateBeanType(beanType, definition);

            if (definition.isContainerType() && beanClass != definition.getBeanType()) {
                throw new NonUniqueBeanException(beanClass, Collections.singletonList(definition).iterator());
            }
            registration = resolveBeanRegistration(resolutionContext, definition, resolvedBeanType, qualifier);
            if (registration.bean == null) {
                registration = resolveNullBeanRegistration(beanType, resolvedBeanType, registration);
            }
        } else {
            registration = null;
        }
        if (registration == null && throwNoSuchBean) {
            throw newNoSuchBeanException(resolutionContext, beanType, qualifier, null);
        }
        if (registration != null && registration.bean == null && throwNoSuchBean && !isNullableBeanDefinition(registration.beanDefinition)) {
            throw newNoSuchBeanException(resolutionContext, beanType, qualifier, null);
        }
        return registration;
    }

    private <T> Argument<T> resolveCandidateBeanType(Argument<T> requestedBeanType, BeanDefinition<T> beanDefinition) {
        if (beanResolutionCustomizer.isCandidateBean(requestedBeanType, beanDefinition)) {
            return requestedBeanType;
        }
        Argument<T> lookupBeanType = resolveBeanLookupArgument(requestedBeanType);
        if (!lookupBeanType.equals(requestedBeanType) && beanResolutionCustomizer.isCandidateBean(lookupBeanType, beanDefinition)) {
            return lookupBeanType;
        }
        return requestedBeanType;
    }

    @SuppressWarnings("unchecked")
    private <T> BeanRegistration<T> resolveNullBeanRegistration(Argument<T> beanType,
                                                                Argument<T> resolvedBeanType,
                                                                BeanRegistration<T> registration) {
        Optional<?> resolvedNullBean = beanResolutionCustomizer.resolveNullBean(beanType, resolvedBeanType, registration.beanDefinition);
        if (resolvedNullBean.isPresent()) {
            return BeanRegistration.of(this, registration.identifier, registration.beanDefinition, (T) resolvedNullBean.get());
        }
        return registration;
    }

    private static boolean isNullableBeanDefinition(BeanDefinition<?> beanDefinition) {
        return beanDefinition.getAnnotationMetadata().hasStereotype(io.micronaut.core.annotation.AnnotationUtil.NULLABLE);
    }

    private void assertContextState() {
        if (!this.running.get() && !this.initializing.get()) {
            // resolving against a context in the wrong lifecycle state, including a stale
            // reference held across shutdown, is what IllegalStateException means in the JDK
            throw new IllegalStateException("Cannot resolve beans until the context is running");
        }
    }

    private <T> Optional<BeanDefinition<T>> findBeanDefinition(@Nullable BeanResolutionContext resolutionContext, Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    protected <T> NoSuchBeanException newNoSuchBeanException(
        @Nullable BeanResolutionContext resolutionContext,
        Argument<T> beanType,
        @Nullable Qualifier<T> qualifier,
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
     *
     * @param resolutionContext The resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type
     * @return The message or null if none exists
     */
    @Nullable
    protected <T> String resolveDisabledBeanMessage(@Nullable BeanResolutionContext resolutionContext, Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        StringBuilder stringBuilder = new StringBuilder();
        resolveDisabledBeanMessage("", stringBuilder, java.util.Objects.requireNonNullElse(CachedEnvironment.getProperty("line.separator"), System.lineSeparator()), resolutionContext, beanType, qualifier);
        return stringBuilder.isEmpty() ? null : stringBuilder.toString();
    }

    @Internal
    final <T> void resolveDisabledBeanMessage(String linePrefix,
                                              StringBuilder messageBuilder,
                                              String lineSeparator,
                                              @Nullable BeanResolutionContext resolutionContext,
                                              Argument<T> beanType,
                                              @Nullable Qualifier<T> qualifier) {
        if (linePrefix.length() == 10) {
            // Break possible cyclic dependencies
            return;
        }

        for (Map.Entry<String, List<String>> entry : disabledConfigurations.entrySet()) {
            String pkg = entry.getKey();
            if (beanType.getTypeName().startsWith(pkg + ".")) {
                messageBuilder.append(lineSeparator)
                    .append(linePrefix)
                    .append("* [")
                    .append(beanType.getTypeString(true))
                    .append("] is disabled because it is within the package [")
                    .append(pkg)
                    .append("] which is disabled due to bean requirements: ")
                    .append(lineSeparator);
                for (String failure : entry.getValue()) {
                    messageBuilder
                        .append(linePrefix)
                        .append(" - ")
                        .append(failure)
                        .append(lineSeparator);
                }
                messageBuilder.setLength(messageBuilder.length() - lineSeparator.length());
                return;
            }
        }

        Collection<BeanDefinition<T>> beanDefinitions = collectBeanCandidates(
            resolutionContext,
            beanType,
            false,
            beanDefinitionProvider.getDisabledBeans(this).stream().<BeanDefinition<T>>mapMulti((disabledBean, consumer) -> {
                if (beanResolutionCustomizer.isCandidateBean(beanType, disabledBean)) {
                    consumer.accept((BeanDefinition<T>) disabledBean);
                }
            }).toList()
        ).stream()
            .sorted(Comparator.comparing(BeanDefinition::getName))
            .toList();
        if (qualifier != null) {
            beanDefinitions = qualifier.filterQualified(beanType.getType(), beanDefinitions);
        }

        if (!beanDefinitions.isEmpty()) {
            for (BeanDefinition<T> beanDefinition : beanDefinitions) {
                messageBuilder
                    .append(lineSeparator)
                    .append(linePrefix)
                    .append("* [").append(beanDefinition.asArgument().getTypeString(true));
                if (!beanDefinition.getBeanType().equals(beanType.getType())) {
                    messageBuilder.append("] a candidate of [")
                        .append(beanType.getTypeString(true));
                }
                messageBuilder.append("] is disabled because:")
                    .append(lineSeparator);
                if (beanDefinition instanceof DisabledBean<T> disabledBean) {
                    for (String failure : disabledBean.reasons()) {
                        messageBuilder
                            .append(linePrefix)
                            .append(" - ")
                            .append(failure)
                            .append(lineSeparator);
                        String prefix = "No bean of type [";
                        if (failure.startsWith(prefix)) {
                            ClassUtils.forName(failure.substring(prefix.length(), failure.indexOf("]")), classLoader)
                                .ifPresent(beanClass -> {
                                    messageBuilder.setLength(messageBuilder.length() - lineSeparator.length());
                                    resolveDisabledBeanMessage(linePrefix + " ",
                                        messageBuilder,
                                        lineSeparator,
                                        resolutionContext,
                                        Argument.of(beanClass),
                                        null);
                                    messageBuilder.append(lineSeparator);
                                });
                        }
                    }
                    messageBuilder.setLength(messageBuilder.length() - lineSeparator.length());
                }
            }
        }
    }

    private <T> @Nullable BeanRegistration<T> provideInjectionPoint(@Nullable BeanResolutionContext resolutionContext,
                                                                    Argument<T> beanType,
                                                                    @Nullable Qualifier<T> qualifier,
                                                                    boolean throwNoSuchBean) {
        final BeanResolutionContext.Path path = resolutionContext != null ? resolutionContext.getPath() : null;
        BeanResolutionContext.Segment<?, ?> injectionPointSegment = null;
        if (path != null) {
            final Iterator<BeanResolutionContext.Segment<?, ?>> i = path.iterator();
            if (i.hasNext()) {
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
                        BeanDefinition<?> declaring = segment.getDeclaringType();
                        if (declaring != null) {
                            @SuppressWarnings("unchecked") BeanDefinition<T> bd = (BeanDefinition<T>) declaring;
                            return BeanRegistration.of(this, BeanIdentifier.of(InjectionPoint.class.getName()), bd, ip);
                        }
                        if (throwNoSuchBean) {
            throw newNoSuchBeanException(resolutionContext, beanType, qualifier, "");

                        }
                        return null;
                    }
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
    private <T> BeanRegistration<T> resolveBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                            BeanDefinition<T> definition) {
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
    private <T> BeanRegistration<T> resolveBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                            BeanDefinition<T> definition,
                                                            Argument<T> beanType,
                                                            @Nullable Qualifier<T> qualifier) {
        return resolveBeanRegistration(resolutionContext, definition, beanType, qualifier, false);
    }

    /**
     * Resolve the {@link BeanRegistration} by a {@link BeanDefinition}.
     *
     * @param resolutionContext The resolution context
     * @param definition        The bean type
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param heldRegistration  Whether a scoped bean comes back in the registration its scope stores, which carries
     *                          what was created for the bean, rather than in one built for it
     * @param <T>               The type
     * @return The bean registration
     */
    private <T> BeanRegistration<T> resolveBeanRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                            BeanDefinition<T> definition,
                                                            Argument<T> beanType,
                                                            @Nullable Qualifier<T> qualifier,
                                                            boolean heldRegistration) {
        assertContextState();
        final boolean isScopedProxyDefinition = definition.hasStereotype(SCOPED_PROXY_ANN);

        if (qualifier != null && AnyQualifier.INSTANCE.equals(definition.getDeclaredQualifier())) {
            // Any factory bean should be stored as a new bean definition with a qualifier
            definition = BeanDefinitionDelegate.create(definition, qualifier);
        }

        if (definition.isSingleton() && !isScopedProxyDefinition) {
            BeanRegistration<T> beanRegistration = singletonScope.findBeanRegistration(definition, beanType, qualifier);
            if (beanRegistration == null) {
                beanRegistration = singletonScope.getOrCreate(this, resolutionContext, definition, beanType, qualifier);
            }
            recordDependency(resolutionContext, beanRegistration.beanDefinition, qualifier);
            return beanRegistration;
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
                if (q != null) {
                    ((Qualified<T>) bean).$withBeanQualifier(q);
                }
            }
            // the receiving bean holds the scoped proxy, which is what a reload must know
            recordDependency(resolutionContext, registration.beanDefinition, q);
            return registration;
        }

        CustomScope<?> customScope = findCustomScope(resolutionContext, definition, isProxy, isScopedProxyDefinition);
        if (customScope != null) {
            if (isProxy) {
                definition = getProxyTargetBeanDefinition(beanType, qualifier);
            }
            BeanRegistration<T> scoped = getOrCreateScopedRegistration(resolutionContext, customScope, qualifier, beanType, definition, heldRegistration);
            recordDependency(resolutionContext, scoped.beanDefinition, qualifier);
            return scoped;
        }
        // Unknown scope, prototype scope etc
        BeanRegistration<T> prototype = createRegistration(resolutionContext, beanType, qualifier, definition, true);
        // a prototype belongs to the bean that received it, and what the prototype received is recorded
        // under the prototype's definition, so a path through it is not lost
        recordDependency(resolutionContext, prototype.beanDefinition, qualifier);
        return prototype;
    }

    private <T> BeanRegistration<T> intializeEagerBean(@Nullable BeanResolutionContext resolutionContext,
                                                       BeanDefinition<T> definition,
                                                       Argument<T> beanType,
                                                       @Nullable Qualifier<T> qualifier) {
        BeanRegistration<T> beanRegistration = singletonScope.findBeanRegistration(definition, beanType, qualifier);
        if (beanRegistration != null) {
            return beanRegistration;
        }
        return singletonScope.getOrCreate(this, resolutionContext, definition, beanType, qualifier);
    }

    @Nullable
    private <T> CustomScope<?> findCustomScope(@Nullable BeanResolutionContext resolutionContext,
                                               BeanDefinition<T> definition,
                                               boolean isProxy,
                                               boolean isScopedProxyDefinition) {
        Optional<Class<? extends Annotation>> scope = definition.getScope();
        if (scope.isPresent()) {
            Class<? extends Annotation> scopeAnnotation = scope.get();
            if (scopeAnnotation == Prototype.class) {
                // a prototype bean has no lifecycle of its own, so a scope declared on the
                // injection point (such as @InjectScope) still applies to it
                return findInjectionPointDeclaredScope(resolutionContext, definition);
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
                    // a prototype bean has no lifecycle of its own, so a scope declared on the
                    // injection point (such as @InjectScope) still applies to it
                    return findInjectionPointDeclaredScope(resolutionContext, definition);
                }
                CustomScope<?> customScope = customScopeRegistry.findScope(scopeAnnotation).orElse(null);
                if (customScope != null) {
                    return customScope;
                }
            }
        }

        CustomScope<?> injectionPointScope = findInjectionPointDeclaredScope(resolutionContext, definition);
        if (injectionPointScope != null) {
            return injectionPointScope;
        }

        if (!isScopedProxyDefinition || !isProxy) {
            return customScopeRegistry.findDeclaredScope(definition).orElse(null);
        }
        return null;
    }

    @Nullable
    private CustomScope<?> findInjectionPointDeclaredScope(@Nullable BeanResolutionContext resolutionContext,
                                                           @Nullable BeanDefinition<?> definition) {
        if (resolutionContext != null) {
            BeanResolutionContext.Segment<?, ?> currentSegment = resolutionContext
                .getPath()
                .currentSegment()
                .orElse(null);
            if (currentSegment != null) {
                if (definition != null && isFactoryOf(definition, currentSegment.getDeclaringType())) {
                    // the bean is the factory that produces the segment's bean rather than an injection point of
                    // it, and a scope declared on the produced bean does not apply to its factory. Applying it
                    // would resolve the factory through the very scope that is creating the produced bean,
                    // re-entering that scope for a second bean while the first is still being created.
                    return null;
                }
                Argument<?> argument = currentSegment.getArgument();
                return customScopeRegistry.findDeclaredScope(argument).orElse(null);
            }
        }
        return null;
    }

    /**
     * Whether the given definition is the factory the produced bean's definition declares its factory method on.
     *
     * @param definition   The definition being resolved
     * @param producedBean The definition of the bean the current segment produces
     * @return True if the definition is the factory of the produced bean
     */
    private static boolean isFactoryOf(BeanDefinition<?> definition, @Nullable BeanDefinition<?> producedBean) {
        return producedBean != null && producedBean.getDeclaringType().orElse(null) == definition.getBeanType();
    }

    private <T> BeanRegistration<T> getOrCreateScopedRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                                  CustomScope<?> registeredScope,
                                                                  @Nullable Qualifier<T> qualifier,
                                                                  Argument<T> beanType,
                                                                  BeanDefinition<T> definition,
                                                                  boolean heldRegistration) {
        BeanKey<T> beanKey = new BeanKey<>(definition.asArgument(), qualifier);
        AtomicReference<BeanRegistration<T>> created = heldRegistration ? new AtomicReference<>() : null;
        T bean = registeredScope.getOrCreate(
            new BeanCreationContext<T>() {
                @Override
                public BeanDefinition<T> definition() {
                    return definition;
                }

                @Override
                public BeanIdentifier id() {
                    return beanKey;
                }

                @Override
                public CreatedBean<T> create() throws BeanCreationException {
                    BeanRegistration<T> registration = createRegistration(resolutionContext == null ? null : resolutionContext.copy(), beanKey.beanType, qualifier, definition, true);
                    if (created != null) {
                        created.set(registration);
                    }
                    return registration;
                }
            }
        );
        if (created != null) {
            // the scope hands back the bean alone: the registration it stores is the one created above, or on a hit
            // the one it finds for the bean
            BeanRegistration<T> registration = created.get();
            if (registration != null && registration.bean == bean) {
                return registration;
            }
            Optional<BeanRegistration<T>> stored = registeredScope.findBeanRegistration(bean);
            if (stored.isPresent()) {
                return stored.get();
            }
        }
        return BeanRegistration.of(this, beanKey, definition, bean);
    }

    @Internal
    final <T> BeanRegistration<T> createRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                     Argument<T> beanType,
                                                     @Nullable Qualifier<T> qualifier,
                                                     BeanDefinition<T> definition,
                                                     boolean dependent) {
        return createRegistration(resolutionContext, beanType, qualifier, definition, dependent, false);
    }

    final <T> BeanRegistration<T> createFreshRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                         BeanDefinition<T> definition) {
        BeanRegistration<T> registration = createRegistration(resolutionContext, definition.asArgument(), definition.getDeclaredQualifier(),
            definition, resolutionContext != null, true);
        if (dependencyGraph != null) {
            // what the fresh instance received is recorded under its definition as it is created; a singleton's
            // fresh instance shares those edges with the scoped one, so destroying either must not forget them
            dependencyGraph.freshCreated(registration);
        }
        return registration;
    }

    /**
     * Records that the owner of a resolver received a bean through it, as the bean's injection would have been.
     *
     * @param owner The owner, or null when the graph is not recorded or the resolver has none
     * @param registration The received registration
     */
    final void recordOwnedDependency(DefaultBeanDependencyGraph.@Nullable Owner owner, BeanRegistration<?> registration) {
        if (dependencyGraph != null && owner != null) {
            dependencyGraph.recordOwned(owner, registration.getBeanDefinition());
        }
    }

    /**
     * Whether the context records a {@link BeanDependencyGraph}.
     *
     * @return True when bean dependencies are tracked
     */
    final boolean isTrackingBeanDependencies() {
        return dependencyGraph != null;
    }

    /**
     * Records, when the graph is recorded, that the bean being created received the given bean at the current
     * injection point. A provider is recorded as what it resolves: an edge, marked lazy, to each definition its type
     * argument and qualifier select, since every provider of a kind shares one definition that names no target.
     *
     * @param resolutionContext The resolution context of the receiving bean
     * @param received The definition of the received bean
     * @param qualifier The qualifier the bean was resolved with
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void recordDependency(@Nullable BeanResolutionContext resolutionContext, BeanDefinition<?> received, @Nullable Qualifier<?> qualifier) {
        DefaultBeanDependencyGraph graph = dependencyGraph;
        if (graph == null || resolutionContext == null) {
            return;
        }
        BeanDefinition<?> target = received instanceof BeanDefinitionDelegate<?> delegate ? delegate.getDelegate() : received;
        if (target instanceof AbstractProviderDefinition<?>) {
            Argument provided = DefaultBeanDependencyGraph.providedArgument(resolutionContext);
            // a provider of Object names no target: every bean would be a candidate
            if (provided != null && provided.getType() != Object.class) {
                graph.recordProvided(resolutionContext, getBeanDefinitions(provided, (Qualifier) providerQualifier(resolutionContext, qualifier)));
            }
            return;
        }
        graph.record(resolutionContext, received);
    }

    /**
     * The qualifier a provider resolves under, as {@link AbstractProviderDefinition} decides it: the qualifier it was
     * looked up with, or for a provider injected into an iterable bean without one, the name the resolution carries.
     *
     * @param resolutionContext The resolution context injecting the provider
     * @param qualifier The qualifier the provider was looked up with
     * @return The qualifier the provider resolves under
     */
    private static @Nullable Qualifier<?> providerQualifier(BeanResolutionContext resolutionContext, @Nullable Qualifier<?> qualifier) {
        if (qualifier != null) {
            return qualifier;
        }
        BeanResolutionContext.Segment<?, ?> segment = resolutionContext.getPath().currentSegment().orElse(null);
        Object name = resolutionContext.getAttribute(Named.class.getName());
        return name != null && segment != null && segment.getDeclaringType().isIterable() ? Qualifiers.byName(name.toString()) : null;
    }

    @SuppressWarnings({"unchecked", "NullAway"}) // Nullable factory definitions may produce a registration without an instance.
    private <T> BeanRegistration<T> createRegistration(@Nullable BeanResolutionContext resolutionContext,
                                                      Argument<T> beanType,
                                                      @Nullable Qualifier<T> qualifier,
                                                      BeanDefinition<T> definition,
                                                      boolean dependent,
                                                      boolean customizeNull) {
        if (resolutionContext instanceof AbstractBeanResolutionContext abstractContext && abstractContext.isLazyProxyTarget()) {
            // The context is retained by a lazy proxy, which resolves its target through it on every call. Create
            // the bean in a copy, so that the created bean is not recorded as a dependent of the retained context
            // and the resolution path is not shared by concurrent calls.
            resolutionContext = resolutionContext.copy();
        }
        try (BeanResolutionContext context = newResolutionContext(definition, resolutionContext)) {
            final BeanResolutionContext.Path path = context.getPath();
            final boolean isNewPath = path.isEmpty();
            if (isNewPath) {
                Argument<T> resolvedBeanType;
                if (qualifier instanceof TypeArgumentQualifier<T> taq) {
                    Class<?>[] typeArguments = taq.getTypeArguments();
                    resolvedBeanType = Argument.of(
                        beanType.getType(),
                        beanType.getAnnotationMetadata(),
                        typeArguments
                    );
                } else {
                    resolvedBeanType = beanType;
                }
                path.pushBeanCreate(definition, resolvedBeanType);
            }
            try {
                AbstractBeanResolutionContext creatingContext = context instanceof AbstractBeanResolutionContext creating ? creating : null;
                BeanCreationState previousCreation = creatingContext == null ? null : creatingContext.creationState;
                BeanCreationState creation = creatingContext == null
                    ? new BeanCreationState(definition, List.of()) : creatingContext.beginCreation(definition);
                try {
                    List<BeanRegistration<?>> parentDependentBeans = context.popDependentBeans();
                    BeanRegistration<T> beanRegistration;
                    try {
                        T bean;
                        if (definition instanceof InstantiatableBeanDefinition<T> instantiatableBeanDefinition) {
                            bean = resolveByBeanFactory(context, instantiatableBeanDefinition, qualifier, Collections.emptyMap());
                        } else {
                            throw new BeanInstantiationException("BeanDefinition doesn't support creating a new instance of the bean");
                        }
                        InterceptorCandidates interceptorCandidates = creation.lifecycleInterceptorCandidates();
                        if (context.getAttribute(BeanResolutionContext.INTERCEPTOR_REGISTRATIONS) instanceof Map<?, ?> registrations) {
                            Object value = registrations.remove(definition);
                            if (value instanceof List<?> list) {
                                interceptorCandidates = new InterceptorCandidates.Resolved((List) list);
                            }
                        }
                        T created = bean;
                        bean = postBeanCreated(context, definition, beanType, qualifier, bean);
                        if (customizeNull && bean == null) {
                            bean = (T) beanResolutionCustomizer.resolveNullBean(beanType, beanType, definition).orElse(null);
                        }

                        BeanRegistration<?> dependentFactoryBean = context.getAndResetDependentFactoryBean();
                        if (dependentFactoryBean != null) {
                            destroyBean(dependentFactoryBean);
                        }
                        Qualifier<T> registrationQualifier = qualifier;
                        if (registrationQualifier == null) {
                            registrationQualifier = definition.getDeclaredQualifier();
                        }
                        BeanKey<T> beanKey = new BeanKey<>(beanType, registrationQualifier);
                        List<BeanRegistration<?>> dependentBeans = context.getAndResetDependentBeans();
                        BeanDisposingRegistration<T> disposing = new BeanDisposingRegistration<>(this, beanKey, definition, bean,
                            dependentBeans, interceptorCandidates, creation.dependencies);
                        if (dependencyGraph != null && created != bean) {
                            // a listener replaced the instance: a development context retains the one it received
                            disposing.setBeforeListeners(created);
                        }
                        beanRegistration = disposing;
                    } catch (RuntimeException | Error e) {
                        destroyDependentsOfFailedBean(context, e);
                        destroyCreatedBeans(creation.dependencies.takeDependents(), e);
                        context.pushDependentBeans(parentDependentBeans);
                        throw e;
                    }
                    if (definition instanceof ProxyBeanDefinition<?> proxyDefinition
                        && context instanceof AbstractBeanResolutionContext creating
                        && beanRegistration instanceof BeanDisposingRegistration<T> disposingRegistration) {
                        // the context a lazy proxy retains holds what is created with the target it caches
                        disposingRegistration.setProxyTargetContext(creating.takeLazyProxyTargetCopy(proxyDefinition.getTargetDefinitionType()));
                    }
                    context.pushDependentBeans(parentDependentBeans);
                    if (dependent) {
                        context.addDependentBean(beanRegistration);
                    }
                    return beanRegistration;
                } finally {
                    if (creatingContext != null) {
                        creatingContext.creationState = previousCreation;
                    }
                }
            } finally {
                if (isNewPath) {
                    path.close();
                }
            }
        }
    }

    /**
     * Destroys the objects created for a bean whose creation failed: its dependents, and its factory bean when the
     * factory is not a singleton.
     *
     * <p>Nothing else would destroy them. They are owned by the registration of the bean, which is never created, and
     * the resolution context that collected them is either discarded or goes on to collect the dependents of the bean
     * whose creation this one is nested in. The dependents are destroyed as the dependents of a destroyed bean are,
     * in the reverse of the order they were created in, and the factory bean as it is once a bean has been created.
     * A failure to destroy one of them is added to the failure of the creation.</p>
     *
     * @param context The resolution context of the failed creation
     * @param failure The failure of the creation
     */
    @SuppressWarnings("java:S1181") // Factory cleanup Errors must not replace the original creation failure.
    private void destroyDependentsOfFailedBean(BeanResolutionContext context, Throwable failure) {
        BeanRegistration<?> dependentFactoryBean = context.getAndResetDependentFactoryBean();
        destroyCreatedBeans(context.getAndResetDependentBeans(), failure);
        if (dependentFactoryBean != null) {
            try {
                destroyBean(dependentFactoryBean);
            } catch (RuntimeException | Error e) {
                if (failure != e) {
                    failure.addSuppressed(e);
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
                                                                  Argument<T> beanType,
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
                                                                         Argument<T> beanType,
                                                                         @Nullable Qualifier<T> qualifier,
                                                                         boolean throwNonUnique) {

        Predicate<BeanDefinition<T>> predicate = candidate -> !candidate.isAbstract() && isInjectableCandidate(beanType, candidate);
        Collection<BeanDefinition<T>> candidates = findBeanCandidates(resolutionContext, beanType, true, predicate);
        Optional<BeanDefinition<T>> beanDefinition = pickOneBean(beanType, qualifier, throwNonUnique, candidates);
        if (beanDefinition.isPresent()) {
            return beanDefinition;
        }
        Argument<T> lookupBeanType = resolveBeanLookupArgument(beanType);
        if (!lookupBeanType.equals(beanType)) {
            Predicate<BeanDefinition<T>> lookupPredicate = candidate -> !candidate.isAbstract() && isInjectableCandidate(lookupBeanType, candidate);
            candidates = findBeanCandidates(resolutionContext, lookupBeanType, true, lookupPredicate);
            return pickOneBean(lookupBeanType, qualifier, throwNonUnique, candidates);
        }
        return Optional.empty();
    }

    private <T> Optional<BeanDefinition<T>> findProxyTargetNoCache(@Nullable BeanResolutionContext resolutionContext,
                                                                   Argument<T> beanType,
                                                                   @Nullable Qualifier<T> qualifier) {

        // TODO: Improve
        List<BeanDefinition<Object>> targetProxyBeans = CollectionUtils.iterableToList(beanDefinitionProvider.getTargetProxyBeans(this));
        Collection<BeanDefinition<T>> candidates = collectBeanCandidates(
            resolutionContext,
            beanType,
            true,
            targetProxyBeans.stream().<BeanDefinition<T>>mapMulti((beanDefinition, consumer) -> {
                if (beanResolutionCustomizer.isCandidateBean(beanType, beanDefinition)) {
                    consumer.accept((BeanDefinition<T>) beanDefinition);
                }
            }).toList()
        );
        return pickOneBean(beanType, qualifier, false, candidates);
    }

    private <T> Optional<BeanDefinition<T>> pickOneBean(Argument<T> beanType,
                                                        @Nullable Qualifier<T> qualifier,
                                                        boolean throwNonUnique,
                                                        Collection<BeanDefinition<T>> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (qualifier != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Qualifying bean [{}] for qualifier: {} ", beanType.getName(), qualifier);
            }
            candidates = qualifier.filterQualified(beanType.getType(), candidates);
            if (candidates.isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No qualifying beans of type [{}] found for qualifier: {} ", beanType.getName(), qualifier);
                }
                return Optional.empty();
            }
        }
        BeanDefinition<T> definition;
        if (candidates.size() == 1) {
            definition = candidates.iterator().next();
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Searching for @Primary for type [{}] from candidates: {} ", beanType.getName(), candidates);
            }
            definition = lastChanceResolve(beanType, qualifier, throwNonUnique, candidates);
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

    @Nullable
    private <T> BeanDefinition<T> lastChanceResolve(Argument<T> beanType,
                                                    @Nullable Qualifier<T> qualifier,
                                                    boolean throwNonUnique,
                                                    Collection<BeanDefinition<T>> candidates) {
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
        Optional<BeanDefinition<T>> customResolved = beanResolutionCustomizer.resolveNonUniqueBean(beanType, qualifier, candidates);
        if (customResolved.isPresent()) {
            return customResolved.get();
        }
        Collection<BeanDefinition<T>> originalCandidates = candidates;
        candidates = candidates.stream().filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class)).toList();
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        // When every candidate is @Secondary none of them is preferable to another, but lowering the
        // precedence of all of them shouldn't remove the ability to decide between them: carry on with
        // the original candidates so that the order and @DefaultImplementation still get a say.
        boolean allSecondary = candidates.isEmpty();
        if (allSecondary) {
            candidates = originalCandidates;
        }
        // pick the bean with the highest priority
        ArrayList<BeanDefinition<T>> listCandidates = new ArrayList<>(candidates);
        listCandidates.sort(OrderUtil.ORDERED_COMPARATOR);
        Iterator<BeanDefinition<T>> iterator = listCandidates.iterator();
        final BeanDefinition<T> bean = iterator.next();
        final BeanDefinition<T> next = iterator.next();
        // We should have at least two beans - no need for next checks
        // Check there are not 2 beans with the same order
        if (bean.getOrder() != next.getOrder()) {
            LOG.debug("Picked bean {} with the highest precedence for type {} and qualifier {}", bean, beanType, null);
            return bean;
        }

        // try resolve @DefaultImplementation
        BeanDefinition<T> first = candidates.iterator().next();
        Class<?> defaultImplementation = first.getDefaultImplementation();
        if (defaultImplementation != null) {
            for (BeanDefinition<T> bd : candidates) {
                if (bd.getBeanType().equals(defaultImplementation)) {
                    return bd;
                }
            }
        }
        Collection<BeanDefinition<T>> exactMatches = filterExactMatch(beanType.getType(), candidates);
        if (exactMatches.size() == 1) {
            return exactMatches.iterator().next();
        }
        if (allSecondary) {
            throw new NonUniqueBeanException(beanType.getType(), originalCandidates.iterator());
        }
        if (throwNonUnique) {
            return findConcreteCandidate(beanType.getType(), qualifier, candidates);
        }
        return null;
    }

    private void readAllBeanConfigurations() {
        if (beanConfigurations.isEmpty()) {
            Iterable<BeanConfiguration> beanConfigurations = resolveBeanConfigurations();
            for (BeanConfiguration beanConfiguration : beanConfigurations) {
                registerConfiguration(beanConfiguration);
            }
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

    private void configureAndStartContext() {
        registerConversionService();
        configureContextInternal();
        initializeEventListeners();
        wrapAdoptedRegistrations();
        destroyRejectedRetainedRegistrations();
        initializeTypeConverters();
        initializeContext();
    }

    /**
     * Stops the context, destroying every singleton except those the given predicate accepts, which
     * are returned instead so that a new context can {@link ApplicationContextBuilder#retainedRegistrations(Collection) adopt}
     * them. No {@code @PreDestroy} method runs and no destruction event is published for a retained bean.
     *
     * <p>For a development launcher restarting the application: a connection pool or a client survives
     * the restart, and the beans that depend on it are created again on top of it. The launcher is
     * responsible for retaining only beans whose classes and dependencies are not being replaced, which
     * the {@link BeanDependencyGraph} tells it. When the graph is recorded, the singletons a retained bean
     * holds are retained with it, since it keeps them anyway, and a bean is not retained at all when it or
     * one of them is a proxy or holds a {@link BeanProvider}, a {@code Provider}, a proxy, the context, its
     * environment, its event publisher or its conversion service, because those would keep resolving
     * through this stopped context; without the graph the predicate answers for all of that.
     * When the graph is recorded, a bean that a {@link io.micronaut.context.event.BeanCreatedEventListener} replaced,
     * by a wrapper that may hold this context, is retained as the listeners received it; the wrapper is dropped, neither
     * destroyed nor announced. The adopting context runs its own listeners on every retained bean, as the bean's
     * creation would have, so a listener that configures what it receives must be idempotent.
     * Whoever holds the returned registrations destroys them eventually, through the context that adopted
     * them or {@link #destroyBean(BeanRegistration)}.</p>
     *
     * @param retain Which singleton registrations to keep alive
     * @return The retained registrations, in no particular order
     * @since 5.3.0
     */
    @Internal
    @Experimental
    public Collection<BeanRegistration<?>> stopRetaining(Predicate<BeanRegistration<?>> retain) {
        ArgumentUtils.requireNonNull("retain", retain);
        return stopRetaining(new RetentionCriteria() {
            @Override
            public boolean retain(BeanRegistration<?> registration) {
                return retain.test(registration);
            }

            @Override
            public Set<String> invalidatedBy(BeanRegistration<?> registration) {
                return Set.of();
            }
        });
    }

    /**
     * Stops the context as {@link #stopRetaining(Predicate)} does, retaining the singletons the criteria
     * {@link RetentionCriteria#retain(BeanRegistration) retain}. A configuration bean, one of
     * {@link io.micronaut.context.annotation.ConfigurationProperties}, {@link io.micronaut.context.annotation.EachProperty}
     * or another {@link ConfigurationReader}, that a retained bean holds does not keep it from being retained when its
     * prefix is one of the prefixes {@link RetentionCriteria#invalidatedBy(BeanRegistration) whose change releases} the
     * retained bean, or under one: the configuration bean is not retained, the next context creates its own, and the
     * retained bean keeps what it copied from it, which stays valid until a change under that prefix releases the
     * bean. A retained bean must therefore not keep its configuration bean, only its values. A configuration bean
     * under another prefix is treated as any other bean the retained bean holds.
     *
     * @param criteria Which singleton registrations to keep alive
     * @return The retained registrations, in no particular order
     * @since 5.3.0
     */
    @Internal
    @Experimental
    public synchronized Collection<BeanRegistration<?>> stopRetaining(RetentionCriteria criteria) {
        ArgumentUtils.requireNonNull("criteria", criteria);
        retainedOnStop.clear();
        retentionCriteria = criteria;
        try {
            stop();
        } finally {
            retentionCriteria = null;
        }
        List<BeanRegistration<?>> retained = List.copyOf(retainedOnStop);
        retainedOnStop.clear();
        return retained;
    }

    /**
     * Registers the singletons of a previous context this one was configured with, under this context's
     * definitions, before any bean is created, so that lookups and injections find the adopted instances.
     */
    private void adoptRetainedRegistrations() {
        Collection<BeanRegistration<?>> registrations = registrationsToAdopt;
        // adopted once: a later restart of this context must not register instances it has destroyed since
        registrationsToAdopt = List.of();
        // a registered instance first: an @EachBean member of it, which comes before it in the destruction order the
        // registrations follow, is only found once this context has the instance's definition
        for (BeanRegistration<?> registration : registrations) {
            if (originalOf(registration).getBeanDefinition() instanceof RuntimeBeanDefinition<?> runtime) {
                resolveAdoptedDefinition(runtime);
            }
        }
        // decided for all before any is registered: a bean whose held dependency cannot be adopted is not
        // adopted either, since it would keep holding a bean this context destroys
        Map<BeanRegistration<?>, BeanDefinition<?>> adoptable = new LinkedHashMap<>();
        List<BeanRegistration<?>> rejected = new ArrayList<>();
        for (BeanRegistration<?> registration : registrations) {
            BeanDefinition<?> definition = resolveAdoption(registration);
            if (definition != null) {
                adoptable.put(registration, definition);
            } else {
                rejected.add(registration);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BeanRegistration<?> registration : List.copyOf(adoptable.keySet())) {
                BeanRegistration<?> rejectedDependency = rejectedDependencyOf(registration, rejected);
                if (rejectedDependency != null) {
                    if (LOG_LIFECYCLE.isWarnEnabled()) {
                        LOG_LIFECYCLE.warn("Retained bean [{}] is destroyed instead of adopted because the bean [{}] it holds cannot be adopted", registration.bean, rejectedDependency.bean);
                    }
                    adoptable.remove(registration);
                    // destroyed before the dependency it holds, as a shutdown would order them
                    rejected.add(0, registration);
                    changed = true;
                }
            }
        }
        Set<Object> adopting = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BeanRegistration<?> registration : adoptable.keySet()) {
            adopting.add(originalOf(registration).bean);
            adopting.add(registration.bean);
        }
        adoptingRetainedBeans = adopting;
        try {
            for (Map.Entry<BeanRegistration<?>, BeanDefinition<?>> entry : adoptable.entrySet()) {
                adopt(entry.getKey(), entry.getValue());
            }
        } finally {
            adoptingRetainedBeans = Set.of();
        }
        for (BeanRegistration<?> registration : rejected) {
            rejectedRetainedRegistrations.add(originalOf(registration));
        }
    }

    private static <T> BeanRegistration<T> originalOf(BeanRegistration<T> registration) {
        return registration instanceof RetainedRegistration<T> retained ? retained.original : registration;
    }

    /**
     * The retained registration of a singleton the given bean holds that cannot be adopted, if any.
     */
    @Nullable
    private BeanRegistration<?> rejectedDependencyOf(BeanRegistration<?> registration, List<BeanRegistration<?>> rejected) {
        if (!(registration instanceof RetainedRegistration<?> retained)) {
            return null;
        }
        for (BeanDependencyGraph.BeanDependency edge : retained.dependencies) {
            if (edge.lazy()) {
                continue;
            }
            for (BeanRegistration<?> candidate : rejected) {
                // the carried edges name the definitions of the stopped context, as the registrations do
                if (originalOf(candidate).beanDefinition == edge.dependency()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * The definition this context adopts the retained bean under, or null when it cannot: there is no
     * definition for it here, or this context already holds another instance under it.
     */
    @Nullable
    private <T> BeanDefinition<T> resolveAdoption(BeanRegistration<T> registration) {
        BeanRegistration<T> original = originalOf(registration);
        BeanDefinition<T> definition = resolveAdoptedDefinition(original.getBeanDefinition());
        if (definition == null) {
            if (LOG_LIFECYCLE.isWarnEnabled()) {
                LOG_LIFECYCLE.warn("Retained bean [{}] has no definition in this context and is destroyed instead of adopted", original.bean);
            }
            return null;
        }
        BeanRegistration<T> existing = singletonScope.findBeanRegistration(definition);
        if (existing == null) {
            // an instance supplied to this context's builder is registered under a runtime definition of its own
            existing = singletonScope.findBeanRegistration(definition, definition.asArgument(), definition.getDeclaredQualifier());
        }
        if (existing != null && existing.bean != original.bean) {
            // this context already holds a bean under the definition, supplied to its builder: what was chosen
            // for this context wins over what the previous one had, and the retained bean is destroyed
            if (LOG_LIFECYCLE.isWarnEnabled()) {
                LOG_LIFECYCLE.warn("Retained bean [{}] is displaced by the bean [{}] this context already holds and is destroyed", original.bean, existing.bean);
            }
            return null;
        }
        return definition;
    }

    /**
     * Destroys the retained beans this context could not adopt, once the bean event listeners exist so
     * that their destruction is announced like any other.
     */
    private void destroyRejectedRetainedRegistrations() {
        List<BeanRegistration<?>> rejected = List.copyOf(rejectedRetainedRegistrations);
        rejectedRetainedRegistrations.clear();
        Set<Object> adopted = adoptedRetainedBeans;
        adoptedRetainedBeans = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> destroyed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BeanRegistration<?> registration : rejected) {
            // an instance adopted under another of its registrations lives on; one rejected under several is destroyed once
            if (adopted.contains(registration.bean) || !destroyed.add(registration.bean)) {
                continue;
            }
            destroyBean(registration);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void adopt(BeanRegistration<T> registration, BeanDefinition<?> resolved) {
        BeanRegistration<T> original = originalOf(registration);
        BeanDefinition<T> definition = (BeanDefinition<T>) resolved;
        // the adopted registration keeps owning what the original owned: the prototypes it received and its
        // interceptor registrations are destroyed with it, as they would have been. They are bound to this
        // context, so the stopped one is not kept reachable through them
        List<BeanRegistration<?>> dependents = null;
        List<?> interceptorRegistrations = null;
        if (original instanceof BeanDisposingRegistration<T> disposing) {
            List<BeanRegistration<?>> owned = disposing.dependentBeans();
            dependents = rebindAll(owned);
            interceptorRegistrations = rebindInterceptors(disposing.getInterceptorCandidates().legacyRegistrations(), owned, dependents);
        }
        // a retained registration carries the instance as the listeners received it when they replaced it: that one is
        // registered, and this context's listeners run on it once they are initialized, whether or not the previous
        // context's listeners replaced it
        BeanRegistration<T> adopted = BeanRegistration.of(this, original.getIdentifier(), definition, registration.getBean(), dependents, interceptorRegistrations);
        singletonScope.registerSingletonBean(adopted, definition.getDeclaredQualifier());
        adoptedToWrap.add(adopted);
        // the prototypes the instance owns are one set however many registrations it is adopted under
        boolean firstRegistration = adoptedRetainedBeans.add(original.bean);
        if (dependencyGraph != null && registration instanceof RetainedRegistration<T> retained) {
            for (BeanDependencyGraph.BeanDependency edge : retained.dependencies) {
                if (!firstRegistration && edge.dependent() != original.getBeanDefinition()) {
                    // the edges of the prototypes it owns, already counted once per prototype instance
                    continue;
                }
                // an edge of the bean itself, or of a prototype it owns: both ends are re-keyed to this context
                BeanDefinition<?> dependent = edge.dependent() == original.getBeanDefinition() ? definition : resolveAdoptedDefinition(edge.dependent());
                BeanDefinition<?> dependency = resolveAdoptedDefinition(edge.dependency());
                if (dependent != null && dependency != null) {
                    dependencyGraph.record(new BeanDependencyGraph.BeanDependency(dependent, dependency, edge.kind(), edge.lazy(), edge.collection()));
                }
            }
        }
        if (LOG_LIFECYCLE.isDebugEnabled()) {
            LOG_LIFECYCLE.debug("Adopted retained bean [{}] with identifier [{}]", original.bean, original.identifier);
        }
    }

    /**
     * Applies this context's bean created listeners to every adopted instance, as its creation would have: a listener
     * the application changed since the previous context applies to a retained bean too, and a wrapper bound to the
     * stopped context, such as one holding its bean locator, is made again bound to this one, around the same retained
     * instance. The listeners run on the instance they received before, never on the previous context's replacement,
     * whose state is not carried over: a listener that configures rather than replaces what it receives sees the
     * retained instance again, so it must be idempotent. Between the adoption and this, while the context is configured,
     * the instance is registered as the listeners received it, as a bean created then would be, since the listeners
     * only apply once they are initialized.
     */
    private void wrapAdoptedRegistrations() {
        if (adoptedToWrap.isEmpty()) {
            return;
        }
        List<BeanRegistration<?>> adopted = adoptedToWrap;
        adoptedToWrap = new ArrayList<>();
        boolean replaced = false;
        for (BeanRegistration<?> registration : adopted) {
            replaced |= wrapAdopted(registration);
        }
        if (replaced) {
            // a collection of beans resolved since the adoption holds the instances as they were adopted
            singletonBeanRegistrations.clear();
        }
    }

    /**
     * @return Whether the adopted registration was replaced by one of what the listeners returned
     */
    private <T> boolean wrapAdopted(BeanRegistration<T> adopted) {
        BeanDefinition<T> definition = adopted.beanDefinition;
        if (singletonScope.findBeanRegistration(definition) != adopted) {
            // destroyed or replaced since it was adopted, by what configured this context
            return false;
        }
        T instance = adopted.bean;
        T wrapped;
        List<BeanRegistration<?>> created;
        try (BeanResolutionContext context = newResolutionContext(definition, null)) {
            try {
                // as the creation of the bean applies them: the listeners, then the validation of what they returned
                wrapped = triggerBeanCreatedEventListener(context, definition, instance, definition.asArgument(), definition.getDeclaredQualifier());
                if (definition instanceof ValidatedBeanDefinition<T> validatedBeanDefinition) {
                    wrapped = validatedBeanDefinition.validate(context, wrapped);
                }
            } catch (RuntimeException | Error e) {
                // what the listeners created for it is owned by nothing
                destroyDependentsOfFailedBean(context, e);
                throw e;
            }
            created = context.getAndResetDependentBeans();
        }
        if (wrapped == instance && created.isEmpty()) {
            return false;
        }
        List<BeanRegistration<?>> dependents = new ArrayList<>(adopted.dependentBeans());
        dependents.addAll(created);
        List<?> interceptorRegistrations = adopted instanceof BeanDisposingRegistration<T> disposing
            ? disposing.getInterceptorCandidates().legacyRegistrations() : null;
        BeanRegistration<T> registration = BeanRegistration.of(this, adopted.identifier, definition, wrapped, dependents, interceptorRegistrations);
        if (wrapped != instance && registration instanceof BeanDisposingRegistration<T> disposing) {
            // retained again as the listeners received it on the next restart
            disposing.setBeforeListeners(instance);
        }
        singletonScope.registerSingletonBean(registration, definition.getDeclaredQualifier());
        if (LOG_LIFECYCLE.isDebugEnabled()) {
            LOG_LIFECYCLE.debug("Wrapped adopted bean [{}] again as [{}]", instance, wrapped);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> RetainedRegistration<T> retainedRegistration(BeanRegistration<T> registration,
                                                                    List<BeanDependencyGraph.BeanDependency> dependencies,
                                                                    AtomicBoolean destroyed) {
        T beforeListeners = registration instanceof BeanDisposingRegistration<T> disposing ? (T) disposing.getBeforeListeners() : null;
        return new RetainedRegistration<>(registration, dependencies, destroyed, beforeListeners);
    }

    /**
     * This context's definition for a definition of the context the bean was retained from.
     *
     * @param retained The retained definition
     * @param <T> The bean type
     * @return The definition to register the bean under, or null when this context has none for it
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private <T> BeanDefinition<T> resolveAdoptedDefinition(BeanDefinition<T> retained) {
        if (retained instanceof RuntimeBeanDefinition<T> runtime) {
            // a registered instance: nothing generated to look up. The definition is registered here so that
            // the instance is found through every type it exposes, not only its own class
            for (BeanDefinition<T> candidate : getBeanDefinitions(runtime.asArgument(), runtime.getDeclaredQualifier())) {
                if (candidate == runtime) {
                    return retained;
                }
                if (candidate instanceof RuntimeBeanDefinition<T>
                    && candidate.getBeanType() == runtime.getBeanType()
                    && Objects.equals(candidate.getDeclaredQualifier(), runtime.getDeclaredQualifier())) {
                    // this context registered an instance of its own for the type: the bean is adopted under that
                    // definition, and the collision check decides between the two instances
                    return candidate;
                }
            }
            registerBeanDefinition(runtime);
            return retained;
        }
        if (retained instanceof BeanDefinitionDelegate<T> delegate) {
            // an @EachProperty entry or an @EachBean member: this context makes its own delegate from its
            // configuration and beans, and the retained bean is registered under that one. No delegate here
            // means the entry was removed or the origin is gone, and the bean is destroyed like any other
            BeanDefinition<T> found = findBeanDefinition(delegate.asArgument(), delegate.getDeclaredQualifier())
                .filter(definition -> definition.isEnabled(this))
                .orElse(null);
            return found != null ? found : resolveNestedEntryDefinition(delegate);
        }
        BeanDefinition<T> definition = findBeanDefinitionByDefinitionClass((Class<? extends BeanDefinition<T>>) retained.getClass()).orElse(null);
        return definition != null && definition.isEnabled(this) ? definition : null;
    }

    /**
     * This context's definition for an {@code @EachProperty} entry nested in the entry of another configuration, which
     * a lookup finds only within the configuration path of its parent: no lookup has walked that path yet while the
     * retained beans are adopted. The entry's delegate is made again from the path it was created under, over this
     * context's definition, as the walk would make it, as long as this context's configuration still has the entry. It
     * equals the delegate the walk makes later, so lookups find the adopted instance under it.
     */
    @Nullable
    private <T> BeanDefinition<T> resolveNestedEntryDefinition(BeanDefinitionDelegate<T> delegate) {
        ConfigurationPath path = delegate.getConfigurationPath().orElse(null);
        Qualifier<T> qualifier = delegate.getDeclaredQualifier();
        // only an entry of configuration nested in another's: an @EachBean member, or a top-level entry, is found by the
        // lookup when its origin or its entry is still there, and is gone otherwise
        if (path == null || qualifier == null || !path.hasDynamicSegments() || delegate.getTarget() instanceof BeanDefinitionDelegate<T>
            || !delegate.getTarget().hasStereotype(ConfigurationReader.class)
            || !(this instanceof PropertyResolver resolver) || !resolver.containsProperties(path.prefix())) {
            return null;
        }
        BeanDefinition<T> target = resolveAdoptedDefinition(delegate.getTarget());
        if (target == null) {
            return null;
        }
        BeanDefinitionDelegate<T> again = BeanDefinitionDelegate.create(target, qualifier, path.copy());
        return again.isEnabled(this) ? again : null;
    }

    @Nullable
    private List<BeanRegistration<?>> rebindAll(@Nullable List<BeanRegistration<?>> registrations) {
        if (registrations == null) {
            return null;
        }
        List<BeanRegistration<?>> rebound = new ArrayList<>(registrations.size());
        for (BeanRegistration<?> registration : registrations) {
            rebound.add(rebind(registration));
        }
        return rebound;
    }

    /**
     * Rebinds the interceptor candidates a retained bean kept. A candidate the bean also owns is the registration
     * its owned dependents were rebound to, so the adopted owner sees one registration for it, as the original did.
     */
    @Nullable
    private List<?> rebindInterceptors(@Nullable List<BeanRegistration<?>> registrations,
                                       List<BeanRegistration<?>> owned,
                                       @Nullable List<BeanRegistration<?>> reboundOwned) {
        if (registrations == null) {
            return null;
        }
        List<Object> rebound = new ArrayList<>(registrations.size());
        for (BeanRegistration<?> registration : registrations) {
            int index = indexOfIdentity(owned, registration);
            rebound.add(index >= 0 && reboundOwned != null ? reboundOwned.get(index) : rebind(registration));
        }
        return rebound;
    }

    private static int indexOfIdentity(List<BeanRegistration<?>> registrations, BeanRegistration<?> registration) {
        for (int i = 0; i < registrations.size(); i++) {
            if (registrations.get(i) == registration) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A registration a retained bean owns (a prototype it received, an interceptor of its own), bound to
     * this context instead of the stopped one, under this context's definition where it has one.
     */
    @SuppressWarnings("unchecked")
    private <T> BeanRegistration<T> rebind(BeanRegistration<T> registration) {
        if (!(registration instanceof BeanDisposingRegistration<T> disposing)) {
            return registration;
        }
        if (disposing.getBean() instanceof DefaultBeanDependencyResolver && disposing.getBeanDefinition() != dependencyResolverDefinition) {
            // a resolver the bean received, or a group it opened: bound to the stopped context, and through its owner to
            // that context's graph. What it resolved stays owned, under a resolver of this context; the bean only used
            // the resolver while it was created, and a bean that kept it would hold the stopped context anyway
            List<BeanRegistration<?>> owned = rebindAll(disposing.dependentBeans());
            DefaultBeanDependencyResolver resolver = new DefaultBeanDependencyResolver(this);
            BeanRegistration<T> rebound = (BeanRegistration<T>) BeanRegistration.of(this, disposing.getIdentifier(), dependencyResolverDefinition,
                resolver, owned, null);
            // the singletons it resolved that this context adopts too, which the bean's destruction is ordered before, as
            // registrations of this context; one the stopped context destroyed is not held, it would keep that context's
            // generation reachable
            List<BeanRegistration<?>> required = new ArrayList<>();
            for (BeanRegistration<?> shared : disposing.getDependencies().requiredBeans()) {
                BeanRegistration<?> here = adoptingRetainedBeans.contains(shared.getBean()) ? sharedRegistration(shared) : null;
                if (here != null) {
                    required.add(here);
                }
            }
            resolver.dependencies.requireAll(required);
            return rebound;
        }
        BeanDefinition<T> definition = resolveAdoptedDefinition(disposing.getBeanDefinition());
        List<BeanRegistration<?>> owned = disposing.dependentBeans();
        List<BeanRegistration<?>> dependents = rebindAll(owned);
        return BeanRegistration.of(
            this,
            disposing.getIdentifier(),
            definition != null ? definition : disposing.getBeanDefinition(),
            disposing.getBean(),
            dependents,
            rebindInterceptors(disposing.getInterceptorCandidates().legacyRegistrations(), owned, dependents)
        );
    }

    /**
     * A registration of this context for a shared instance a rebound resolver orders its destruction by: the instance
     * under this context's definition, as it is adopted, without what the stopped context's registration owned. The
     * stopped context's definition is not held: it was configured with that context's environment.
     */
    @Nullable
    private <S> BeanRegistration<S> sharedRegistration(BeanRegistration<S> shared) {
        BeanDefinition<S> definition = resolveAdoptedDefinition(shared.getBeanDefinition());
        return definition == null ? null : BeanRegistration.of(this, shared.getIdentifier(), definition, shared.getBean());
    }

    /**
     * Collects what a retained bean received, and what the prototypes it owns received, since those
     * travel with it and may hold a singleton the bean therefore holds too.
     */
    private void collectDependencies(BeanRegistration<?> registration, List<BeanDependencyGraph.BeanDependency> into, Set<Object> visited) {
        if (dependencyGraph == null || !visited.add(registration)) {
            return;
        }
        into.addAll(dependencyGraph.dependenciesOf(registration.beanDefinition));
        if (registration instanceof BeanDisposingRegistration<?> disposing && !disposing.dependentBeans().isEmpty()) {
            for (BeanRegistration<?> dependent : disposing.dependentBeans()) {
                collectDependencies(dependent, into, visited);
            }
        }
    }

    /**
     * What {@link #stopRetaining(RetentionCriteria)} asks about the singletons of the context it stops.
     *
     * @since 5.3.0
     */
    @Internal
    @Experimental
    public interface RetentionCriteria {

        /**
         * Whether a singleton survives the restart, with what it holds.
         *
         * @param registration The singleton's registration
         * @return True to retain it
         */
        boolean retain(BeanRegistration<?> registration);

        /**
         * The configuration prefixes a change under which releases a singleton this retains: a configuration bean
         * under one of them that the singleton holds is not retained with it, nor does it keep it from being retained.
         *
         * @param registration The retained singleton's registration
         * @return The prefixes, empty when no configuration change releases it
         */
        Set<String> invalidatedBy(BeanRegistration<?> registration);

        /**
         * Whether the restart replaces a class, so that a retained bean bound to it would keep the stopped generation
         * of the application reachable and run its old code: a bean whose closure has a definition, an instance or a
         * prototype it owns of such a class is not retained.
         *
         * @param type The class of a definition or an instance in the closure of a retained bean
         * @return True when the class is replaced
         */
        default boolean isReplaced(Class<?> type) {
            return false;
        }
    }

    /**
     * A registration {@link #stopRetaining(Predicate)} returns: the original registration, so the
     * adopting context keeps what it owned, and what the bean received, so that context's graph knows.
     *
     * @param <T> The bean type
     */
    private static final class RetainedRegistration<T> extends BeanRegistration<T> implements DependentBeanProvider {
        private final BeanRegistration<T> original;
        private final List<BeanDependencyGraph.BeanDependency> dependencies;
        /**
         * Shared by the registrations of one instance, so a launcher destroying them all destroys it once.
         */
        private final AtomicBoolean destroyed;

        RetainedRegistration(BeanRegistration<T> original, List<BeanDependencyGraph.BeanDependency> dependencies, AtomicBoolean destroyed,
                             @Nullable T beforeListeners) {
            super(original.identifier, original.beanDefinition, beforeListeners != null ? beforeListeners : original.bean);
            this.original = original;
            this.dependencies = dependencies;
            this.destroyed = destroyed;
        }

        @Override
        public List<BeanRegistration<?>> dependentBeans() {
            return original instanceof DependentBeanProvider provider ? provider.dependentBeans() : List.of();
        }
    }

    protected void initializeTypeConverters() {
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<BeanDefinition<T>> findBeanCandidatesInternal(@Nullable BeanResolutionContext resolutionContext, Argument<T> beanType) {
        Collection<BeanDefinition<T>> beanDefinitions = findBeanCandidatesCached(resolutionContext, beanType);
        Argument<T> lookupBeanType = resolveBeanLookupArgument(beanType);
        if (!lookupBeanType.equals(beanType)) {
            Collection<BeanDefinition<T>> lookupBeanDefinitions = findBeanCandidatesCached(resolutionContext, lookupBeanType);
            if (!lookupBeanDefinitions.isEmpty()) {
                Set<BeanDefinition<T>> merged = new LinkedHashSet<>(beanDefinitions);
                merged.addAll(lookupBeanDefinitions);
                return merged;
            }
        }
        return beanDefinitions;
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<BeanDefinition<T>> findBeanCandidatesCached(@Nullable BeanResolutionContext resolutionContext, Argument<T> beanType) {
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
                                                       Argument<T> beanType,
                                                       @Nullable Qualifier<T> qualifier) {
        return java.util.Objects.requireNonNull(resolveBeanRegistration(resolutionContext, beanType, qualifier, true));
    }

    /**
     * Obtains the registrations of the interceptors bound to the bean a resolution context resolves for, as the bean's
     * own: a singleton or an interceptor of a custom scope from its scope, any other the instance created for the bean
     * earlier, found among its dependents, or one created now as a new dependent, marked as created to intercept it.
     *
     * @param resolutionContext The resolution context of the bean
     * @param interceptorType   The interceptor type
     * @param binding           The interceptor binding qualifier
     * @param <I>               The interceptor type
     * @return The registrations, in order
     * @see BeanResolutionContext#getInterceptorRegistrations(Argument, Qualifier)
     */
    <I> Collection<BeanRegistration<I>> getInterceptorRegistrations(AbstractBeanResolutionContext resolutionContext,
                                                                    Argument<I> interceptorType,
                                                                    @Nullable Qualifier<I> binding) {
        assertContextState();
        Collection<BeanDefinition<I>> candidates = findBeanCandidatesInternal(resolutionContext, interceptorType);
        if (!candidates.isEmpty()) {
            candidates = applyBeanResolutionFilters(resolutionContext, interceptorType, candidates);
            if (binding != null) {
                candidates = binding.filterQualified(interceptorType.getType(), candidates);
            }
        }
        boolean owned = false;
        for (BeanDefinition<I> candidate : candidates) {
            if (isUnscoped(candidate)) {
                owned = true;
                break;
            }
        }
        if (!owned) {
            // every interceptor comes from its scope, as any lookup gets it
            return getBeanRegistrations(resolutionContext, interceptorType, binding);
        }
        List<BeanRegistration<I>> registrations = new ArrayList<>(candidates.size());
        // when the creation of one fails, the ones created before it stay the dependents of the resolution context
        for (BeanDefinition<I> candidate : candidates) {
            BeanRegistration<I> existing = isUnscoped(candidate) ? resolutionContext.findInterceptor(candidate) : null;
            if (existing != null) {
                registrations.add(existing);
            } else {
                int created = registrations.size();
                addCandidateToList(resolutionContext, candidate, interceptorType, binding, registrations);
                if (isUnscoped(candidate)) {
                    markCreatedAsInterceptors(registrations.subList(created, registrations.size()));
                }
            }
        }
        registrations.sort(OrderUtil.ORDERED_COMPARATOR);
        return registrations;
    }

    private static <I> void markCreatedAsInterceptors(List<BeanRegistration<I>> created) {
        for (BeanRegistration<I> registration : created) {
            if (registration instanceof BeanDisposingRegistration<I> disposingRegistration) {
                disposingRegistration.markCreatedAsInterceptor();
            }
        }
    }

    /**
     * Destroys beans created for something that does not, or no longer, own them, the last created first.
     *
     * @param created The registrations of the beans
     * @param failure The failure they are destroyed for, which a failure to destroy one is added to as suppressed,
     *                or {@code null} to log runtime exceptions and rethrow errors after attempting all destructions
     */
    @SuppressWarnings("java:S1181") // Attempt every destruction before rethrowing an Error or suppressing it on the original failure.
    void destroyCreatedBeans(@Nullable List<BeanRegistration<?>> created, @Nullable Throwable failure) {
        if (created == null) {
            return;
        }
        Error cleanupError = null;
        for (int i = created.size() - 1; i >= 0; i--) {
            try {
                destroyDependentBean(created.get(i));
            } catch (RuntimeException | Error e) {
                if (failure != null) {
                    if (failure != e) {
                        failure.addSuppressed(e);
                    }
                } else if (e instanceof Error error) {
                    if (cleanupError == null) {
                        cleanupError = error;
                    } else if (cleanupError != error) {
                        cleanupError.addSuppressed(error);
                    }
                } else if (LOG.isErrorEnabled()) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }
        if (cleanupError != null) {
            throw cleanupError;
        }
    }

    /**
     * Whether no scope holds a bean of the given definition: a prototype, one with no scope, or one of a scope
     * nothing implements, which is created for whoever asks for it.
     */
    boolean isUnscoped(BeanDefinition<?> definition) {
        if (definition.isSingleton()) {
            return false;
        }
        String scope = definition.getScopeName().orElse(null);
        return scope == null || Prototype.class.getName().equals(scope) || customScopeRegistry.findDeclaredScope(definition).isEmpty();
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
                                                                    Argument<T> beanType,
                                                                    @Nullable Qualifier<T> qualifier) {
        assertContextState();
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
            beanDefinitions = applyBeanResolutionFilters(resolutionContext, beanType, beanDefinitions);
            if (qualifier != null) {
                beanDefinitions = qualifier.filterQualified(beanType.getType(), beanDefinitions);
            }
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

    private <T> Collection<BeanRegistration<T>> resolveBeanRegistrations(@Nullable BeanResolutionContext resolutionContext,
                                                                         Collection<BeanDefinition<T>> beanDefinitions,
                                                                         Argument<T> beanType,
                                                                         @Nullable Qualifier<T> qualifier) {
        List<BeanRegistration<T>> beansOfTypeList = new ArrayList<>(beanDefinitions.size());
        for (BeanDefinition<T> definition : beanDefinitions) {
            addCandidateToList(resolutionContext, definition, beanType, qualifier, beansOfTypeList);
        }
        beansOfTypeList.sort(OrderUtil.ORDERED_COMPARATOR);
        return beansOfTypeList;
    }

    private <T> void logResolvedExistingBeanRegistrations(Argument<T> beanType, @Nullable Qualifier<T> qualifier, Collection<BeanRegistration<T>> existing) {
        if (LOG.isDebugEnabled()) {
            if (qualifier == null) {
                LOG.debug("Found {} existing beans for type [{}]: {} ", existing.size(), beanType.getName(), existing);
            } else {
                LOG.debug("Found {} existing beans for type [{} {}]: {} ", existing.size(), qualifier, beanType.getName(), existing);
            }
        }
    }

    private <T> Collection<BeanDefinition<T>> applyBeanResolutionFilters(@Nullable BeanResolutionContext resolutionContext,
                                                                         Argument<T> beanType,
                                                                         Collection<BeanDefinition<T>> candidates) {
        Argument<T> lookupBeanType = resolveBeanLookupArgument(beanType);
        BeanResolutionContext.Segment<?, ?> segment = resolutionContext != null ? resolutionContext.getPath().peek() : null;
        BeanDefinition<?> declaringBean = null;
        Class<?> proxyTargetDefinitionType = null;
        if (segment instanceof AbstractBeanResolutionContext.ConstructorSegment || segment instanceof AbstractBeanResolutionContext.MethodSegment) {
            declaringBean = segment.getDeclaringType();
            // if the currently injected segment is a constructor argument and the type to be constructed is the
            // same as the candidate, then filter out the candidate to avoid a circular injection problem
            if (declaringBean instanceof ProxyBeanDefinition<?> proxyBeanDefinition) {
                proxyTargetDefinitionType = proxyBeanDefinition.getTargetDefinitionType();
            }
        }
        candidates = new LinkedHashSet<>(candidates); // Make mutable
        for (Iterator<BeanDefinition<T>> iterator = candidates.iterator(); iterator.hasNext(); ) {
            BeanDefinition<T> c = iterator.next();
            if (c.isAbstract() || declaringBean != null && c.equals(declaringBean) || proxyTargetDefinitionType != null && proxyTargetDefinitionType.equals(c.getClass())) {
                iterator.remove();
            } else if (!isInjectableCandidate(beanType, c) && (lookupBeanType.equals(beanType) || !isInjectableCandidate(lookupBeanType, c))) {
                iterator.remove();
            }
        }
        return candidates;
    }

    /**
     * Whether the candidate can be provided as the requested type. A bean that is only enumerable by the
     * requested type because it is {@link io.micronaut.core.annotation.Indexed} by it, without implementing it,
     * can be listed via {@link #getBeanDefinitions(Argument)} but cannot be instantiated or injected as that type.
     *
     * @param beanType  The requested bean type
     * @param candidate The candidate
     * @param <T>       The bean type
     * @return True if the candidate is a candidate for the requested type
     */
    private <T> boolean isInjectableCandidate(Argument<T> beanType, BeanDefinition<T> candidate) {
        return beanType.getType() == Object.class || beanResolutionCustomizer.isCandidateBean(beanType, candidate);
    }

    /**
     * Whether exactly one injectable bean candidate exists for the requested type and qualifier.
     *
     * @param beanType  The requested bean type
     * @param qualifier The requested qualifier
     * @param <T>       The bean type
     * @return True if exactly one injectable candidate exists
     */
    @Internal
    public <T> boolean isUniqueBeanCandidate(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(null, beanType);
        candidates = applyBeanResolutionFilters(null, beanType, candidates);
        if (qualifier != null && !candidates.isEmpty()) {
            candidates = qualifier.filterQualified(beanType.getType(), candidates);
        }
        return candidates.size() == 1;
    }

    private <T> void addCandidateToList(@Nullable BeanResolutionContext resolutionContext,
                                        BeanDefinition<T> candidate,
                                        Argument<T> beanType,
                                        @Nullable Qualifier<T> qualifier,
                                        Collection<BeanRegistration<T>> beansOfTypeList) {
        BeanRegistration<T> beanRegistration = null;
        try {
            beanRegistration = resolveBeanRegistration(
                resolutionContext,
                candidate,
                candidate.asArgument(),
                candidate.getDeclaredQualifier()
            );
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
                Iterable<?> iterable = asIterable(beanRegistration.bean);
                if (iterable != null) {
                    int i = 0;
                    for (Object o : iterable) {
                        if (o == null || !beanType.isInstance(o)) {
                            continue;
                        }
                        BeanKey<T> elementKey = new BeanKey<>(beanType, (qualifier == null ? Qualifiers.byName(String.valueOf(i++)) : Qualifiers.byQualifiers(Qualifiers.byName(String.valueOf(i++)), qualifier)));
                        // An element of a container bean shares the container's definition: destroying it
                        // through the context would run destruction with a mismatched definition and purge
                        // the container's singleton registration, so close() stays a no-op unless the
                        // element carries its own lifecycle or disposal semantics
                        beansOfTypeList.add(candidate instanceof DisposableBeanDefinition || o instanceof LifeCycle ?
                            BeanRegistration.of(this, elementKey, candidate, (T) o) :
                            new BeanRegistration<>(elementKey, candidate, (T) o));
                    }
                }
            } else {
                beansOfTypeList.add(beanRegistration);
            }
        }
    }

    @Nullable
    private Iterable<?> asIterable(@Nullable Object container) {
        if (container == null) {
            return null;
        }
        if (container instanceof Object[] array) {
            return Arrays.asList(array);
        }
        if (container instanceof Iterable<?> iterable) {
            return iterable;
        }
        // a language integration may model a container that is not a java.lang.Iterable
        return getConversionService().convert(container, Iterable.class).orElse(null);
    }

    private <T> boolean isCandidatePresent(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        final Collection<BeanDefinition<T>> candidates = findBeanCandidates(null, beanType, true, candidate -> isInjectableCandidate(beanType, candidate));
        if (!candidates.isEmpty()) {
            filterReplacedBeans(candidates);
            if (qualifier != null) {
                return qualifier.doesQualifyQualified(beanType.getType(), candidates);
            }
            return true;
        }
        return false;
    }

    /**
     * Destroys the given singleton registrations, skipping any bean already present in {@code processed}.
     *
     * @param registrations The registrations to destroy
     * @param processed     The identity set of already destroyed beans, added to as beans are destroyed
     */
    private void destroySingletons(Collection<BeanRegistration> registrations, Set<Object> processed) {
        // need to sort registered singletons so that beans with that require other beans appear first
        List<BeanRegistration> objects = (List) BeanDestructionOrder.sort((Collection) registrations);

        Set<Object> retainedBeans = retainedBeans(objects);
        for (BeanRegistration beanRegistration : objects) {
            Object bean = beanRegistration.bean;
            if (!processed.add(bean)) {
                continue;
            }
            if (retainedBeans.contains(bean)) {
                // kept alive for the context that comes next: no @PreDestroy, no destruction event
                continue;
            }

            if (LOG_LIFECYCLE.isDebugEnabled()) {
                LOG_LIFECYCLE.debug("Destroying bean [{}] with identifier [{}]", bean, beanRegistration.identifier);
            }

            try {
                destroyBean(beanRegistration);
            } catch (BeanDestructionException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * The instances a {@link #stopRetaining(RetentionCriteria)} in progress keeps, and their registrations. An
     * instance registered under several definitions is kept when any of its registrations is accepted,
     * and every registration of it is kept, so the instance stays reachable under each of them.
     *
     * @param registrations The singleton registrations about to be destroyed
     * @return The retained instances, by identity; empty when no retention is in progress
     */
    private Set<Object> retainedBeans(List<BeanRegistration> registrations) {
        RetentionCriteria criteria = retentionCriteria;
        if (criteria == null) {
            return Set.of();
        }
        Map<Object, AtomicBoolean> beans = new IdentityHashMap<>();
        for (BeanRegistration<?> registration : registrations) {
            if (registration.bean == null || !criteria.retain(registration)) {
                continue;
            }
            // a retained bean keeps the singletons it holds, so they are retained with it; one of them bound to
            // this context (a provider, a proxy, the context itself) keeps the whole closure from being retained,
            // except configuration a change of which releases the bean: it is not retained, but made again by the next context
            List<BeanRegistration<?>> closure = new ArrayList<>();
            Set<String> invalidatedBy = criteria.invalidatedBy(registration);
            BeanRegistration<?> bound = collectRetentionClosure(registration, invalidatedBy, closure, Collections.newSetFromMap(new IdentityHashMap<>()));
            if (bound != null) {
                if (LOG_LIFECYCLE.isWarnEnabled()) {
                    LOG_LIFECYCLE.warn("Bean [{}] is not retained across the restart: {} holds a provider, a proxy or the context, which are bound to this context",
                        registration.bean, bound == registration ? "it" : "the bean [" + bound.bean + "] it holds");
                }
                continue;
            }
            // what it received is what it runs: a class the restart replaces, anywhere in the closure, would keep the old
            // generation of the application running in it
            Set<Object> examined = Collections.newSetFromMap(new IdentityHashMap<>());
            BeanRegistration<?> replacing = null;
            Class<?> replaced = null;
            for (BeanRegistration<?> member : closure) {
                replaced = replacedClassOf(member, criteria, invalidatedBy, examined);
                if (replaced != null) {
                    replacing = member;
                    break;
                }
            }
            if (replacing != null && replaced != null) {
                if (LOG_LIFECYCLE.isWarnEnabled()) {
                    LOG_LIFECYCLE.warn("Bean [{}] is not retained across the restart: {} bound to the class [{}], which the restart replaces",
                        registration.bean, replacing == registration ? "it is" : "the bean [" + replacing.bean + "] it holds is", replaced.getName());
                }
                continue;
            }
            for (BeanRegistration<?> member : closure) {
                beans.putIfAbsent(member.bean, new AtomicBoolean());
            }
        }
        for (BeanRegistration<?> registration : registrations) {
            AtomicBoolean destroyed = registration.bean == null ? null : beans.get(registration.bean);
            if (destroyed != null) {
                // what the bean received travels with it, so the next context's graph knows what it holds
                List<BeanDependencyGraph.BeanDependency> dependencies = new ArrayList<>();
                if (dependencyGraph != null) {
                    collectDependencies(registration, dependencies, Collections.newSetFromMap(new IdentityHashMap<>()));
                }
                retainedOnStop.add(retainedRegistration(registration, List.copyOf(dependencies), destroyed));
                if (LOG_LIFECYCLE.isDebugEnabled()) {
                    LOG_LIFECYCLE.debug("Retaining bean [{}] with identifier [{}] across the restart", registration.bean, registration.identifier);
                }
            }
        }
        return beans.keySet();
    }

    /**
     * Whether a bean received something that resolves through this context on every use, a
     * {@link BeanProvider} or a lazy proxy, or is such a proxy itself. Adopted by another context it
     * would keep resolving through this stopped one, so it is not retained. Known only when the
     * dependency graph is recorded; without it the caller answers for what it retains.
     */
    /**
     * Collects a bean and the singletons it holds, directly or through the singletons they hold, into
     * the closure that is retained with it. Known only when the dependency graph is recorded; without it
     * the closure is the bean alone and the caller answers for what it holds.
     *
     * @param invalidatedBy The prefixes a change under which releases the retained bean: a configuration bean under one
     * of them is left out of the closure rather than examined
     * @return The first member bound to this context, which keeps the closure from being retained, or null
     */
    @Nullable
    private BeanRegistration<?> collectRetentionClosure(BeanRegistration<?> registration, Set<String> invalidatedBy,
                                                       List<BeanRegistration<?>> closure, Set<Object> visited) {
        if (!visited.add(registration)) {
            return null;
        }
        if (!closure.isEmpty() && isCoveredConfiguration(registration.beanDefinition, invalidatedBy)) {
            // the next context binds it again; the retained bean holds only what it copied, valid until a change releases it
            return null;
        }
        if (holdsContextBoundState(registration, invalidatedBy)) {
            return registration;
        }
        closure.add(registration);
        if (dependencyGraph == null) {
            return null;
        }
        return collectRetentionClosure(registration.beanDefinition, invalidatedBy, closure, visited);
    }

    /**
     * Follows what a definition's beans received: a singleton joins the closure, a prototype travels with
     * its owner and contributes what it received in turn.
     */
    @Nullable
    private BeanRegistration<?> collectRetentionClosure(BeanDefinition<?> definition, Set<String> invalidatedBy,
                                                       List<BeanRegistration<?>> closure, Set<Object> visited) {
        if (dependencyGraph == null) {
            return null;
        }
        for (BeanDependencyGraph.BeanDependency edge : dependencyGraph.dependenciesOf(definition)) {
            if (edge.lazy()) {
                continue;
            }
            BeanRegistration<?> bound;
            if (edge.dependency().isSingleton()) {
                BeanRegistration<?> dependency = singletonScope.findBeanRegistration(edge.dependency());
                bound = dependency == null ? null : collectRetentionClosure(dependency, invalidatedBy, closure, visited);
            } else if (isCoveredConfiguration(edge.dependency(), invalidatedBy)) {
                bound = null;
            } else if (visited.add(edge.dependency())) {
                bound = collectRetentionClosure(edge.dependency(), invalidatedBy, closure, visited);
            } else {
                bound = null;
            }
            if (bound != null) {
                return bound;
            }
        }
        return null;
    }

    /**
     * The first class the restart replaces that a member of a retained bean's closure is bound to: its definition, its
     * instance, the instance the bean created listeners received, and the prototypes it owns or received.
     */
    @Nullable
    private Class<?> replacedClassOf(BeanRegistration<?> registration, RetentionCriteria criteria, Set<String> invalidatedBy, Set<Object> examined) {
        if (!examined.add(registration)) {
            return null;
        }
        Class<?> replaced = replacedClassOf(registration.beanDefinition, criteria, invalidatedBy, examined);
        if (replaced != null) {
            return replaced;
        }
        Object beforeListeners = registration instanceof BeanDisposingRegistration<?> disposing ? disposing.getBeforeListeners() : null;
        for (Object instance : new Object[] {registration.bean, beforeListeners}) {
            if (instance != null && criteria.isReplaced(instance.getClass())) {
                return instance.getClass();
            }
        }
        if (registration instanceof BeanDisposingRegistration<?> disposing) {
            for (BeanRegistration<?> dependent : disposing.dependentBeans()) {
                if (isCoveredConfiguration(dependent.beanDefinition, invalidatedBy)) {
                    continue;
                }
                replaced = replacedClassOf(dependent, criteria, invalidatedBy, examined);
                if (replaced != null) {
                    return replaced;
                }
            }
        }
        return null;
    }

    @Nullable
    private Class<?> replacedClassOf(BeanDefinition<?> definition, RetentionCriteria criteria, Set<String> invalidatedBy, Set<Object> examined) {
        if (!examined.add(definition)) {
            return null;
        }
        BeanDefinition<?> generated = definition;
        while (generated instanceof DelegatingBeanDefinition<?> delegating) {
            generated = delegating.getTarget();
        }
        if (criteria.isReplaced(generated.getClass())) {
            return generated.getClass();
        }
        if (criteria.isReplaced(definition.getBeanType())) {
            return definition.getBeanType();
        }
        if (dependencyGraph != null) {
            for (BeanDependencyGraph.BeanDependency edge : dependencyGraph.dependenciesOf(definition)) {
                // a singleton it received is a member of the closure, examined as such
                if (edge.lazy() || edge.dependency().isSingleton() || isCoveredConfiguration(edge.dependency(), invalidatedBy)) {
                    continue;
                }
                Class<?> replaced = replacedClassOf(edge.dependency(), criteria, invalidatedBy, examined);
                if (replaced != null) {
                    return replaced;
                }
            }
        }
        return null;
    }

    private boolean holdsContextBoundState(BeanRegistration<?> registration, Set<String> invalidatedBy) {
        return holdsContextBoundState(registration, invalidatedBy, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * Whether a definition is of configuration, {@link io.micronaut.context.annotation.ConfigurationProperties},
     * {@link io.micronaut.context.annotation.EachProperty} or another {@link ConfigurationReader}, whose prefix is one
     * of the given prefixes or under one: a change of it is a change under that prefix. The prefix of an
     * {@code @EachProperty} entry is the entry's own.
     */
    private static boolean isCoveredConfiguration(BeanDefinition<?> definition, Set<String> invalidatedBy) {
        if (invalidatedBy.isEmpty() || !definition.hasStereotype(ConfigurationReader.class)) {
            return false;
        }
        String prefix = definition instanceof BeanDefinitionDelegate<?> delegate
            ? delegate.getConfigurationPath().map(ConfigurationPath::prefix).orElse(null) : null;
        if (prefix == null) {
            prefix = definition.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse(null);
        }
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }
        for (String covering : invalidatedBy) {
            if (prefix.equals(covering) || prefix.length() > covering.length() && prefix.startsWith(covering)
                && (prefix.charAt(covering.length()) == '.' || prefix.charAt(covering.length()) == '[')) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a type is one the context owns an instance of per context: the context itself and what it
     * registers or hands out for itself, which is stopped or discarded with it.
     */
    private static boolean isContextOwnedType(Class<?> type) {
        return BeanLocator.class.isAssignableFrom(type)
            || PropertyResolver.class.isAssignableFrom(type)
            || ApplicationEventPublisher.class.isAssignableFrom(type)
            || ConversionService.class.isAssignableFrom(type)
            || ResourceLoader.class.isAssignableFrom(type)
            || BeanResolutionContext.class.isAssignableFrom(type);
    }

    private boolean isContextOwned(@Nullable Object bean) {
        return bean == this
            || bean instanceof PropertyResolver
            || bean instanceof ApplicationEventPublisher
            || bean instanceof ConversionService
            || bean instanceof ResourceLoader
            || bean instanceof BeanResolutionContext;
    }

    private boolean holdsContextBoundState(BeanRegistration<?> registration, Set<String> invalidatedBy, Set<Object> visited) {
        if (!visited.add(registration)) {
            return false;
        }
        if (registration.beanDefinition.isProxy() || isContextOwned(registration.bean)
            || registration instanceof BeanDisposingRegistration<?> disposing && isContextOwned(disposing.getBeforeListeners())) {
            return true;
        }
        // the context and what it owns (its environment, its event publishers, its conversion service, its
        // resource loader) are handed out without a recorded dependency: a bean holding one of them holds
        // the stopped context's
        for (Class<?> required : registration.beanDefinition.getRequiredComponents()) {
            if (required.isInstance(this) || isContextOwnedType(required)) {
                return true;
            }
        }
        if (dependencyGraph != null) {
            for (BeanDependencyGraph.BeanDependency dependency : dependencyGraph.dependenciesOf(registration.beanDefinition)) {
                if (isCoveredConfiguration(dependency.dependency(), invalidatedBy)) {
                    continue;
                }
                if (dependency.lazy() || dependency.dependency().isProxy() || dependency.dependency() instanceof AbstractProviderDefinition) {
                    return true;
                }
            }
        }
        // what an owned prototype holds, the bean holds through it
        if (registration instanceof BeanDisposingRegistration<?> disposing && !disposing.dependentBeans().isEmpty()) {
            for (BeanRegistration<?> dependent : disposing.dependentBeans()) {
                if (!isCoveredConfiguration(dependent.beanDefinition, invalidatedBy) && holdsContextBoundState(dependent, invalidatedBy, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public <T> CreatedBean<T> createBeanRegistration(BeanDefinition<T> definition) {
        ArgumentUtils.requireNonNull(ARGUMENT_DEFINITION, definition);
        if (isDependencyCreationClosed()) {
            throw new IllegalStateException("Cannot create a bean before the context is configured or after its shutdown has begun");
        }
        BeanRegistration<T> registration = createFreshRegistration(null, definition);
        trackShutdownDependents(null, List.<BeanRegistration<?>>of(registration));
        return registration;
    }

    @Override
    public BeanDependencyGroup createDependencyGroup() {
        if (isDependencyCreationClosed()) {
            throw new IllegalStateException("Cannot create a dependency group before the context is configured or after its shutdown has begun");
        }
        return new DefaultBeanDependencyResolver(this);
    }

    BeanRegistration<BeanDependencyResolver> newDependencyGroupRegistration(@Nullable DefaultBeanResolutionContext destructionContext) {
        return BeanRegistration.of(this, BeanIdentifier.of(BeanDependencyResolver.class.getName()),
            dependencyResolverDefinition, new DefaultBeanDependencyResolver(this, new DefaultBeanDependencies(destructionContext)));
    }

    boolean isContextConfigured() {
        return configured.get();
    }

    /**
     * Whether lookups through an existing dependency group or resolver are rejected: before the context is configured,
     * after it stopped, and during shutdown on any thread but the one running it. On that thread every lookup is made
     * on behalf of a shutdown event listener or a destruction callback, and what it creates is destroyed before the
     * shutdown completes.
     *
     * @return Whether the lookups are rejected
     */
    boolean isDependencyResolutionClosed() {
        return !configured.get() || terminating.get() && shutdownThread != Thread.currentThread();
    }

    /**
     * Whether new top-level ownership, an independent group or a fresh registration, is rejected. During shutdown only
     * a listener of the {@link ShutdownEvent} may start it; a destruction callback uses the dependencies of its own
     * invocation instead.
     *
     * @return Whether new top-level ownership is rejected
     */
    private boolean isDependencyCreationClosed() {
        return !configured.get() || terminating.get() && shutdownEventThread != Thread.currentThread();
    }

    /**
     * Records the registrations a lookup created during shutdown, so they are destroyed before it completes.
     *
     * @param owner The dependencies that own the registrations, or null when the caller of the context owns them
     * @param created The registrations
     */
    void trackShutdownDependents(@Nullable DefaultBeanDependencies owner, List<BeanRegistration<?>> created) {
        if (!created.isEmpty() && shutdownThread == Thread.currentThread()) {
            for (BeanRegistration<?> registration : created) {
                shutdownDependents.add(new ShutdownDependent(owner, registration));
            }
        }
    }

    /**
     * Destroys what was created during shutdown and is still held, in reverse creation order. A dependent already
     * destroyed with its owner, or released by it, is skipped.
     */
    private void destroyShutdownDependents() {
        while (!shutdownDependents.isEmpty()) {
            List<ShutdownDependent> taken = List.copyOf(shutdownDependents);
            shutdownDependents.clear();
            for (int i = taken.size() - 1; i >= 0; i--) {
                ShutdownDependent dependent = taken.get(i);
                try {
                    if (dependent.owner() == null) {
                        // Destruction is claimed once, so a registration the caller closed is not destroyed again
                        dependent.registration().close();
                    } else if (dependent.owner().remove(dependent.registration())) {
                        destroyDependentBean(dependent.registration());
                    }
                } catch (RuntimeException e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error destroying bean [{}] created during shutdown: {}", dependent.registration().bean, e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Stops resolution through a registration being destroyed and through everything it owns. The destruction callbacks
     * may still resolve through the bean itself, its proxy target and the resolvers and groups injected into them, but
     * not through the other beans they own.
     *
     * @param invocation The destruction invocation whose callbacks may still resolve, or null
     * @param registration The registration
     * @param visited The registrations already stopped
     */
    private void stopDependencyResolution(@Nullable DefaultBeanResolutionContext invocation, BeanRegistration<?> registration,
                                          Set<BeanRegistration<?>> visited) {
        if (!visited.add(registration)) {
            return;
        }
        if (dependencyGraph != null && registration.getBean() instanceof DefaultBeanDependencyResolver resolver) {
            // the owner of the resolver is being destroyed: what it received through the resolver goes, and a lookup
            // racing the destruction records nothing
            resolver.releaseOwner(dependencyGraph);
        }
        DefaultBeanDependencies dependencies = registration.getDependencies();
        if (dependencies != null) {
            dependencies.stopResolving(invocation);
        }
        for (BeanRegistration<?> owned : registration.dependentBeans()) {
            stopDependencyResolution(owned.getBean() instanceof DefaultBeanDependencyResolver ? invocation : null, owned, visited);
        }
        if (registration.getBean() instanceof InterceptedBeanProxy<?> proxy && proxy.hasCachedInterceptedTarget()
            && findProxyTargetBeanDefinition(registration.getBeanDefinition()).map(this::isUnscoped).orElse(false)) {
            BeanRegistration<?> target = proxy.interceptedTargetRegistration();
            if (target != null) {
                stopDependencyResolution(invocation, target, visited);
            }
            if (registration instanceof BeanDisposingRegistration<?> disposing && disposing.getProxyTargetContext() != null) {
                for (BeanRegistration<?> owned : disposing.getProxyTargetContext().getCachedProxyTargetDependents()) {
                    stopDependencyResolution(owned.getBean() instanceof DefaultBeanDependencyResolver ? invocation : null, owned, visited);
                }
            }
        }
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return MutableConvertibleValues.of(attributes);
    }

    @Override
    public Optional<Object> getAttribute(CharSequence name) {
        if (name != null) {
            return Optional.ofNullable(attributes.get(name));
        } else {
            return Optional.empty();
        }
    }

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

    @Override
    public BeanContext setAttribute(CharSequence name, @Nullable Object value) {
        if (name != null) {
            if (value != null) {
                attributes.put(name, value);
            } else {
                attributes.remove(name);
            }
        }
        return this;
    }

    @Override
    public <T> Optional<T> removeAttribute(CharSequence name, Class<T> type) {
        final Object o = attributes.remove(name);
        if (type.isInstance(o)) {
            return Optional.of((T) o);
        }
        return Optional.empty();
    }

    @Override
    public MutableConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public final synchronized void configure() {
        if (this.running.get()) {
            configurationFailure("already running");
        }
        if (this.terminating.get()) {
            configurationFailure("currently terminating");
        }
        if (this.initializing.get()) {
            configurationFailure("currently initializing");
        }
        configureContextInternal();
    }

    /**
     * Configures the context reading all bean definitions.
     */
    @Internal
    void configureContextInternal() {
        if (configured.compareAndSet(false, true)) {
            readAllBeanConfigurations();
            beanDefinitionProvider.initialize(this);
            // an index exhaustiveness computed before the bean definitions were read does not hold for them
            beanDefinitionsEpoch.incrementAndGet();
            // before anything resolves a bean, including the application context configurers that run next in
            // an application context: a configurer touching a retained type must find the retained instance
            adoptRetainedRegistrations();
        }
    }

    /**
     * A registration created during shutdown.
     *
     * @param owner The dependencies that own it, or null when the caller of the context does
     * @param registration The registration
     */
    private record ShutdownDependent(@Nullable DefaultBeanDependencies owner, BeanRegistration<?> registration) {
    }

    private static void configurationFailure(String message) {
        throw new ConfigurationException("Bean context is " + message + ". The configure() method can only be called prior to startup");
    }

    /**
     * @param <T> The type
     * @param <R> The return type
     */
    private abstract static sealed class AbstractExecutionHandle<T, R> implements MethodExecutionHandle<T, R> {
        protected final ExecutableMethod<T, R> method;

        /**
         * @param method The method
         */
        AbstractExecutionHandle(ExecutableMethod<T, R> method) {
            this.method = method;
        }

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
        @Nullable
        public R invoke(@Nullable Object... arguments) {
            return method.invoke(target, arguments);
        }

        @Override
        @Nullable
        public R invokeUnsafe(@Nullable Object... arguments) {
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
        private final DefaultBeanContext beanContext;
        private final Class<T> beanType;
        private final Argument<T> beanArgument;
        private final @Nullable Qualifier<T> qualifier;
        private final boolean isSingleton;
        private final BeanDefinition<T> definition;

        private volatile @Nullable T target;

        /**
         * @param beanContext The bean context
         * @param beanType    The bean type
         * @param qualifier   The qualifier
         * @param method      The method
         */
        BeanExecutionHandle(DefaultBeanContext beanContext, BeanDefinition<T> definition, Class<T> beanType, @Nullable Qualifier<T> qualifier, ExecutableMethod<T, R> method) {
            super(method);
            this.beanContext = beanContext;
            this.beanType = beanType;
            this.beanArgument = Argument.of(beanType);
            this.qualifier = qualifier;
            this.isSingleton = definition.isSingleton();
            this.definition = definition;
        }

        @Override
        public T getTarget() {
            T target = this.target;
            if (target == null) {
                synchronized (this) { // double check
                    target = this.target;
                    if (target == null) {
                        try (BeanResolutionContext resolutionContext = beanContext.newResolutionContext(definition, null)) {
                            target = beanContext.getBean(resolutionContext, beanArgument, qualifier);
                            this.target = target;
                        }
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
        @Nullable
        public R invoke(@Nullable Object... arguments) {
            if (isSingleton) {
                return method.invoke(getTarget(), arguments);
            } else {
                return method.invoke(beanContext.getBean(beanType, qualifier), arguments);
            }
        }
    }

    /**
     * Internal supplier of listeners.
     *
     * @param <T> The listener type
     * @author Denis Stepanov
     * @since 4.0.0
     */
    @Internal
    sealed interface ListenersSupplier<T extends EventListener> {

        /**
         * Retrieved the listeners lazily.
         *
         * @param beanResolutionContext The bean resolution context
         * @return the collection of listeners along with their order value for later sorting.
         */
        Iterable<ListenerAndOrder<T>> get(@Nullable BeanResolutionContext beanResolutionContext);

        record ListenerAndOrder<T>(T bean, int order) implements Ordered {
            @Override
            public int getOrder() {
                return order;
            }
        }
    }

    /**
     * Class used as a bean key.
     *
     * @param <T> The bean type
     */
    @SuppressWarnings("java:S1948")
    static final class BeanKey<T> implements BeanIdentifier {
        final Argument<T> beanType;
        private final @Nullable Qualifier<T> qualifier;
        private final int hashCode;

        /**
         * A bean key for the given bean definition.
         *
         * @param definition The definition
         * @param qualifier  The qualifier
         */
        BeanKey(BeanDefinition<T> definition, @Nullable Qualifier<T> qualifier) {
            this(definition.asArgument(), qualifier);
        }

        /**
         * A bean key for the given bean definition.
         *
         * @param argument  The argument
         * @param qualifier The qualifier
         */
        BeanKey(Argument<T> argument, @Nullable Qualifier<T> qualifier) {
            this.beanType = argument;
            this.qualifier = qualifier;
            this.hashCode = argument.typeHashCode();
        }

        /**
         * @param beanType      The bean type
         * @param qualifier     The qualifier
         * @param typeArguments The type arguments
         */
        BeanKey(Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Class<?>... typeArguments) {
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
        private final @Nullable Qualifier<T> qualifier;
        private final boolean throwNonUnique;
        private final int hashCode;

        /**
         * A bean key for the given bean definition.
         *
         * @param argument       The argument
         * @param qualifier      The qualifier
         * @param throwNonUnique The throwNonUnique
         */
        BeanCandidateKey(Argument<T> argument, @Nullable Qualifier<T> qualifier, boolean throwNonUnique) {
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

    private final class EventListenerListenersSupplier<T extends EventListener> implements ListenersSupplier<T> {

        private final List<BeanDefinition<T>> listenersDefinitions;
        private final Argument<?> eventType;
        // The supplier can be triggered concurrently.
        // We allow for the listeners collection to be initialized multiple times.
        @SuppressWarnings("java:S3077")
        @Nullable
        private volatile List<ListenerAndOrder<T>> listeners;

        EventListenerListenersSupplier(Argument<?> eventType, List<BeanDefinition<T>> listenersDefinitions) {
            this.eventType = eventType;
            this.listenersDefinitions = listenersDefinitions;
        }

        @Override
        public Iterable<ListenerAndOrder<T>> get(@Nullable BeanResolutionContext beanResolutionContext) {
            if (listeners == null) {
                List<ListenerAndOrder<T>> listeners = new ArrayList<>(listenersDefinitions.size());
                List<BeanRegistration<T>> registrations = new ArrayList<>(listenersDefinitions.size());
                for (BeanDefinition<T> listenersDefinition : listenersDefinitions) {
                    BeanRegistration<T> registration;
                    if (beanResolutionContext == null) {
                        try (BeanResolutionContext context = newResolutionContext(listenersDefinition, null)) {
                            try (BeanResolutionContext.Path ignored = context.getPath().pushEventListenerResolve(
                                listenersDefinition,
                                eventType
                            )) {
                                registration = resolveBeanRegistration(context, listenersDefinition);
                            }
                        }
                    } else {
                        try (BeanResolutionContext.Path ignored = beanResolutionContext.getPath().pushEventListenerResolve(
                            listenersDefinition,
                            eventType
                        )) {
                            registration = resolveBeanRegistration(beanResolutionContext, listenersDefinition);
                        }
                    }
                    registrations.add(registration);
                }
                for (BeanRegistration<T> registration : registrations) {
                    listeners.add(new ListenerAndOrder<>(registration.getBean(), registration.getOrder()));
                }
                this.listeners = listeners;
            }
            return listeners;
        }
    }

    private static final class BeanDefinitionProcessorListenerSupplier implements ListenersSupplier<BeanCreatedEventListener> {

        @Override
        public Iterable<ListenerAndOrder<BeanCreatedEventListener>> get(@Nullable BeanResolutionContext beanResolutionContext) {
            return Collections.singletonList(new ListenerAndOrder<>(new BeanDefinitionProcessorListener(), 0));
        }

    }

    private static final class ExecutableMethodProcessorListenerSupplier implements ListenersSupplier<BeanCreatedEventListener> {

        @Override
        public Iterable<ListenerAndOrder<BeanCreatedEventListener>> get(@Nullable BeanResolutionContext beanResolutionContext) {
            return Collections.singletonList(new ListenerAndOrder<>(new ExecutableMethodProcessorListener(), 0));
        }

    }

    private final class SingletonBeanResolutionContext extends AbstractBeanResolutionContext {

        public SingletonBeanResolutionContext(@Nullable BeanDefinition<?> beanDefinition) {
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
        @Nullable Collection<BeanRegistration<T>> registrations;
    }

    private final class BeanContextUnsafeExecutionHandle extends BeanContextExecutionHandle implements UnsafeExecutionHandle<Object, Object> {

        private final UnsafeExecutable<Object, Object> unsafeExecutionHandle;

        public BeanContextUnsafeExecutionHandle(ExecutableMethod<Object, ?> method, BeanDefinition<?> beanDefinition, UnsafeExecutable<Object, Object> unsafeExecutionHandle) {
            super(method, beanDefinition);
            this.unsafeExecutionHandle = unsafeExecutionHandle;
        }

        @Override
        @Nullable
        public Object invokeUnsafe(@Nullable Object... arguments) {
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
        private volatile @Nullable Object target;

        public BeanContextExecutionHandle(ExecutableMethod<Object, ?> method, BeanDefinition<?> beanDefinition) {
            this.method = method;
            this.beanDefinition = beanDefinition;
        }

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
        @Nullable
        public Object invoke( @Nullable Object... arguments) {
            return method.invoke(getTarget(), arguments);
        }

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
