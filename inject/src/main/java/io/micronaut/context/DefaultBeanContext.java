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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.DefaultConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StreamUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.core.value.ValueResolver;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionMethodReference;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.ParametrizedBeanFactory;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.ValidatedBeanDefinition;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
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
    @SuppressWarnings("rawtypes")
    private static final Qualifier PROXY_TARGET_QUALIFIER = new Qualifier<Object>() {
        @SuppressWarnings("rawtypes")
        @Override
        public <BT extends BeanType<Object>> Stream<BT> reduce(Class<Object> beanType, Stream<BT> candidates) {
            return candidates.filter(bt -> {
                if (bt instanceof BeanDefinitionDelegate) {
                    return !(((BeanDefinitionDelegate) bt).getDelegate() instanceof ProxyBeanDefinition);
                } else {
                    return !(bt instanceof ProxyBeanDefinition);
                }
            });
        }
    };
    private static final String SCOPED_PROXY_ANN = "io.micronaut.runtime.context.scope.ScopedProxy";
    private static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction";
    private static final String ADAPTER_TYPE = "io.micronaut.aop.Adapter";
    private static final String NAMED_MEMBER = "named";
    private static final String QUALIFIER_MEMBER = "qualifier";
    private static final String PARALLEL_TYPE = Parallel.class.getName();
    private static final String INDEXES_TYPE = Indexes.class.getName();
    private static final String REPLACES_ANN = Replaces.class.getName();
    private static final Comparator<BeanRegistration<?>> BEAN_REGISTRATION_COMPARATOR = (o1, o2) -> {
        int order1 = OrderUtil.getOrder(o1.getBeanDefinition(), o1.getBean());
        int order2 = OrderUtil.getOrder(o2.getBeanDefinition(), o2.getBean());
        return Integer.compare(order1, order2);
    };

    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean initializing = new AtomicBoolean(false);
    protected final AtomicBoolean terminating = new AtomicBoolean(false);

    final Map<BeanIdentifier, BeanRegistration<?>> singlesInCreation = new ConcurrentHashMap<>(5);
    Set<Map.Entry<Class<?>, List<BeanInitializedEventListener>>> beanInitializedEventListeners;

    private final SingletonScope singletonScope = new SingletonScope();

    private final BeanContextConfiguration beanContextConfiguration;
    private final Collection<BeanDefinitionReference> beanDefinitionsClasses = new ConcurrentLinkedQueue<>();
    private final Map<String, BeanConfiguration> beanConfigurations = new HashMap<>(10);
    private final Map<BeanKey, Boolean> containsBeanCache = new ConcurrentHashMap<>(30);
    private final Map<CharSequence, Object> attributes = Collections.synchronizedMap(new HashMap<>(5));

    private final Map<BeanKey, CollectionHolder> singletonBeanRegistrations = new ConcurrentHashMap<>(50);

    private final Map<BeanCandidateKey, Optional<BeanDefinition>> beanConcreteCandidateCache =
            new ConcurrentLinkedHashMap.Builder<BeanCandidateKey, Optional<BeanDefinition>>().maximumWeightedCapacity(30).build();

    private final Map<Argument, Collection<BeanDefinition>> beanCandidateCache = new ConcurrentLinkedHashMap.Builder<Argument, Collection<BeanDefinition>>().maximumWeightedCapacity(30).build();

    private final Map<Class, Collection<BeanDefinitionReference>> beanIndex = new ConcurrentHashMap<>(12);

    private final ClassLoader classLoader;
    private final Set<Class> thisInterfaces = CollectionUtils.setOf(
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
    private final Set<Class> indexedTypes = CollectionUtils.setOf(
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

    private Set<Map.Entry<Class<?>, List<BeanCreatedEventListener<?>>>> beanCreationEventListeners;
    private Set<Map.Entry<Class<?>, List<BeanPreDestroyEventListener>>> beanPreDestroyEventListeners;
    private Set<Map.Entry<Class<?>, List<BeanDestroyedEventListener>>> beanDestroyedEventListeners;

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
        List<String> eagerInitStereotypes = new ArrayList<>(eagerInitAnnotated.size());
        for (Class<? extends Annotation> ann : eagerInitAnnotated) {
            eagerInitStereotypes.add(ann.getName());
        }
        this.eagerInitStereotypes = eagerInitStereotypes.toArray(new String[0]);
        this.eagerInitStereotypesPresent = !eagerInitStereotypes.isEmpty();
        this.eagerInitSingletons = eagerInitStereotypesPresent && (eagerInitStereotypes.contains(AnnotationUtil.SINGLETON) || eagerInitStereotypes.contains(Singleton.class.getName()));
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
                // Reset possibly modified shared context
                ((DefaultConversionService) ConversionService.SHARED).reset();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Starting BeanContext");
                }
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

            singletonBeanRegistrations.clear();
            beanConcreteCandidateCache.clear();
            beanCandidateCache.clear();
            containsBeanCache.clear();
            beanConfigurations.clear();
            singletonScope.clear();
            beanInitializedEventListeners = null;
            beanCreationEventListeners = null;
            beanPreDestroyEventListeners = null;
            beanDestroyedEventListeners = null;
            ((DefaultConversionService) ConversionService.SHARED).reset();
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
        return findBeanDefinition(Argument.of(type), null, false)
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
        if (beanRegistration.bean != null) {
            beanRegistration.definition().inject(this, beanRegistration.bean);
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
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(Class<T> beanType, String method, Class... arguments) {
        return findExecutionHandle(beanType, null, method, arguments);
    }

    @Override
    public MethodExecutionHandle<?, Object> createExecutionHandle(BeanDefinition<? extends Object> beanDefinition, ExecutableMethod<Object, ?> method) {
        return new MethodExecutionHandle<Object, Object>() {

            private Object target;

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
            public ExecutableMethod<?, Object> getExecutableMethod() {
                return (ExecutableMethod<?, Object>) method;
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(Class<T> beanType, Qualifier<?> qualifier, String method, Class... arguments) {
        Optional<? extends BeanDefinition<?>> foundBean = findBeanDefinition(beanType, (Qualifier) qualifier);
        if (foundBean.isPresent()) {
            BeanDefinition<?> beanDefinition = foundBean.get();
            Optional<? extends ExecutableMethod<?, Object>> foundMethod = beanDefinition.findMethod(method, arguments);
            if (foundMethod.isPresent()) {
                return foundMethod.map((ExecutableMethod executableMethod) ->
                        new BeanExecutionHandle(this, beanType, qualifier, executableMethod)
                );
            } else {
                return beanDefinition.findPossibleMethods(method)
                        .findFirst()
                        .filter(m -> {
                            Class[] argTypes = m.getArgumentTypes();
                            if (argTypes.length == arguments.length) {
                                for (int i = 0; i < argTypes.length; i++) {
                                    if (!arguments[i].isAssignableFrom(argTypes[i])) {
                                        return false;
                                    }
                                }
                                return true;
                            }
                            return false;
                        })
                        .map((ExecutableMethod executableMethod) -> new BeanExecutionHandle(this, beanType, qualifier, executableMethod));
            }
        }
        return Optional.empty();
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findExecutableMethod(Class<T> beanType, String method, Class[] arguments) {
        if (beanType != null) {
            Collection<BeanDefinition<T>> definitions = getBeanDefinitions(beanType);
            if (!definitions.isEmpty()) {
                BeanDefinition<T> beanDefinition = definitions.iterator().next();
                Optional<ExecutableMethod<T, R>> foundMethod = beanDefinition.findMethod(method, arguments);
                if (foundMethod.isPresent()) {
                    return foundMethod;
                } else {
                    return beanDefinition.<R>findPossibleMethods(method)
                            .findFirst();
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(T bean, String method, Class[] arguments) {
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
            beanDefinition = findBeanDefinition(type, qualifier).orElse(null);
            if (beanDefinition == null) {
                // Purge cache miss
                beanCandidateCache.entrySet().removeIf(entry -> entry.getKey().isInstance(singleton));
                beanConcreteCandidateCache.entrySet().removeIf(entry -> entry.getKey().beanType.isInstance(singleton));
            }
        } else {
            beanDefinition = null;
        }
        if (beanDefinition != null && beanDefinition.getBeanType().isInstance(singleton)) {
            try (BeanResolutionContext context = newResolutionContext(beanDefinition, null)) {
                doInject(context, singleton, beanDefinition);
                DefaultBeanContext.BeanKey<T> key = new DefaultBeanContext.BeanKey<>(beanDefinition.asArgument(), qualifier);
                singletonScope.registerSingletonBean(BeanRegistration.of(this, key, beanDefinition, singleton), qualifier);
            }
        } else {
            NoInjectionBeanDefinition<T> dynamicRegistration = new NoInjectionBeanDefinition<>(singleton.getClass(), qualifier);
            if (qualifier instanceof Named) {
                final BeanDefinitionDelegate<T> delegate = BeanDefinitionDelegate.create(dynamicRegistration);
                delegate.put(BeanDefinition.NAMED_ATTRIBUTE, ((Named) qualifier).getName());
                beanDefinition = delegate;
            } else {
                beanDefinition = dynamicRegistration;
            }
            beanDefinitionsClasses.add(dynamicRegistration);
            DefaultBeanContext.BeanKey<T> key = new DefaultBeanContext.BeanKey<>(beanDefinition.asArgument(), qualifier);
            singletonScope.registerSingletonBean(BeanRegistration.of(this, key, dynamicRegistration, singleton), qualifier);

            for (Class indexedType : indexedTypes) {
                if (indexedType == type || indexedType.isAssignableFrom(type)) {
                    final Collection<BeanDefinitionReference> indexed = resolveTypeIndex(indexedType);
                    BeanDefinition<T> finalBeanDefinition = beanDefinition;
                    indexed.add(new AbstractBeanDefinitionReference(type.getName(), type.getName()) {
                        @Override
                        protected Class<? extends BeanDefinition<?>> getBeanDefinitionType() {
                            return (Class<? extends BeanDefinition<?>>) finalBeanDefinition.getClass();
                        }

                        @Override
                        public BeanDefinition load() {
                            return finalBeanDefinition;
                        }

                        @Override
                        public Class getBeanType() {
                            return type;
                        }
                    });
                    break;
                }
            }
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
                .orElseThrow(() -> new NoSuchBeanException(beanType, qualifier));
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Argument<T> beanType, Qualifier<T> qualifier) {
        BeanDefinition<T> beanDefinition = singletonScope.findCachedSingletonBeanDefinition(beanType, qualifier);
        if (beanDefinition != null) {
            return Optional.of(beanDefinition);
        }
        return findConcreteCandidate(null, beanType, qualifier, true);
    }

    private <T> Optional<BeanDefinition<T>> findBeanDefinition(Argument<T> beanType, Qualifier<T> qualifier, boolean throwNonUnique) {
        return findConcreteCandidate(null, beanType, qualifier, throwNonUnique);
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
            candidates = qualifier.reduce(beanType.getType(), new ArrayList<>(candidates).stream()).collect(Collectors.toList());
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
            if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getSimpleName(), e.getMessage());
            }
            throw new NoSuchBeanException(beanType, qualifier);
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

        Collection<BeanDefinition> candidates = findBeanCandidatesForInstance(instance);
        if (candidates.size() == 1) {
            BeanDefinition<T> beanDefinition = candidates.iterator().next();
            try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
                final BeanKey<T> beanKey = new BeanKey<>(beanDefinition.getBeanType(), null);
                resolutionContext.addInFlightBean(
                        beanKey,
                        new BeanRegistration<>(beanKey, beanDefinition, instance)
                );
                doInject(
                        resolutionContext,
                        instance,
                        beanDefinition
                );
            }

        } else if (!candidates.isEmpty()) {
            final Iterator iterator = candidates.iterator();
            throw new NonUniqueBeanException(instance.getClass(), iterator);
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
        throw new NoSuchBeanException(beanType);
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
                return doCreateBean(resolutionContext, definition, beanArg, qualifier, args);
            }
        }
        throw new NoSuchBeanException(beanType);
    }

    /**
     * @param resolutionContext The bean resolution context
     * @param definition        The bean definition
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param args              The argument values
     * @param <T>               the bean generic type
     * @return The instance
     */
    @NonNull
    protected <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                                 @NonNull BeanDefinition<T> definition,
                                 @NonNull Argument<T> beanType,
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
        if (!(definition instanceof ParametrizedBeanFactory)) {
            return Collections.emptyMap();
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating bean for parameters: {}", ArrayUtils.toString(args));
        }
        Argument[] requiredArguments = ((ParametrizedBeanFactory) definition).getRequiredArguments();
        Map<String, Object> argumentValues = new LinkedHashMap<>(requiredArguments.length);
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
                            argumentValues.put(requiredArgument.getName(), ConversionService.SHARED.convert(val, requiredArgument).orElseThrow(() ->
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
        if (beanToDestroy instanceof LifeCycle) {
            try {
                ((LifeCycle<?>) beanToDestroy).stop();
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
            beanPreDestroyEventListeners = loadListeners(BeanPreDestroyEventListener.class).entrySet();
        }
        if (!beanPreDestroyEventListeners.isEmpty()) {
            Class<T> beanType = getBeanType(beanDefinition);
            for (Map.Entry<Class<?>, List<BeanPreDestroyEventListener>> entry : beanPreDestroyEventListeners) {
                if (entry.getKey().isAssignableFrom(beanType)) {
                    final BeanPreDestroyEvent<T> event = new BeanPreDestroyEvent<>(this, beanDefinition, bean);
                    for (BeanPreDestroyEventListener<T> listener : entry.getValue()) {
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
        if (registration instanceof BeanDisposingRegistration) {
            BeanDisposingRegistration<?> disposingRegistration = (BeanDisposingRegistration<?>) registration;
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
        if (!declaredScope.isPresent()) {
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
            beanDestroyedEventListeners = loadListeners(BeanDestroyedEventListener.class).entrySet();
        }
        if (!beanDestroyedEventListeners.isEmpty()) {
            Class<T> beanType = getBeanType(beanDefinition);
            for (Map.Entry<Class<?>, List<BeanDestroyedEventListener>> entry : beanDestroyedEventListeners) {
                if (entry.getKey().isAssignableFrom(beanType)) {
                    final BeanDestroyedEvent<T> event = new BeanDestroyedEvent<>(this, beanDefinition, bean);
                    for (BeanDestroyedEventListener<T> listener : entry.getValue()) {
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
        throw new NoSuchBeanException(beanType);
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
            BeanDefinition definition = concreteCandidate.get();
            if (requestingBeanDefinition != null && requestingBeanDefinition.equals(definition)) {
                // bail out, don't inject for bean definition in creation
                return instance;
            }
            doInject(resolutionContext, instance, definition);
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

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public <T> T getProxyTargetBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getProxyTargetBean(Argument.of(beanType), qualifier);
    }

    @NonNull
    @Override
    public <T> T getProxyTargetBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        @SuppressWarnings("unchecked")
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        return resolveBeanRegistration(null, definition, beanType, proxyQualifier).bean;
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
        @SuppressWarnings("unchecked")
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        return resolveBeanRegistration(
                resolutionContext,
                definition, beanType, proxyQualifier
        ).bean;
    }

    @NonNull
    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(@NonNull Class<T> beanType, @NonNull String method, @NonNull Class[] arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, null);
        return definition.findMethod(method, arguments);
    }

    @NonNull
    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(@NonNull Class<T> beanType, Qualifier<T> qualifier, @NonNull String method, Class... arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return definition.findMethod(method, arguments);
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(@NonNull Argument<T> beanType, Qualifier<T> qualifier, @NonNull String method, Class... arguments) {
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
    public <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        @SuppressWarnings("unchecked")
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        BeanCandidateKey<T> key = new BeanCandidateKey<>(beanType, proxyQualifier, true);

        Optional beanDefinition = beanConcreteCandidateCache.get(key);
        //noinspection OptionalAssignedToNull
        if (beanDefinition == null) {
            BeanRegistration<T> beanRegistration = singletonScope.findCachedSingletonBeanRegistration(beanType, qualifier);
            if (beanRegistration != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Resolved existing bean [{}] for type [{}] and qualifier [{}]", beanRegistration.bean, beanType, qualifier);
                }
                beanDefinition = Optional.of(beanRegistration.beanDefinition);
            } else {
                beanDefinition = findConcreteCandidateNoCache(null, beanType, proxyQualifier, true, false);
            }

            beanConcreteCandidateCache.put(key, beanDefinition);
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
            Stream<BeanDefinitionReference> reduced = qualifier.reduce(Object.class, beanDefinitionsClasses.stream());
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
            filterProxiedTypes(candidates, true, true, null);
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
            List collection = beanDefinitionsClasses
                    .stream()
                    .map(ref -> ref.load(this))
                    .filter(candidate -> candidate.isEnabled(this))
                    .collect(Collectors.toList());
            return collection;
        }

        return (Collection<BeanDefinition<?>>) Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public Collection<BeanDefinitionReference<?>> getBeanDefinitionReferences() {
        if (!beanDefinitionsClasses.isEmpty()) {
            final List refs = beanDefinitionsClasses.stream().filter(ref -> ref.isEnabled(this))
                    .collect(Collectors.toList());

            return Collections.unmodifiableList(refs);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public <B> BeanContext registerBeanDefinition(@NonNull RuntimeBeanDefinition<B> definition) {
        Objects.requireNonNull(definition, "Bean definition cannot be null");
        this.beanDefinitionsClasses.add(definition);
        beanCandidateCache.entrySet().removeIf(entry -> entry.getKey().isAssignableFrom(definition.getBeanType()));
        beanConcreteCandidateCache.entrySet().removeIf(entry -> entry.getKey().beanType.isAssignableFrom(definition.getBeanType()));
        singletonBeanRegistrations.entrySet().removeIf(entry -> entry.getKey().beanType.isAssignableFrom(definition.getBeanType()));
        containsBeanCache.entrySet().removeIf(entry -> entry.getKey().beanType.isAssignableFrom(definition.getBeanType()));
        return this;
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
            if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getSimpleName(), e.getMessage());
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
        final Map<Class<?>, List<BeanCreatedEventListener<?>>> beanCreatedListeners = loadCreatedListeners();
        beanCreatedListeners.put(AnnotationProcessor.class, Collections.singletonList(new AnnotationProcessorListener()));
        final Map<Class<?>, List<BeanInitializedEventListener>> beanInitializedListeners = loadListeners(BeanInitializedEventListener.class);
        this.beanCreationEventListeners = beanCreatedListeners.entrySet();
        this.beanInitializedEventListeners = beanInitializedListeners.entrySet();
    }

    private void handleEagerInitializedDependencies(BeanDefinition<?> listener,
                                                    Argument<?> listensTo,
                                                    List<List<Argument<?>>> targets) {
        if (LOG.isWarnEnabled()) {
            List<String> paths = new ArrayList<>(targets.size());
            for (List<Argument<?>> line: targets) {
                paths.add("    " + line.stream()
                        .map(Argument::getType)
                        .map(Class::getName)
                        .collect(Collectors.joining(AbstractBeanResolutionContext.DefaultPath.RIGHT_ARROW)));
            }
            LOG.warn("The bean created event listener {} will not be executed because one or more other bean created event listeners inject {}:\n" +
                    "{}\n" +
                    "Change at least one point in the path to be lazy initialized by injecting a provider to avoid this issue", listener.getBeanType().getName(), listensTo.getType().getName(), String.join("\n", paths));
        }
    }

    @NonNull
    private Map<Class<?>, List<BeanCreatedEventListener<?>>> loadCreatedListeners() {
        final Collection<BeanDefinition<BeanCreatedEventListener>> beanDefinitions = getBeanDefinitions(BeanCreatedEventListener.class);
        final HashMap<Class<?>, List<BeanCreatedEventListener<?>>> typeToListener = new HashMap<>(beanDefinitions.size(), 1);
        if (beanDefinitions.isEmpty()) {
            return typeToListener;
        }
        final HashMap<BeanDefinition<?>, List<List<Argument<?>>>> invalidListeners = new HashMap<>();
        final HashMap<BeanDefinition<?>, Argument<?>> beanCreationTargets = new HashMap<>();
        for (BeanDefinition<BeanCreatedEventListener> beanCreatedDefinition: beanDefinitions) {
            List<Argument<?>> typeArguments = beanCreatedDefinition.getTypeArguments(BeanCreatedEventListener.class);
            Argument<?> argument = CollectionUtils.last(typeArguments);
            if (argument == null) {
                argument = Argument.OBJECT_ARGUMENT;
            }
            beanCreationTargets.put(beanCreatedDefinition, argument);
        }
        for (BeanDefinition<BeanCreatedEventListener> beanCreatedDefinition: beanDefinitions) {
            try (ScanningBeanResolutionContext context = new ScanningBeanResolutionContext(beanCreatedDefinition, beanCreationTargets)) {
                BeanCreatedEventListener<?> listener = resolveBeanRegistration(context, beanCreatedDefinition).bean;
                List<Argument<?>> typeArguments = beanCreatedDefinition.getTypeArguments(BeanCreatedEventListener.class);
                Argument<?> argument = CollectionUtils.last(typeArguments);
                if (argument == null) {
                    argument = Argument.OBJECT_ARGUMENT;
                }
                typeToListener.computeIfAbsent(argument.getType(), aClass -> new ArrayList<>(10))
                        .add(listener);
                Map<BeanDefinition<?>, List<List<Argument<?>>>> foundTargets = context.getFoundTargets();
                for (Map.Entry<BeanDefinition<?>, List<List<Argument<?>>>> entry: foundTargets.entrySet()) {
                    invalidListeners.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                            .addAll(entry.getValue());
                }
            }
        }
        for (List<BeanCreatedEventListener<?>> listeners: typeToListener.values()) {
            OrderUtil.sort(listeners);
        }
        for (Map.Entry<BeanDefinition<?>, List<List<Argument<?>>>> entry: invalidListeners.entrySet()) {
            handleEagerInitializedDependencies(entry.getKey(), beanCreationTargets.get(entry.getKey()), entry.getValue());
        }
        return typeToListener;
    }

    @NonNull
    private <T extends EventListener> Map<Class<?>, List<T>> loadListeners(@NonNull Class<T> listenerType) {
        final Collection<BeanDefinition<T>> beanDefinitions = getBeanDefinitions(listenerType);
        final HashMap<Class<?>, List<T>> typeToListener = new HashMap<>(beanDefinitions.size(), 1);
        for (BeanDefinition<T> beanCreatedDefinition : beanDefinitions) {
            try (BeanResolutionContext context = newResolutionContext(beanCreatedDefinition, null)) {
                T listener = resolveBeanRegistration(context, beanCreatedDefinition).bean;
                List<Argument<?>> typeArguments = beanCreatedDefinition.getTypeArguments(listenerType);
                Argument<?> argument = CollectionUtils.last(typeArguments);
                if (argument == null) {
                    argument = Argument.OBJECT_ARGUMENT;
                }
                typeToListener.computeIfAbsent(argument.getType(), aClass -> new ArrayList<>(10))
                        .add(listener);
            }
        }
        for (List<T> listenerList : typeToListener.values()) {
            OrderUtil.sort(listenerList);
        }
        return typeToListener;
    }

    /**
     * Initialize the context with the given {@link io.micronaut.context.annotation.Context} scope beans.
     *
     * @param contextScopeBeans The context scope beans
     * @param processedBeans    The beans that require {@link ExecutableMethodProcessor} handling
     * @param parallelBeans     The parallel bean definitions
     */
    protected void initializeContext(
            @NonNull List<BeanDefinitionReference> contextScopeBeans,
            @NonNull List<BeanDefinitionReference> processedBeans,
            @NonNull List<BeanDefinitionReference> parallelBeans) {

        if (CollectionUtils.isNotEmpty(contextScopeBeans)) {
            final List<BeanDefinition> contextBeans = new ArrayList<>(contextScopeBeans.size());

            for (BeanDefinitionReference contextScopeBean : contextScopeBeans) {
                try {
                    loadContextScopeBean(contextScopeBean, contextBeans::add);
                } catch (Throwable e) {
                    throw new BeanInstantiationException("Bean definition [" + contextScopeBean.getName() + "] could not be loaded: " + e.getMessage(), e);
                }
            }
            filterProxiedTypes((Collection) contextBeans, true, false, null);
            filterReplacedBeans(null, (Collection) contextBeans);
            OrderUtil.sort(contextBeans);
            for (BeanDefinition contextScopeDefinition : contextBeans) {
                try {
                    loadContextScopeBean(contextScopeDefinition);
                } catch (DisabledBeanException e) {
                    if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                        AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", contextScopeDefinition.getBeanType().getSimpleName(), e.getMessage());
                    }
                } catch (Throwable e) {
                    throw new BeanInstantiationException("Bean definition [" + contextScopeDefinition.getName() + "] could not be loaded: " + e.getMessage(), e);
                }
            }
        }

        if (!processedBeans.isEmpty()) {

            @SuppressWarnings("unchecked") Stream<BeanDefinitionMethodReference<?, ?>> methodStream = processedBeans
                    .stream()
                    // is the bean reference enabled
                    .filter(ref -> ref.isEnabled(this))
                    // ok - continue and load it
                    .map((Function<BeanDefinitionReference, BeanDefinition<?>>) reference -> {
                        try {
                            return reference.load(this);
                        } catch (Exception e) {
                            throw new BeanInstantiationException("Bean definition [" + reference.getName() + "] could not be loaded: " + e.getMessage(), e);
                        }
                    })
                    // is the bean itself enabled
                    .filter(bean -> bean.isEnabled(this))
                    // ok continue and get all of the ExecutableMethod references
                    .flatMap(beanDefinition ->
                            beanDefinition.getExecutableMethods()
                                    .parallelStream()
                                    .filter(method -> method.hasStereotype(Executable.class))
                                    .map((Function<ExecutableMethod<?, ?>, BeanDefinitionMethodReference<?, ?>>) executableMethod ->
                                            BeanDefinitionMethodReference.of((BeanDefinition) beanDefinition, executableMethod)
                                    )
                    );

            // group the method references by annotation type such that we have a map of Annotation -> MethodReference
            // ie. Class<Scheduled> -> @Scheduled void someAnnotation()
            Map<Class<? extends Annotation>, List<BeanDefinitionMethodReference<?, ?>>> byAnnotation = new HashMap<>(processedBeans.size());
            methodStream.forEach(reference -> {
                List<Class<? extends Annotation>> annotations = reference.getAnnotationTypesByStereotype(Executable.class);
                annotations.forEach(annotation -> byAnnotation.compute(annotation, (ann, list) -> {
                    if (list == null) {
                        list = new ArrayList<>(10);
                    }
                    list.add(reference);
                    return list;
                }));
            });

            // Find ExecutableMethodProcessor for each annotation and process the BeanDefinitionMethodReference
            byAnnotation.forEach((annotationType, methods) ->
                    streamOfType(ExecutableMethodProcessor.class, Qualifiers.byTypeArguments(annotationType))
                            .forEach(processor -> {
                                if (processor instanceof LifeCycle<?>) {
                                    ((LifeCycle<?>) processor).start();
                                }
                                for (BeanDefinitionMethodReference<?, ?> method : methods) {

                                    BeanDefinition<?> beanDefinition = method.getBeanDefinition();

                                    // Only process the method if the the annotation is not declared at the class level
                                    // If declared at the class level it will already have been processed by AnnotationProcessorListener
                                    if (!beanDefinition.hasStereotype(annotationType)) {
                                        //noinspection unchecked
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

                                if (processor instanceof LifeCycle<?>) {
                                    ((LifeCycle<?>) processor).stop();
                                }

                            }));
        }

        if (CollectionUtils.isNotEmpty(parallelBeans)) {
            processParallelBeans(parallelBeans);
        }
        final Runnable runnable = () ->
                beanDefinitionsClasses.removeIf((BeanDefinitionReference beanDefinitionReference) ->
                        !beanDefinitionReference.isEnabled(this));
        ForkJoinPool.commonPool().execute(runnable);
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param <T>      The bean generic type
     * @param beanType The bean type
     * @param filter   A bean definition to filter out
     * @return The candidates
     */
    @NonNull
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(@NonNull Class<T> beanType, @Nullable BeanDefinition<?> filter) {
        return findBeanCandidates(null, Argument.of(beanType), filter, true);
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param <T>               The bean generic type
     * @param resolutionContext The current resolution context
     * @param beanType          The bean type
     * @param filter            A bean definition to filter out
     * @param filterProxied     Whether to filter out bean proxy targets
     * @return The candidates
     */
    @SuppressWarnings("unchecked")
    @NonNull
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(@Nullable BeanResolutionContext resolutionContext,
                                                                   @NonNull Argument<T> beanType,
                                                                   @Nullable BeanDefinition<?> filter,
                                                                   boolean filterProxied) {
        Predicate<BeanDefinition<T>> predicate = filter == null ? null : definition -> !definition.equals(filter);
        return findBeanCandidates(resolutionContext, beanType, filterProxied, predicate);
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param <T>               The bean generic type
     * @param resolutionContext The current resolution context
     * @param beanType          The bean type
     * @param filterProxied     Whether to filter out bean proxy targets
     * @param predicate         The predicate to filter candidates
     * @return The candidates
     */
    @SuppressWarnings("unchecked")
    @NonNull
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(@Nullable BeanResolutionContext resolutionContext,
                                                                   @NonNull Argument<T> beanType,
                                                                   boolean filterProxied,
                                                                   Predicate<BeanDefinition<T>> predicate) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        final Class<T> beanClass = beanType.getType();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for type: {}", beanType);
        }
        // first traverse component definition classes and load candidates

        Collection<BeanDefinitionReference> beanDefinitionsClasses;

        if (indexedTypes.contains(beanClass)) {
            beanDefinitionsClasses = beanIndex.get(beanClass);
            if (beanDefinitionsClasses == null) {
                beanDefinitionsClasses = Collections.emptyList();
            }
        } else {
            beanDefinitionsClasses = this.beanDefinitionsClasses;
        }

        Set<BeanDefinition<T>> candidates;
        if (!beanDefinitionsClasses.isEmpty()) {

            candidates = new HashSet<>();
            for (BeanDefinitionReference reference : beanDefinitionsClasses) {
                if (!reference.isCandidateBean(beanType) || !reference.isEnabled(this, resolutionContext)) {
                    continue;
                }
                BeanDefinition<T> loadedBean;
                try {
                    loadedBean = reference.load(this);
                } catch (Throwable e) {
                    throw new BeanContextException("Error loading bean [" + reference.getName() + "]: " + e.getMessage(), e);
                }
                if (!loadedBean.isCandidateBean(beanType)) {
                    continue;
                }
                if (predicate != null && !predicate.test(loadedBean)) {
                    continue;
                }
                if (!loadedBean.isEnabled(this, resolutionContext)) {
                    continue;
                }
                candidates.add(loadedBean);
            }

            if (!candidates.isEmpty()) {
                if (filterProxied) {
                    filterProxiedTypes(candidates, true, false, null);
                }
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
     * Method that transforms iterable candidates if possible.
     *
     * @param resolutionContext The resolution context
     * @param candidates        The candidates.
     * @param filterProxied     Whether to filter proxied.
     * @param <T>               The bean type
     * @return The candidates
     */
    protected <T> Collection<BeanDefinition<T>> transformIterables(BeanResolutionContext resolutionContext, Collection<BeanDefinition<T>> candidates, boolean filterProxied) {
        return candidates;
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param instance The bean instance
     * @param <T>      The bean generic type
     * @return The candidates
     */
    @NonNull
    protected <T> Collection<BeanDefinition> findBeanCandidatesForInstance(@NonNull T instance) {
        ArgumentUtils.requireNonNull("instance", instance);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for instance: {}", instance);
        }
        Collection<BeanDefinitionReference> beanDefinitionsClasses = this.beanDefinitionsClasses;
        final Class<?> beanClass = instance.getClass();
        Argument<?> beanType = Argument.of(beanClass);
        Collection<BeanDefinition> beanDefinitions = beanCandidateCache.get(beanType);
        if (beanDefinitions == null) {
            // first traverse component definition classes and load candidates
            if (!beanDefinitionsClasses.isEmpty()) {
                List<BeanDefinition> candidates = new ArrayList<>();
                for (BeanDefinitionReference<?> reference : beanDefinitionsClasses) {
                    if (!reference.isEnabled(this)) {
                        continue;
                    }
                    Class<?> candidateType = reference.getBeanType();
                    if (candidateType == null || !candidateType.isInstance(instance)) {
                        continue;
                    }
                    BeanDefinition<?> candidate = reference.load(this);
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
                                    !(candidate instanceof NoInjectionBeanDefinition) &&
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
            beanCandidateCache.put(beanType, beanDefinitions);
        }
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
        return doCreateBean(resolutionContext, beanDefinition, qualifier, Argument.of(beanDefinition.getBeanType()), false, argumentValues);
    }

    /**
     * Execution the creation of a bean. The returned value can be null if a
     * factory method returned null.
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param beanDefinition    The {@link BeanDefinition}
     * @param qualifier         The {@link Qualifier}
     * @param <T>               The bean generic type
     * @return The created bean
     */
    @Internal
    @NonNull
    final <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                             @NonNull BeanDefinition<T> beanDefinition,
                             @Nullable Qualifier<T> qualifier) {
        return doCreateBean(resolutionContext, beanDefinition, qualifier, Argument.of(beanDefinition.getBeanType()), false, null);
    }

    /**
     * Execution the creation of a bean. The returned value can be null if a
     * factory method returned null.
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param beanDefinition    The {@link BeanDefinition}
     * @param qualifier         The {@link Qualifier}
     * @param isSingleton       Whether the bean is a singleton
     * @param argumentValues    Any argument values passed to create the bean
     * @param <T>               The bean generic type
     * @return The created bean
     * @deprecated Use {@link #doCreateBean(BeanResolutionContext, BeanDefinition, Qualifier, Map)} instead.
     */
    @Internal
    @NonNull
    @Deprecated
    protected <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                                 @NonNull BeanDefinition<T> beanDefinition,
                                 @Nullable Qualifier<T> qualifier,
                                 boolean isSingleton,
                                 @Nullable Map<String, Object> argumentValues) {
        return doCreateBean(resolutionContext, beanDefinition, qualifier, Argument.of(beanDefinition.getBeanType()), isSingleton, argumentValues);
    }

    /**
     * Execution the creation of a bean. The returned value can be null if a
     * factory method returned null.
     * <p>
     * Method is deprecated since it doesn't do anything related to the singleton.
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param beanDefinition    The {@link BeanDefinition}
     * @param qualifier         The {@link Qualifier}
     * @param qualifierBeanType The bean type used in the qualifier
     * @param isSingleton       Whether the bean is a singleton
     * @param argumentValues    Any argument values passed to create the bean
     * @param <T>               The bean generic type
     * @return The created bean
     * @deprecated Use {@link #doCreateBean(BeanResolutionContext, BeanDefinition, Qualifier, Map)} instead.
     */
    @Internal
    @NonNull
    @Deprecated
    protected <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                                 @NonNull BeanDefinition<T> beanDefinition,
                                 @Nullable Qualifier<T> qualifier,
                                 @Nullable Argument<T> qualifierBeanType,
                                 boolean isSingleton,
                                 @Nullable Map<String, Object> argumentValues) {
        T bean;
        if (beanDefinition instanceof BeanFactory) {
            bean = resolveByBeanFactory(resolutionContext, beanDefinition, qualifier, argumentValues);
        } else {
            bean = resolveByBeanDefinition(resolutionContext, beanDefinition);
        }
        return postBeanCreated(resolutionContext, beanDefinition, qualifier, bean);
    }

    @NonNull
    private <T> T resolveByBeanDefinition(@NonNull BeanResolutionContext resolutionContext,
                                          @NonNull BeanDefinition<T> beanDefinition) {
        ConstructorInjectionPoint<T> constructor = beanDefinition.getConstructor();
        Argument<?>[] requiredConstructorArguments = constructor.getArguments();
        T bean;
        if (requiredConstructorArguments.length == 0) {
            bean = constructor.invoke();
        } else {
            Object[] constructorArgs = new Object[requiredConstructorArguments.length];
            for (int i = 0; i < requiredConstructorArguments.length; i++) {
                Class<?> argument = requiredConstructorArguments[i].getType();
                constructorArgs[i] = getBean(resolutionContext, argument);
            }
            bean = constructor.invoke(constructorArgs);
        }

        inject(resolutionContext, null, bean);
        return bean;
    }

    @NonNull
    private <T> T resolveByBeanFactory(@NonNull BeanResolutionContext resolutionContext,
                                       @NonNull BeanDefinition<T> beanDefinition,
                                       @Nullable Qualifier<T> qualifier,
                                       @Nullable Map<String, Object> argumentValues) {
        BeanFactory<T> beanFactory = (BeanFactory<T>) beanDefinition;
        Qualifier<T> declaredQualifier = beanDefinition.getDeclaredQualifier();
        boolean propagateQualifier = beanDefinition.isProxy() && declaredQualifier instanceof Named;
        Qualifier prevQualifier = resolutionContext.getCurrentQualifier();
        try {
            if (propagateQualifier) {
                resolutionContext.setAttribute(BeanDefinition.NAMED_ATTRIBUTE, ((Named) declaredQualifier).getName());
            }
            resolutionContext.setCurrentQualifier(declaredQualifier != null && !AnyQualifier.INSTANCE.equals(declaredQualifier) ? declaredQualifier : qualifier);
            T bean;
            if (beanFactory instanceof ParametrizedBeanFactory) {
                ParametrizedBeanFactory<T> parametrizedBeanFactory = (ParametrizedBeanFactory<T>) beanDefinition;
                Map<String, Object> convertedValues = getRequiredArgumentValues(resolutionContext, parametrizedBeanFactory.getRequiredArguments(),
                        argumentValues, beanDefinition);
                bean = (parametrizedBeanFactory).build(resolutionContext, this, beanDefinition, convertedValues);
            } else {
                bean = beanFactory.build(resolutionContext, this, beanDefinition);
            }
            if (bean == null) {
                throw new BeanInstantiationException(resolutionContext, "Bean Factory [" + beanFactory + "] returned null");
            }
            if (bean instanceof Qualified) {
                ((Qualified) bean).$withBeanQualifier(declaredQualifier);
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
            if (propagateQualifier) {
                resolutionContext.removeAttribute(BeanDefinition.NAMED_ATTRIBUTE);
            }
        }
    }

    @NonNull
    private <T> T postBeanCreated(@NonNull BeanResolutionContext resolutionContext,
                                  @NonNull BeanDefinition<T> beanDefinition,
                                  @Nullable Qualifier<T> qualifier,
                                  @NonNull T bean) {
        Qualifier<T> finalQualifier = qualifier != null ? qualifier : beanDefinition.getDeclaredQualifier();

        bean = triggerBeanCreatedEventListener(resolutionContext, beanDefinition, bean, finalQualifier);

        if (beanDefinition instanceof ValidatedBeanDefinition) {
            bean = ((ValidatedBeanDefinition<T>) beanDefinition).validate(resolutionContext, bean);
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
        Class<T> beanType = beanDefinition.getBeanType();
        if (!(bean instanceof BeanCreatedEventListener) && CollectionUtils.isNotEmpty(beanCreationEventListeners)) {
            for (Map.Entry<Class<?>, List<BeanCreatedEventListener<?>>> entry : beanCreationEventListeners) {
                if (entry.getKey().isAssignableFrom(beanType)) {
                    BeanKey<T> beanKey = new BeanKey<>(beanDefinition, finalQualifier);
                    for (BeanCreatedEventListener<?> listener : entry.getValue()) {
                        bean = (T) listener.onCreated(new BeanCreatedEvent(this, beanDefinition, beanKey, bean));
                        if (bean == null) {
                            throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onCreated event");
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
            convertedValues = requiredArguments.length == 0 ? null : new LinkedHashMap<>();
            argumentValues = Collections.emptyMap();
        } else {
            convertedValues = new LinkedHashMap<>();
        }
        if (convertedValues != null) {
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
                        convertedValue = ConversionService.SHARED.convert(val, requiredArgument).orElseThrow(() ->
                                new BeanInstantiationException(resolutionContext, "Invalid bean argument [" + requiredArgument + "]. Cannot convert object [" + val + "] to required type: " + requiredArgument.getType())
                        );
                    }
                    convertedValues.put(argumentName, convertedValue);
                }
            }
            return convertedValues;
        } else {
            return Collections.emptyMap();
        }
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
    protected void processParallelBeans(List<BeanDefinitionReference> parallelBeans) {
        if (!parallelBeans.isEmpty()) {
            List<BeanDefinitionReference> finalParallelBeans = parallelBeans.stream().filter(bdr -> bdr.isEnabled(this)).collect(Collectors.toList());
            if (!finalParallelBeans.isEmpty()) {
                new Thread(() -> {
                    Collection<BeanDefinition> parallelDefinitions = new ArrayList<>();
                    finalParallelBeans.forEach(beanDefinitionReference -> {
                        try {
                            loadContextScopeBean(beanDefinitionReference, parallelDefinitions::add);
                        } catch (Throwable e) {
                            LOG.error("Parallel Bean definition [" + beanDefinitionReference.getName() + "] could not be loaded: " + e.getMessage(), e);
                            Boolean shutdownOnError = beanDefinitionReference.getAnnotationMetadata().booleanValue(Parallel.class, "shutdownOnError").orElse(true);
                            if (shutdownOnError) {
                                stop();
                            }
                        }
                    });

                    filterProxiedTypes((Collection) parallelDefinitions, true, false, null);
                    filterReplacedBeans(null, (Collection) parallelDefinitions);

                    parallelDefinitions.forEach(beanDefinition -> ForkJoinPool.commonPool().execute(() -> {
                        try {
                            loadContextScopeBean(beanDefinition);
                        } catch (Throwable e) {
                            LOG.error("Parallel Bean definition [" + beanDefinition.getName() + "] could not be loaded: " + e.getMessage(), e);
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

    private <T> void filterReplacedBeans(BeanResolutionContext resolutionContext, Collection<? extends BeanType<T>> candidates) {
        if (candidates.size() > 1) {
            List<BeanType<T>> replacementTypes = new ArrayList<>(2);

            for (BeanType<T> candidate : candidates) {
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
                                                 List<BeanType<T>> replacementTypes,
                                                 BeanType<T> definitionToBeReplaced) {
        if (!definitionToBeReplaced.isEnabled(this, resolutionContext)) {
            return true;
        }
        final AnnotationMetadata annotationMetadata = definitionToBeReplaced.getAnnotationMetadata();
        if (annotationMetadata.hasDeclaredStereotype(Infrastructure.class)) {
            return false;
        }
        for (BeanType<T> replacementType : replacementTypes) {
            if (isNotTheSameDefinition(replacementType, definitionToBeReplaced) &&
                    isNotProxy(replacementType, definitionToBeReplaced) &&
                    checkIfReplaces(replacementType, definitionToBeReplaced, annotationMetadata)) {
                return true;
            }
        }
        return false;
    }

    private <T> boolean isNotTheSameDefinition(BeanType<T> replacingCandidate, BeanType<T> definitionToBeReplaced) {
        return replacingCandidate != definitionToBeReplaced;
    }

    private <T> boolean isNotProxy(BeanType<T> replacingCandidate, BeanType<T> definitionToBeReplaced) {
        return !(replacingCandidate instanceof ProxyBeanDefinition &&
                ((ProxyBeanDefinition<T>) replacingCandidate).getTargetDefinitionType() == definitionToBeReplaced.getClass());
    }

    private <T> boolean checkIfReplaces(BeanType<T> replacingCandidate, BeanType<T> definitionToBeReplaced, AnnotationMetadata annotationMetadata) {

        final AnnotationValue<Replaces> replacesAnnotation = replacingCandidate.getAnnotation(Replaces.class);
        Class replacedBeanType = replacesAnnotation.classValue().orElse(getCanonicalBeanType(replacingCandidate));
        final Optional<String> named = replacesAnnotation.stringValue(NAMED_MEMBER);
        final Optional<AnnotationClassValue<?>> qualifier = replacesAnnotation.annotationClassValue(QUALIFIER_MEMBER);

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

        Optional<Class<?>> factory = replacesAnnotation.classValue("factory");

        Optional<Class<?>> declaringType = definitionToBeReplaced instanceof BeanDefinition ?
                ((BeanDefinition<?>) definitionToBeReplaced).getDeclaringType() :
                Optional.empty();
        if (factory.isPresent() && declaringType.isPresent()) {
            final boolean factoryReplaces = factory.get() == declaringType.get() &&
                    checkIfTypeMatches(definitionToBeReplaced, annotationMetadata, replacedBeanType);
            if (factoryReplaces) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Bean [{}] replaces existing bean of type [{}] in factory type [{}]",
                            replacingCandidate.getBeanType(), replacedBeanType, factory.get());
                }
                return true;
            }
            return false;
        }

        final boolean isTypeMatches = checkIfTypeMatches(definitionToBeReplaced, annotationMetadata, replacedBeanType);
        if (isTypeMatches && LOG.isDebugEnabled()) {
            LOG.debug("Bean [{}] replaces existing bean of type [{}]", replacingCandidate.getBeanType(), replacedBeanType);
        }
        return isTypeMatches;
    }

    private <T> boolean qualifiedByQualifier(BeanType<T> definitionToBeReplaced,
                                             Class<T> replacedBeanType,
                                             AnnotationClassValue<?> qualifier) {
        @SuppressWarnings("unchecked") final Class<? extends Annotation> qualifierClass =
                (Class<? extends Annotation>) qualifier.getType().orElse(null);
        if (qualifierClass != null && !qualifierClass.isAssignableFrom(Annotation.class)) {
            return Qualifiers.<T>byStereotype(qualifierClass).qualify(replacedBeanType, Stream.of(definitionToBeReplaced))
                .isPresent();
        } else {
            throw new ConfigurationException(String.format("Default qualifier value was used while replacing %s", replacedBeanType));
        }
    }

    private <T> boolean qualifiedByNamed(BeanType<T> definitionToBeReplaced, Class replacedBeanType, String named) {
        return Qualifiers.<T>byName(named).qualify(replacedBeanType, Stream.of(definitionToBeReplaced))
            .isPresent();
    }

    private <T> Class<T> getCanonicalBeanType(BeanType<T> beanType) {
        if (beanType instanceof AdvisedBeanType) {
            return (Class<T>) ((AdvisedBeanType<T>) beanType).getInterceptedType();
        } else if (beanType instanceof ProxyBeanDefinition) {
            return ((ProxyBeanDefinition<T>) beanType).getTargetType();
        } else {
            AnnotationMetadata annotationMetadata = beanType.getAnnotationMetadata();
            Class<T> bt = beanType.getBeanType();
            if (annotationMetadata.hasStereotype(INTRODUCTION_TYPE)) {
                Class<? super T> superclass = bt.getSuperclass();
                if (superclass == Object.class || superclass == null) {
                    // interface introduction
                    return bt;
                } else {
                    // abstract class introduction
                    return (Class<T>) superclass;
                }
            } else if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_AROUND)) {
                Class<? super T> superclass = bt.getSuperclass();
                if (superclass != null) {
                    return (Class<T>) superclass;
                } else {
                    return bt;
                }
            }
            return bt;
        }
    }

    private <T> boolean checkIfTypeMatches(BeanType<T> definitionToBeReplaced,
                                                            AnnotationMetadata annotationMetadata,
                                                            Class replacingCandidate) {
        Class<T> bt;

        if (definitionToBeReplaced instanceof ProxyBeanDefinition) {
            bt = ((ProxyBeanDefinition<T>) definitionToBeReplaced).getTargetType();
        } else if (definitionToBeReplaced instanceof AdvisedBeanType) {
            //noinspection unchecked
            bt = (Class<T>) ((AdvisedBeanType<T>) definitionToBeReplaced).getInterceptedType();
        } else {
            bt = definitionToBeReplaced.getBeanType();
            if (annotationMetadata.hasStereotype(INTRODUCTION_TYPE)) {
                Class<? super T> superclass = bt.getSuperclass();
                if (superclass == Object.class) {
                    // interface introduction
                    return replacingCandidate.isAssignableFrom(bt);
                } else {
                    // abstract class introduction
                    return replacingCandidate == superclass;
                }
            }
            if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_AROUND)) {
                Class<? super T> superclass = bt.getSuperclass();
                return replacingCandidate == superclass || replacingCandidate == bt;
            }
        }

        if (annotationMetadata.hasAnnotation(DefaultImplementation.class)) {
            Optional<Class> defaultImpl = annotationMetadata.classValue(DefaultImplementation.class);
            if (!defaultImpl.isPresent()) {
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

    private <T> void doInject(BeanResolutionContext resolutionContext, T instance, BeanDefinition definition) {
        definition.inject(resolutionContext, this, instance);
        if (definition instanceof InitializingBeanDefinition) {
            ((InitializingBeanDefinition) definition).initialize(resolutionContext, this, instance);
        }
    }

    private void loadContextScopeBean(BeanDefinitionReference contextScopeBean, Consumer<BeanDefinition> beanDefinitionConsumer) {
        if (contextScopeBean.isEnabled(this)) {
            BeanDefinition beanDefinition = contextScopeBean.load(this);
            try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
                if (beanDefinition.isEnabled(this, resolutionContext)) {
                    beanDefinitionConsumer.accept(beanDefinition);
                }
            }
        }
    }

    private void loadContextScopeBean(BeanDefinition beanDefinition) {
        if (beanDefinition.isIterable() || beanDefinition.hasStereotype(ConfigurationReader.class.getName())) {
            Collection<BeanDefinition> beanCandidates = (Collection<BeanDefinition>) transformIterables(null, Collections.singleton(beanDefinition), true);
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

        Optional<BeanDefinition<T>> concreteCandidate = findBeanDefinition(beanType, qualifier);

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
            throw new NoSuchBeanException(beanType, qualifier);
        }
        return registration;
    }

    @Nullable
    private <T> BeanRegistration<T> provideInjectionPoint(BeanResolutionContext resolutionContext,
                                                          Argument<T> beanType,
                                                          Qualifier<T> qualifier,
                                                          boolean throwNoSuchBean) {
        final BeanResolutionContext.Path path = resolutionContext != null ? resolutionContext.getPath() : null;
        BeanResolutionContext.Segment<?> injectionPointSegment = null;
        if (CollectionUtils.isNotEmpty(path)) {
            final Iterator<BeanResolutionContext.Segment<?>> i = path.iterator();
            injectionPointSegment = i.next();
            BeanResolutionContext.Segment<?> segment = null;
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
            throw new NoSuchBeanException(beanType, qualifier);
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

        if (isProxy && isScopedProxyDefinition && (qualifier == null || !qualifier.contains(PROXY_TARGET_QUALIFIER))) {
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
            BeanResolutionContext.Segment<?> currentSegment = resolutionContext
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Optional<BeanDefinition<T>> findConcreteCandidate(@Nullable BeanResolutionContext resolutionContext,
                                                                  @NonNull Argument<T> beanType,
                                                                  @Nullable Qualifier<T> qualifier,
                                                                  boolean throwNonUnique) {
        if (beanType.getType() == Object.class && qualifier == null) {
            return Optional.empty();
        }
        BeanCandidateKey bk = new BeanCandidateKey(beanType, qualifier, throwNonUnique);
        Optional beanDefinition = beanConcreteCandidateCache.get(bk);
        //noinspection OptionalAssignedToNull
        if (beanDefinition == null) {
            beanDefinition = findConcreteCandidateNoCache(
                    resolutionContext,
                    beanType,
                    qualifier,
                    throwNonUnique,
                    true
            );
            beanConcreteCandidateCache.put(bk, beanDefinition);
        }
        return beanDefinition;
    }

    private <T> Optional<BeanDefinition<T>> findConcreteCandidateNoCache(@Nullable BeanResolutionContext resolutionContext,
                                                                         @NonNull Argument<T> beanType,
                                                                         @Nullable Qualifier<T> qualifier,
                                                                         boolean throwNonUnique,
                                                                         boolean filterProxied) {

        Predicate<BeanDefinition<T>> predicate = new Predicate<BeanDefinition<T>>() {
            @Override
            public boolean test(BeanDefinition<T> candidate) {
                if (candidate.isAbstract()) {
                    return false;
                }
                if (qualifier != null) {
                    if (candidate instanceof NoInjectionBeanDefinition) {
                        NoInjectionBeanDefinition noInjectionBeanDefinition = (NoInjectionBeanDefinition) candidate;
                        return qualifier.contains(noInjectionBeanDefinition.getQualifier());
                    }
                }
                return true;

            }
        };

        Collection<BeanDefinition<T>> candidates = new ArrayList<>(findBeanCandidates(resolutionContext, beanType, filterProxied, predicate));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        filterProxiedTypes(candidates, filterProxied, false, predicate);

        int size = candidates.size();
        BeanDefinition<T> definition = null;
        if (size > 0) {
            if (qualifier != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Qualifying bean [{}] for qualifier: {} ", beanType.getName(), qualifier);
                }

                Stream<BeanDefinition<T>> candidateStream = candidates.stream().filter(c -> {
                    if (!c.isAbstract()) {
                        if (c instanceof NoInjectionBeanDefinition) {
                            NoInjectionBeanDefinition noInjectionBeanDefinition = (NoInjectionBeanDefinition) c;
                            return qualifier.contains(noInjectionBeanDefinition.getQualifier());
                        }
                        return true;
                    }
                    return false;
                });

                Stream<BeanDefinition<T>> qualified = qualifier.reduce(beanType.getType(), candidateStream);
                List<BeanDefinition<T>> beanDefinitionList = qualified.collect(Collectors.toList());
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
                            qualifier,
                            throwNonUnique,
                            candidates
                    );
                }
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

    private <T> void filterProxiedTypes(Collection<BeanDefinition<T>> candidates, boolean filterProxied, boolean filterDelegates, Predicate<BeanDefinition<T>> predicate) {
        int count = candidates.size();
        Set<Class> proxiedTypes = new HashSet<>(count);
        Iterator<BeanDefinition<T>> i = candidates.iterator();
        Collection<BeanDefinition<T>> delegates = filterDelegates ? new ArrayList<>(count) : Collections.emptyList();
        while (i.hasNext()) {
            BeanDefinition<T> candidate = i.next();
            if (candidate instanceof ProxyBeanDefinition) {
                if (filterProxied) {
                    proxiedTypes.add(((ProxyBeanDefinition) candidate).getTargetDefinitionType());
                } else {
                    proxiedTypes.add(candidate.getClass());
                }
            } else if (candidate instanceof BeanDefinitionDelegate) {
                BeanDefinition<T> delegate = ((BeanDefinitionDelegate<T>) candidate).getDelegate();
                if (filterDelegates) {
                    i.remove();

                    if (!delegates.contains(delegate) && (predicate == null || predicate.test(delegate))) {
                        delegates.add(delegate);
                    }
                } else if (filterProxied && delegate instanceof ProxyBeanDefinition) {
                    proxiedTypes.add(((ProxyBeanDefinition) delegate).getTargetDefinitionType());
                }
            }
        }
        if (filterDelegates) {
            candidates.addAll(delegates);
        }
        if (!proxiedTypes.isEmpty()) {
            candidates.removeIf(candidate -> {
                if (candidate instanceof BeanDefinitionDelegate) {
                    return proxiedTypes.contains(((BeanDefinitionDelegate<T>) candidate).getDelegate().getClass());
                } else {
                    return proxiedTypes.contains(candidate.getClass());
                }
            });
        }
    }

    private <T> BeanDefinition<T> lastChanceResolve(Argument<T> beanType,
                                                    Qualifier<T> qualifier,
                                                    boolean throwNonUnique,
                                                    Collection<BeanDefinition<T>> candidates) {
        final Class<T> beanClass = beanType.getType();

        if (candidates.size() > 1) {
            List<BeanDefinition<T>> primary = candidates.stream()
                    .filter(BeanDefinition::isPrimary)
                    .collect(Collectors.toList());
            if (!primary.isEmpty()) {
                candidates = primary;
            }
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        BeanDefinition<T> definition = null;
        candidates = candidates.stream().filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class)).collect(Collectors.toList());
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
        List<BeanDefinitionReference> contextScopeBeans = new ArrayList<>(20);
        List<BeanDefinitionReference> processedBeans = new ArrayList<>(10);
        List<BeanDefinitionReference> parallelBeans = new ArrayList<>(10);

        List<BeanDefinitionReference> beanDefinitionReferences = resolveBeanDefinitionReferences();
        beanDefinitionsClasses.addAll(beanDefinitionReferences);

        Set<BeanConfiguration> configurationsDisabled = new HashSet<>();
        for (BeanConfiguration bc : beanConfigurations.values()) {
            if (!bc.isEnabled(this)) {
                configurationsDisabled.add(bc);
            }
        }

        reference:
        for (BeanDefinitionReference beanDefinitionReference : beanDefinitionReferences) {
            for (BeanConfiguration disableConfiguration : configurationsDisabled) {
                if (disableConfiguration.isWithin(beanDefinitionReference)) {
                    beanDefinitionsClasses.remove(beanDefinitionReference);
                    continue reference;
                }
            }
            final AnnotationMetadata annotationMetadata = beanDefinitionReference.getAnnotationMetadata();
            Class[] indexes = annotationMetadata.classValues(INDEXES_TYPE);
            if (indexes.length > 0) {
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < indexes.length; i++) {
                    Class indexedType = indexes[i];
                    resolveTypeIndex(indexedType).add(beanDefinitionReference);
                }
            } else {
                if (annotationMetadata.hasStereotype(ADAPTER_TYPE)) {
                    final Class aClass = annotationMetadata.classValue(ADAPTER_TYPE, AnnotationMetadata.VALUE_MEMBER).orElse(null);
                    if (indexedTypes.contains(aClass)) {
                        resolveTypeIndex(aClass).add(beanDefinitionReference);
                    }
                }
            }
            if (isEagerInit(beanDefinitionReference)) {
                contextScopeBeans.add(beanDefinitionReference);
            } else if (annotationMetadata.hasDeclaredStereotype(PARALLEL_TYPE)) {
                parallelBeans.add(beanDefinitionReference);
            }

            if (beanDefinitionReference.requiresMethodProcessing()) {
                processedBeans.add(beanDefinitionReference);
            }

        }

        this.beanDefinitionReferences = null;
        this.beanConfigurationsList = null;

        initializeEventListeners();
        initializeContext(contextScopeBeans, processedBeans, parallelBeans);
    }

    private boolean isEagerInit(BeanDefinitionReference beanDefinitionReference) {
        return beanDefinitionReference.isContextScope() ||
                (eagerInitSingletons && beanDefinitionReference.isSingleton()) ||
                (eagerInitStereotypesPresent && beanDefinitionReference.getAnnotationMetadata().hasDeclaredStereotype(eagerInitStereotypes));
    }

    @NonNull
    private Collection<BeanDefinitionReference> resolveTypeIndex(Class<?> indexedType) {
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
        Stream<BeanDefinition<T>> candidateStream = applyBeanResolutionFilters(resolutionContext, beanDefinitions.stream());
        if (qualifier != null) {
            candidateStream = qualifier.reduce(beanType.getType(), candidateStream);
        }
        beanDefinitions = candidateStream.collect(Collectors.toList());

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
        Collection<BeanRegistration<T>> result = beansOfTypeList;
        if (beansOfTypeList != Collections.EMPTY_SET) {
            Stream<BeanRegistration<T>> stream = beansOfTypeList.stream();
            if (Ordered.class.isAssignableFrom(beanType.getType())) {
                result = stream
                        .sorted(OrderUtil.COMPARATOR)
                        .collect(StreamUtils.toImmutableCollection());
            } else {
                if (hasOrderAnnotation) {
                    stream = stream.sorted(BEAN_REGISTRATION_COMPARATOR);
                }
                result = stream.collect(StreamUtils.toImmutableCollection());
            }
        }
        return result;
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
        BeanResolutionContext.Segment<?> segment = resolutionContext != null ? resolutionContext.getPath().peek() : null;
        if (segment instanceof AbstractBeanResolutionContext.ConstructorSegment || segment instanceof AbstractBeanResolutionContext.MethodSegment) {
            BeanDefinition<?> declaringBean = segment.getDeclaringType();
            // if the currently injected segment is a constructor argument and the type to be constructed is the
            // same as the candidate, then filter out the candidate to avoid a circular injection problem
            candidateStream = candidateStream.filter(c -> {
                if (c.equals(declaringBean)) {
                    return false;
                } else if (declaringBean instanceof ProxyBeanDefinition) {
                    return !((ProxyBeanDefinition) declaringBean).getTargetDefinitionType().equals(c.getClass());
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
            if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getTypeName(), e.getMessage());
            }
        }

        if (beanRegistration != null) {
            if (candidate.isContainerType()) {
                Object container = beanRegistration.bean;
                if (container instanceof Object[]) {
                    container = Arrays.asList((Object[]) container);
                }
                if (container instanceof Iterable) {
                    Iterable<Object> iterable = (Iterable<Object>) container;
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
        Set<Class> satisfied = new HashSet<>();

        // Optimization for types which we know are already unsatisified
        // in a single iteration, allowing to skip the loop on unsorted elements
        Set<Class> unsatisfied = new HashSet<>();

        //loop until all items have been sorted
        while (!unsorted.isEmpty()) {
            boolean acyclic = false;

            unsatisfied.clear();
            Iterator<BeanRegistration> i = unsorted.iterator();
            while (i.hasNext()) {
                BeanRegistration bean = i.next();
                boolean found = false;

                //determine if any components are in the unsorted list
                Collection<Class> components = bean.getBeanDefinition().getRequiredComponents();
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

                //none of the required components are in the unsorted list
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
                return ConversionService.SHARED.convert(o, type);
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
        public ExecutableMethod<?, R> getExecutableMethod() {
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
    private static final class ObjectExecutionHandle<T, R> extends AbstractExecutionHandle<T, R> {

        private final T target;

        /**
         * @param target The target type
         * @param method The method
         */
        ObjectExecutionHandle(T target, ExecutableMethod<T, R> method) {
            super(method);
            this.target = target;
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
        BeanKey(Class<T> beanType, Qualifier<T> qualifier, @Nullable Class... typeArguments) {
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
            if (qualifier instanceof Named) {
                return ((Named) qualifier).getName();
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

    private final class ScanningBeanResolutionContext extends SingletonBeanResolutionContext {

        private final HashMap<BeanDefinition<?>, Argument<?>> beanCreationTargets;
        private final Map<BeanDefinition<?>, List<List<Argument<?>>>> foundTargets = new HashMap<>();

        private ScanningBeanResolutionContext(BeanDefinition<?> beanDefinition, HashMap<BeanDefinition<?>, Argument<?>> beanCreationTargets) {
            super(beanDefinition);
            this.beanCreationTargets = beanCreationTargets;
        }

        private List<Argument<?>> getHierarchy() {
            List<Argument<?>> hierarchy = new ArrayList<>(path.size());
            for (Iterator<BeanResolutionContext.Segment<?>> it = path.descendingIterator(); it.hasNext();) {
                BeanResolutionContext.Segment<?> segment = it.next();
                hierarchy.add(segment.getArgument());
            }
            return hierarchy;
        }

        @Override
        protected void onNewSegment(Segment<?> segment) {
            Argument<?> argument = segment.getArgument();
            if (argument.isContainerType()) {
                argument = argument.getFirstTypeVariable().orElse(null);
                if (argument == null) {
                    return;
                }
            }
            if (argument.isProvider()) {
                return;
            }
            for (Map.Entry<BeanDefinition<?>, Argument<?>> entry : beanCreationTargets.entrySet()) {
                if (argument.isAssignableFrom(entry.getValue())) {
                    foundTargets.computeIfAbsent(entry.getKey(), bd -> new ArrayList<>(5))
                            .add(getHierarchy());
                }
            }
        }

        @SuppressWarnings("java:S1452")
        Map<BeanDefinition<?>, List<List<Argument<?>>>> getFoundTargets() {
            return foundTargets;
        }
    }
}
