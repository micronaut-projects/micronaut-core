/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;
import io.micronaut.context.exceptions.*;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.context.scope.CustomScopeRegistry;
import io.micronaut.core.annotation.*;
import io.micronaut.core.async.subscriber.Completable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ClassLoadingReporter;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.*;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.inject.*;
import io.micronaut.inject.qualifiers.Qualified;
import io.micronaut.inject.qualifiers.Qualifiers;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The default context implementations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("MagicNumber")
public class DefaultBeanContext implements BeanContext {

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultBeanContext.class);
    protected static final Logger LOG_LIFECYCLE = LoggerFactory.getLogger(DefaultBeanContext.class.getPackage().getName() + ".lifecycle");
    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger(ApplicationEventPublisher.class);
    private static final Qualifier PROXY_TARGET_QUALIFIER = new Qualifier<Object>() {
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
    private static final String AROUND_TYPE = "io.micronaut.aop.Around";
    private static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction";
    private static final String NAMED_MEMBER = "named";

    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean initializing = new AtomicBoolean(false);
    protected final AtomicBoolean terminating = new AtomicBoolean(false);

    final Map<BeanKey, BeanRegistration> singletonObjects = new ConcurrentHashMap<>(100);
    final Map<BeanKey, Object> scopedProxies = new ConcurrentHashMap<>(20);
    Collection<BeanRegistration<BeanInitializedEventListener>> beanInitializedEventListeners;

    private final Collection<BeanDefinitionReference> beanDefinitionsClasses = new ConcurrentLinkedQueue<>();
    private final Map<String, BeanConfiguration> beanConfigurations = new ConcurrentHashMap<>(4);
    private final Map<BeanKey, Boolean> containsBeanCache = new ConcurrentHashMap<>(30);

    private final Map<BeanKey, Collection<Object>> initializedObjectsByType = new ConcurrentLinkedHashMap.Builder<BeanKey, Collection<Object>>().maximumWeightedCapacity(30).build();
    private final Map<BeanKey, Optional<BeanDefinition>> beanConcreteCandidateCache = new ConcurrentLinkedHashMap.Builder<BeanKey, Optional<BeanDefinition>>().maximumWeightedCapacity(30).build();
    private final Map<Class, Collection<BeanDefinition>> beanCandidateCache = new ConcurrentLinkedHashMap.Builder<Class, Collection<BeanDefinition>>().maximumWeightedCapacity(30).build();
    private final Map<Class, Collection<BeanDefinitionReference>> beanIndex = new ConcurrentHashMap<>(12);

    private final ClassLoader classLoader;
    private final Set<Class> thisInterfaces = ReflectionUtils.getAllInterfaces(getClass());
    private final Set<Class> indexedTypes = CollectionUtils.setOf(
            TypeConverter.class,
            TypeConverterRegistrar.class,
            ApplicationEventListener.class,
            BeanCreatedEventListener.class,
            BeanInitializedEventListener.class
    );
    private final CustomScopeRegistry customScopeRegistry;
    private Collection<BeanRegistration<BeanCreatedEventListener>> beanCreationEventListeners;

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
    public DefaultBeanContext(@Nonnull ClassLoader classLoader) {
        this(new BeanContextConfiguration() {
            @Nonnull
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
    public DefaultBeanContext(@Nonnull ClassPathResourceLoader resourceLoader) {
        this(new BeanContextConfiguration() {
            @Nonnull
            @Override
            public ClassLoader getClassLoader() {
                ArgumentUtils.requireNonNull("resourceLoader", resourceLoader);
                return resourceLoader.getClassLoader();
            }
        });
    }

    /**
     * Creates a new bean context with the given configuration.
     * @param contextConfiguration The context configuration
     */
    public DefaultBeanContext(@Nonnull BeanContextConfiguration contextConfiguration) {
        ArgumentUtils.requireNonNull("contextConfiguration", contextConfiguration);
        // enable classloader logging
        System.setProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING, "true");
        this.classLoader = contextConfiguration.getClassLoader();
        this.customScopeRegistry = new DefaultCustomScopeRegistry(this, classLoader);

        // startup optimization.. index Jackson modules
        ClassUtils.forName("com.fasterxml.jackson.databind.Module", classLoader).ifPresent(indexedTypes::add);
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
                readAllBeanConfigurations();
                readAllBeanDefinitionClasses();
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
            // start thread for parallel beans
            processParallelBeans();
            running.set(true);
            initializing.set(false);
        }
        return this;
    }


    /**
     * The close method will shut down the context calling {@link javax.annotation.PreDestroy} hooks on loaded
     * singletons.
     */
    @Override
    public synchronized BeanContext stop() {
        if (terminating.compareAndSet(false, true)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping BeanContext");
            }
            publishEvent(new ShutdownEvent(this));

            // need to sort registered singletons so that beans with that require other beans appear first
            List<BeanRegistration> objects = topologicalSort(singletonObjects.values());

            Set<Integer> processed = new HashSet<>();
            for (BeanRegistration beanRegistration : objects) {
                BeanDefinition def = beanRegistration.beanDefinition;
                Object bean = beanRegistration.bean;
                int sysId = System.identityHashCode(bean);
                if (processed.contains(sysId)) {
                    continue;
                }

                if (LOG_LIFECYCLE.isDebugEnabled()) {
                    LOG_LIFECYCLE.debug("Destroying bean [{}] with identifier [{}]", bean, beanRegistration.identifier);
                }

                processed.add(sysId);
                if (def instanceof DisposableBeanDefinition) {
                    try {
                        //noinspection unchecked
                        ((DisposableBeanDefinition) def).dispose(this, bean);
                    } catch (Throwable e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error disposing of bean registration [" + def.getName() + "]: " + e.getMessage(), e);
                        }
                    }
                }
                if (def instanceof Closeable) {
                    try {
                        ((Closeable) def).close();
                    } catch (Throwable e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error disposing of bean registration [" + def.getName() + "]: " + e.getMessage(), e);
                        }
                    }
                }
                if (bean instanceof LifeCycle) {
                    ((LifeCycle) bean).stop();
                }
            }

            terminating.set(false);
            running.set(false);
            ClassLoadingReporter.finish();
        }
        return this;
    }

    @Override
    @Nonnull
    public AnnotationMetadata resolveMetadata(Class<?> type) {
        if (type == null) {
            return AnnotationMetadata.EMPTY_METADATA;
        } else {
            Optional<? extends BeanDefinition<?>> candidate = findConcreteCandidate(type, null, false, false);
            return candidate.map(AnnotationMetadataProvider::getAnnotationMetadata).orElse(AnnotationMetadata.EMPTY_METADATA);
        }
    }

    @SuppressWarnings({"SuspiciousMethodCalls", "unchecked"})
    @Override
    public <T> Optional<T> refreshBean(BeanIdentifier identifier) {
        if (identifier != null) {
            BeanRegistration beanRegistration = singletonObjects.get(identifier);
            if (beanRegistration != null) {
                BeanDefinition definition = beanRegistration.getBeanDefinition();
                return Optional.of((T) definition.inject(this, beanRegistration.getBean()));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<BeanRegistration<?>> getActiveBeanRegistrations(Qualifier<?> qualifier) {
        if (qualifier == null) {
            return Collections.emptyList();
        }
        List result = singletonObjects
                .values()
                .stream()
                .filter(registration -> {
                    BeanDefinition beanDefinition = registration.beanDefinition;
                    return qualifier.reduce(beanDefinition.getBeanType(), Stream.of(beanDefinition)).findFirst().isPresent();
                })
                .collect(Collectors.toList());
        return (Collection<BeanRegistration<?>>) result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Collection<BeanRegistration<T>> getActiveBeanRegistrations(Class<T> beanType) {
        if (beanType == null) {
            return Collections.emptyList();
        }
        List result = singletonObjects
                .values()
                .stream()
                .filter(registration -> {
                    BeanDefinition beanDefinition = registration.beanDefinition;
                    return beanType.isAssignableFrom(beanDefinition.getBeanType());
                })
                .collect(Collectors.toList());
        return (Collection<BeanRegistration<T>>) result;
    }

    @Override
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(Class<T> beanType) {
        if (beanType == null) {
            return Collections.emptyList();
        }
        // initialize the beans
        getBeansOfType(beanType);
        return getActiveBeanRegistrations(beanType);
    }

    @Override
    public <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean) {
        for (BeanRegistration beanRegistration : singletonObjects.values()) {
            if (bean == beanRegistration.getBean()) {
                return Optional.of(beanRegistration);
            }
        }

        Collection<CustomScope> scopes = getBeansOfType(CustomScope.class);
        for (CustomScope<?> scope : scopes) {
            Optional<BeanRegistration<T>> beanRegistration = scope.findBeanRegistration(bean);
            if (beanRegistration.isPresent()) {
                return beanRegistration;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Optional<MethodExecutionHandle<T, R>> findExecutionHandle(Class<T> beanType, String method, Class... arguments) {
        return findExecutionHandle(beanType, null, method, arguments);
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
    public <T> BeanContext registerSingleton(@Nonnull Class<T> type, @Nonnull T singleton, Qualifier<T> qualifier, boolean inject) {
        ArgumentUtils.requireNonNull("type", type);
        ArgumentUtils.requireNonNull("singleton", singleton);
        BeanKey<T> beanKey = new BeanKey<>(type, qualifier);
        synchronized (singletonObjects) {

            initializedObjectsByType.clear();
            beanCandidateCache.remove(type);
            BeanDefinition<T> beanDefinition = inject ? findConcreteCandidate(type, qualifier, false, false).orElse(null) : null;
            if (beanDefinition != null && beanDefinition.getBeanType().isInstance(singleton)) {
                doInject(new DefaultBeanResolutionContext(this, beanDefinition), singleton, beanDefinition);
                singletonObjects.put(beanKey, new BeanRegistration<>(beanKey, beanDefinition, singleton));
                BeanKey concreteKey = new BeanKey(singleton.getClass(), qualifier);
                singletonObjects.put(concreteKey, new BeanRegistration<>(concreteKey, beanDefinition, singleton));
            } else {
                NoInjectionBeanDefinition<T> dynamicRegistration = new NoInjectionBeanDefinition<>(singleton.getClass(), qualifier);
                if (qualifier instanceof Named) {
                    final BeanDefinitionDelegate<T> delegate = BeanDefinitionDelegate.create(dynamicRegistration);
                    delegate.put(Named.class.getName(), ((Named) qualifier).getName());
                    beanDefinition = delegate;
                } else {
                    beanDefinition = dynamicRegistration;
                }
                beanDefinitionsClasses.add(dynamicRegistration);
                singletonObjects.put(beanKey, new BeanRegistration<>(beanKey, dynamicRegistration, singleton));
                BeanKey concreteKey = new BeanKey(singleton.getClass(), qualifier);
                singletonObjects.put(concreteKey, new BeanRegistration<>(concreteKey, dynamicRegistration, singleton));
                final Optional<Class> indexedType = indexedTypes.stream().filter(t -> t.isAssignableFrom(type) || t == type).findFirst();
                if (indexedType.isPresent()) {
                    final Collection<BeanDefinitionReference> indexed = resolveTypeIndex(indexedType.get());
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
                }
            }

        }
        return this;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
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
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType, Qualifier<T> qualifier) {
        if (Object.class == beanType) {
            // optimization for object resolve
            return Optional.empty();
        }

        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);
        @SuppressWarnings("unchecked") BeanRegistration<T> reg = singletonObjects.get(beanKey);
        if (reg != null) {
            return Optional.of(reg.getBeanDefinition());
        }
        Collection<BeanDefinition<T>> beanCandidates = new ArrayList<>(findBeanCandidatesInternal(beanType));
        if (qualifier != null) {
            beanCandidates = qualifier.reduce(beanType, beanCandidates.stream()).collect(Collectors.toList());
        }
        filterProxiedTypes(beanCandidates, true, true);
        if (beanCandidates.isEmpty()) {
            return Optional.empty();
        } else {
            if (beanCandidates.size() == 1) {
                return Optional.of(beanCandidates.iterator().next());
            } else {
                return findConcreteCandidate(beanType, null, false, true);
            }
        }
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType) {
        Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(beanType);
        return Collections.unmodifiableCollection(candidates);
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType, Qualifier<T> qualifier) {
        Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(beanType);
        if (qualifier != null) {
            candidates = qualifier.reduce(beanType, new ArrayList<>(candidates).stream()).collect(Collectors.toList());
        }
        return Collections.unmodifiableCollection(candidates);
    }

    @Override
    public <T> boolean containsBean(@Nonnull Class<T> beanType, Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);
        if (containsBeanCache.containsKey(beanKey)) {
            return containsBeanCache.get(beanKey);
        } else {
            boolean result = singletonObjects.containsKey(beanKey) ||
                    isCandidatePresent(beanType, qualifier);

            containsBeanCache.put(beanKey, result);
            return result;
        }
    }

    @Override
    public <T> T getBean(Class<T> beanType, Qualifier<T> qualifier) {
        return getBeanInternal(null, beanType, qualifier, true, true);
    }

    @Override
    public <T> T getBean(Class<T> beanType) {
        return getBeanInternal(null, beanType, null, true, true);
    }

    @Override
    public <T> Optional<T> findBean(Class<T> beanType, Qualifier<T> qualifier) {
        return findBean(null, beanType, qualifier);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType) {
        return getBeansOfType(null, beanType);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType, Qualifier<T> qualifier) {
        return getBeansOfTypeInternal(null, beanType, qualifier);
    }

    @Override
    public <T> Stream<T> streamOfType(Class<T> beanType, Qualifier<T> qualifier) {
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
        return getBeansOfTypeInternal(resolutionContext, beanType, qualifier).stream();
    }

    @Override
    public @Nonnull <T> T inject(@Nonnull T instance) {
        Objects.requireNonNull(instance, "Instance cannot be null");

        Collection<BeanDefinition> candidates = findBeanCandidatesForInstance(instance);
        if (candidates.size() == 1) {
            BeanDefinition<T> beanDefinition = candidates.stream().findFirst().get();
            final DefaultBeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(this, beanDefinition);
            final BeanKey<T> beanKey = new BeanKey<>(beanDefinition.getBeanType(), null);
            resolutionContext.addInFlightBean(
                    beanKey,
                    instance
            );
            doInject(
                    resolutionContext,
                    instance,
                    beanDefinition
            );

        } else if (!candidates.isEmpty()) {
            final Iterator iterator = candidates.iterator();
            throw new NonUniqueBeanException(instance.getClass(), iterator);
        }
        return instance;
    }

    @Override
    public @Nonnull <T> T createBean(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return createBean(null, beanType, qualifier);
    }

    @Override
    public @Nonnull <T> T createBean(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Map<String, Object> argumentValues) {
        ArgumentUtils.requireNonNull("beanType", beanType);

        Optional<BeanDefinition<T>> candidate = findConcreteCandidate(beanType, qualifier, true, false);
        if (candidate.isPresent()) {
            T createdBean = doCreateBean(new DefaultBeanResolutionContext(this, candidate.get()), candidate.get(), qualifier, false, argumentValues);
            if (createdBean == null) {
                throw new NoSuchBeanException(beanType);
            }
            return createdBean;
        }
        throw new NoSuchBeanException(beanType);
    }

    @Override
    public @Nonnull <T> T createBean(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Object... args) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        Optional<BeanDefinition<T>> candidate = findConcreteCandidate(beanType, qualifier, true, false);
        if (candidate.isPresent()) {
            BeanDefinition<T> definition = candidate.get();
            DefaultBeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(this, definition);
            return doCreateBean(resolutionContext, definition, beanType, qualifier, args);
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
    protected @Nonnull <T> T doCreateBean(@Nonnull BeanResolutionContext resolutionContext,
                                          @Nonnull BeanDefinition<T> definition,
                                          @Nonnull Class<T> beanType,
                                          @Nullable Qualifier<T> qualifier,
                                          @Nullable Object... args) {
        Map<String, Object> argumentValues;
        if (definition instanceof ParametrizedBeanFactory) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Creating bean for parameters: {}", ArrayUtils.toString(args));
            }
            Argument[] requiredArguments = ((ParametrizedBeanFactory) definition).getRequiredArguments();
            argumentValues = new LinkedHashMap<>(requiredArguments.length);
            BeanResolutionContext.Path path = resolutionContext.getPath();
            for (int i = 0; i < requiredArguments.length; i++) {
                Argument<?> requiredArgument = requiredArguments[i];
                try {
                    path.pushConstructorResolve(
                            definition, requiredArgument
                    );
                    if (args.length > i) {
                        Object val = args[i];
                        if (val != null) {
                            argumentValues.put(requiredArgument.getName(), ConversionService.SHARED.convert(val, requiredArgument).orElseThrow(() ->
                                    new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. Cannot convert object [" + val + "] to required type: " + requiredArgument.getType())
                            ));
                        } else {
                            if (!requiredArgument.isDeclaredNullable()) {
                                throw new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. Argument cannot be null");
                            }
                        }
                    } else {
                        // attempt resolve from context
                        Optional<?> existingBean = findBean(resolutionContext, requiredArgument.getType(), null);
                        if (existingBean.isPresent()) {
                            argumentValues.put(requiredArgument.getName(), existingBean.get());
                        } else {
                            if (!requiredArgument.isDeclaredNullable()) {
                                throw new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. No bean found for type: " + requiredArgument.getType());
                            }
                        }
                    }
                } finally {
                    path.pop();
                }
            }
        } else {
            argumentValues = Collections.emptyMap();
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Computed bean argument values: {}", argumentValues);
        }
        T createdBean = doCreateBean(resolutionContext, definition, qualifier, false, argumentValues);
        if (createdBean == null) {
            throw new NoSuchBeanException(beanType);
        }
        return createdBean;
    }

    @Override
    public @Nullable <T> T destroyBean(@Nonnull Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        T bean = null;
        BeanKey<T> beanKey = new BeanKey<>(beanType, null);

        synchronized (singletonObjects) {
            if (singletonObjects.containsKey(beanKey)) {
                @SuppressWarnings("unchecked") BeanRegistration<T> beanRegistration = singletonObjects.get(beanKey);
                bean = beanRegistration.bean;
                if (bean != null) {
                    if (LOG_LIFECYCLE.isDebugEnabled()) {
                        LOG_LIFECYCLE.debug("Destroying bean [{}] with identifier [{}]", bean, beanKey);
                    }

                    singletonObjects.remove(beanKey);
                    BeanKey<?> concreteKey = new BeanKey<>(bean.getClass(), null);
                    singletonObjects.remove(concreteKey);
                }
            }
        }

        if (bean != null) {
            Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(beanType, null, false, true);
            T finalBean = bean;
            concreteCandidate.ifPresent(definition -> {
                        if (definition instanceof DisposableBeanDefinition) {
                            ((DisposableBeanDefinition<T>) definition).dispose(this, finalBean);
                        }
                    }
            );
        }
        return bean;
    }

    /**
     * Find an active {@link javax.inject.Singleton} bean for the given definition and qualifier.
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
        return singletonObjects.get(new BeanKey(beanDefinition, qualifier));
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
    protected @Nonnull <T> T createBean(@Nullable BeanResolutionContext resolutionContext, @Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);

        Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(beanType, qualifier, true, false);
        if (concreteCandidate.isPresent()) {
            BeanDefinition<T> candidate = concreteCandidate.get();
            if (resolutionContext == null) {
                resolutionContext = new DefaultBeanResolutionContext(this, candidate);
            }
            T createBean = doCreateBean(resolutionContext, candidate, qualifier, false, null);
            if (createBean == null) {
                throw new NoSuchBeanException(beanType);
            }
            return createBean;
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
    protected @Nonnull <T> T inject(@Nonnull BeanResolutionContext resolutionContext, @Nullable BeanDefinition requestingBeanDefinition, @Nonnull T instance) {
        @SuppressWarnings("unchecked") Class<T> beanType = (Class<T>) instance.getClass();
        Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(beanType, null, false, true);
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
    protected @Nonnull <T> Collection<T> getBeansOfType(@Nullable BeanResolutionContext resolutionContext, @Nonnull Class<T> beanType) {
        return getBeansOfTypeInternal(resolutionContext, beanType, null);
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
    protected @Nonnull <T> Collection<T> getBeansOfType(
            @Nullable BeanResolutionContext resolutionContext,
            @Nonnull Class<T> beanType,
            @Nullable Qualifier<T> qualifier) {
        return getBeansOfTypeInternal(resolutionContext, beanType, qualifier);
    }

    /**
     * Get provided beans of the given type.
     *
     * @param resolutionContext The bean resolution context
     * @param beanType          The bean type
     * @param <T>               The bean type parameter
     * @return The found beans
     */
    protected @Nonnull <T> Provider<T> getBeanProvider(@Nullable BeanResolutionContext resolutionContext, @Nonnull Class<T> beanType) {
        return getBeanProvider(resolutionContext, beanType, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull <T> T getProxyTargetBean(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return getBeanForDefinition(new DefaultBeanResolutionContext(this, definition), beanType, proxyQualifier, true, definition);
    }

    @Override
    public @Nonnull <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(@Nonnull Class<T> beanType, @Nonnull String method, @Nonnull Class[] arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, null);
        return definition.findMethod(method, arguments);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nonnull <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        BeanKey key = new BeanKey(beanType, proxyQualifier);

        return (Optional) beanConcreteCandidateCache.computeIfAbsent(key, beanKey -> {
            BeanRegistration<T> beanRegistration = singletonObjects.get(beanKey);
            if (beanRegistration != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Resolved existing bean [{}] for type [{}] and qualifier [{}]", beanRegistration.bean, beanType, qualifier);
                }
                return Optional.of(beanRegistration.beanDefinition);
            }
            return findConcreteCandidateNoCache(
                    (Class) beanType,
                    proxyQualifier,
                    true,
                    false,
                    false
            );
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull Collection<BeanDefinition<?>> getBeanDefinitions(@Nullable Qualifier<Object> qualifier) {
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
            return (Collection<BeanDefinition<?>>) Collections.EMPTY_LIST;
        }
        if (CollectionUtils.isNotEmpty(candidates)) {
            filterProxiedTypes(candidates, true, true);
            filterReplacedBeans(candidates);
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull Collection<BeanDefinition<?>> getAllBeanDefinitions() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding all bean definitions");
        }

        if (!beanDefinitionsClasses.isEmpty()) {
            List collection = beanDefinitionsClasses
                    .stream()
                    .map(ref -> ref.load(this))
                    .filter(candidate -> candidate.isEnabled(this))
                    .collect(Collectors.toList());
            return (Collection<BeanDefinition<?>>) collection;
        }

        return (Collection<BeanDefinition<?>>) Collections.EMPTY_MAP;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nonnull Collection<BeanDefinitionReference<?>> getBeanDefinitionReferences() {
        if (!beanDefinitionsClasses.isEmpty()) {
            final List refs = beanDefinitionsClasses.stream().filter(ref -> ref.isEnabled(this))
                    .collect(Collectors.toList());

            return (Collection<BeanDefinitionReference<?>>) Collections.unmodifiableList(refs);
        }
        return Collections.emptyList();
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
    public @Nonnull <T> T getBean(@Nullable BeanResolutionContext resolutionContext, @Nonnull Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getBeanInternal(resolutionContext, beanType, null, true, true);
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
    public @Nonnull <T> T getBean(@Nullable BeanResolutionContext resolutionContext, @Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getBeanInternal(resolutionContext, beanType, qualifier, true, true);
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
    public @Nonnull <T> Optional<T> findBean(@Nullable BeanResolutionContext resolutionContext, @Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        // allow injection the bean context
        if (thisInterfaces.contains(beanType)) {
            return Optional.of((T) this);
        }

        T bean = getBeanInternal(resolutionContext, beanType, qualifier, true, false);
        if (bean == null) {
            return Optional.empty();
        } else {
            return Optional.of(bean);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void publishEvent(@Nonnull Object event) {
        //noinspection ConstantConditions
        if (event != null) {
            if (EVENT_LOGGER.isDebugEnabled()) {
                EVENT_LOGGER.debug("Publishing event: {}", event);
            }
            Collection<ApplicationEventListener> eventListeners = getBeansOfType(ApplicationEventListener.class, Qualifiers.byTypeArguments(event.getClass()));

            eventListeners = eventListeners.stream().sorted(OrderUtil.COMPARATOR).collect(Collectors.toList());

            if (!eventListeners.isEmpty()) {
                if (EVENT_LOGGER.isTraceEnabled()) {
                    EVENT_LOGGER.trace("Established event listeners {} for event: {}", eventListeners, event);
                }
                eventListeners
                        .forEach(listener -> {
                                    if (listener.supports(event)) {
                                        try {
                                            if (EVENT_LOGGER.isTraceEnabled()) {
                                                EVENT_LOGGER.trace("Invoking event listener [{}] for event: {}", listener, event);
                                            }
                                            listener.onApplicationEvent(event);
                                        } catch (ClassCastException ex) {
                                            String msg = ex.getMessage();
                                            if (msg == null || msg.startsWith(event.getClass().getName())) {
                                                if (EVENT_LOGGER.isDebugEnabled()) {
                                                    EVENT_LOGGER.debug("Incompatible listener for event: " + listener, ex);
                                                }
                                            } else {
                                                throw ex;
                                            }
                                        }
                                    }
                                }
                        );
            }
        }
    }

    @Override
    public @Nonnull <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        return getBeanDefinitions(beanType, qualifier)
                .stream()
                .filter(BeanDefinition::isProxy)
                .findFirst();
    }

    /**
     * Invalidates the bean caches.
     */
    protected void invalidateCaches() {
        beanCandidateCache.clear();
        initializedObjectsByType.clear();
    }

    /**
     * Get a bean provider.
     *
     * @param resolutionContext The bean resolution context
     * @param beanType          The bean type
     * @param qualifier         The qualifier
     * @param <T>               The bean type parameter
     * @return The bean provider
     */
    protected @Nonnull <T> Provider<T> getBeanProvider(@Nullable BeanResolutionContext resolutionContext, @Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        @SuppressWarnings("unchecked") BeanRegistration<T> beanRegistration = singletonObjects.get(new BeanKey(beanType, qualifier));
        if (beanRegistration != null) {
            return new ResolvedProvider<>(beanRegistration.bean);
        }

        Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(beanType, qualifier, true, false);
        if (concreteCandidate.isPresent()) {
            return new UnresolvedProvider<>(beanType, qualifier, this);
        } else {
            throw new NoSuchBeanException(beanType);
        }
    }

    /**
     * Resolves the {@link BeanDefinitionReference} class instances. Default implementation uses ServiceLoader pattern.
     *
     * @return The bean definition classes
     */
    protected @Nonnull List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        final SoftServiceLoader<BeanDefinitionReference> definitions = SoftServiceLoader.load(BeanDefinitionReference.class, classLoader);
        List<BeanDefinitionReference> list = new ArrayList<>(300);
        for (ServiceDefinition<BeanDefinitionReference> definition : definitions) {
            if (definition.isPresent()) {
                final BeanDefinitionReference ref = definition.load();
                list.add(ref);
            }
        }
        return list;
    }

    /**
     * Resolves the {@link BeanConfiguration} class instances. Default implementation uses ServiceLoader pattern.
     *
     * @return The bean definition classes
     */
    protected @Nonnull Iterable<BeanConfiguration> resolveBeanConfigurations() {
        final SoftServiceLoader<BeanConfiguration> definitions = SoftServiceLoader.load(BeanConfiguration.class, classLoader);
        List<BeanConfiguration> list = new ArrayList<>(20);
        for (ServiceDefinition<BeanConfiguration> definition : definitions) {
            if (definition.isPresent()) {
                list.add(definition.load());
            }
        }
        return list;
    }

    /**
     * Initialize the event listeners.
     */
    protected void initializeEventListeners() {
        this.beanCreationEventListeners = getBeanRegistrations(BeanCreatedEventListener.class);
        this.beanInitializedEventListeners = getBeanRegistrations(BeanInitializedEventListener.class);
    }

    /**
     * Initialize the context with the given {@link io.micronaut.context.annotation.Context} scope beans.
     *
     * @param contextScopeBeans The context scope beans
     * @param processedBeans    The beans that require {@link ExecutableMethodProcessor} handling
     */
    protected void initializeContext(
            @Nonnull List<BeanDefinitionReference> contextScopeBeans,
            @Nonnull List<BeanDefinitionReference> processedBeans) {

        if (CollectionUtils.isNotEmpty(contextScopeBeans)) {
            final Collection<BeanDefinition> contextBeans = new ArrayList<>();

            for (BeanDefinitionReference contextScopeBean : contextScopeBeans) {
                try {
                    loadContextScopeBean(contextScopeBean, contextBeans::add);
                } catch (Throwable e) {
                    throw new BeanInstantiationException("Bean definition [" + contextScopeBean.getName() + "] could not be loaded: " + e.getMessage(), e);
                }
            }
            filterProxiedTypes((Collection) contextBeans, true, false);
            filterReplacedBeans((Collection) contextBeans);

            for (BeanDefinition contextScopeDefinition: contextBeans) {
                try {
                    loadContextScopeBean(contextScopeDefinition);
                } catch (Throwable e) {
                    throw new BeanInstantiationException("Bean definition [" + contextScopeDefinition.getName() + "] could not be loaded: " + e.getMessage(), e);
                }
            }
            contextBeans.clear();
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
                                    .map((Function<ExecutableMethod<?, ?>, BeanDefinitionMethodReference<?, ?>>) executableMethod ->
                                            BeanDefinitionMethodReference.of((BeanDefinition) beanDefinition, executableMethod)
                                    )
                    );

            // group the method references by annotation type such that we have a map of Annotation -> MethodReference
            // ie. Class<Scheduled> -> @Scheduled void someAnnotation()
            Map<Class<? extends Annotation>, List<BeanDefinitionMethodReference<?, ?>>> byAnnotation = new HashMap<>();
            methodStream.collect(Collectors.toList()).forEach(reference -> {
                List<Class<? extends Annotation>> annotations = reference.getAnnotationTypesByStereotype(Executable.class);
                if (annotations.isEmpty()) {
                    throw new IllegalStateException("BeanDefinition.requiresMethodProcessing() returned true but method has no @Executable definition. This should never happen. Please report an issue.");
                } else {
                    annotations.forEach(annotation -> {
                        byAnnotation.compute(annotation, (ann, list) -> {
                            if (list == null) {
                                list = new ArrayList<>();
                            }
                            list.add(reference);
                            return list;
                        });
                    });
                }
            });

            // Find ExecutableMethodProcessor for each annotation and process the BeanDefinitionMethodReference
            for (Map.Entry<Class<? extends Annotation>, List<BeanDefinitionMethodReference<?, ?>>> entry : byAnnotation.entrySet()) {
                Class<? extends Annotation> annotationType = entry.getKey();
                streamOfType(ExecutableMethodProcessor.class, Qualifiers.byTypeArguments(annotationType))
                        .forEach(processor -> {
                            for (BeanDefinitionMethodReference<?, ?> method : entry.getValue()) {

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
                                                Boolean shutdownOnError = method.getValue(Parallel.class, "shutdownOnError", Boolean.class).orElse(true);
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

                            if (processor instanceof Completable) {
                                ((Completable) processor).onComplete();
                            }

                        });
            }
        }

        final Runnable runnable = () ->
                beanDefinitionsClasses.removeIf((BeanDefinitionReference beanDefinitionReference) ->
                        !beanDefinitionReference.isEnabled(this));
        if (ClassLoadingReporter.isReportingEnabled()) {
            // do this in a blocking manner so that reporting is immediately aware of missing classes
            runnable.run();
        } else {
            // proactively remove bean definitions that are not enabled
            new Thread(runnable).start();
        }

    }

    /**
     * Find bean candidates for the given type.
     *
     * @param beanType The bean type
     * @param filter   A bean definition to filter out
     * @param <T>      The bean generic type
     * @return The candidates
     */
    @SuppressWarnings("unchecked")
    protected @Nonnull <T> Collection<BeanDefinition<T>>  findBeanCandidates(@Nonnull Class<T> beanType, @Nullable BeanDefinition<?> filter) {
        ArgumentUtils.requireNonNull("beanType", beanType);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for type: {}", beanType);
        }
        // first traverse component definition classes and load candidates

        Collection<BeanDefinitionReference> beanDefinitionsClasses;

        if (indexedTypes.contains(beanType)) {
            beanDefinitionsClasses = beanIndex.get(beanType);
            if (beanDefinitionsClasses == null) {
                beanDefinitionsClasses = Collections.emptyList();
            }
        } else {
            beanDefinitionsClasses = this.beanDefinitionsClasses;
        }

        if (!beanDefinitionsClasses.isEmpty()) {

            Stream<BeanDefinition<T>> candidateStream = beanDefinitionsClasses
                    .stream()
                    .filter(reference -> {
                        if (reference.isPresent()) {
                            Class<?> candidateType = reference.getBeanType();
                            final boolean isCandidate = candidateType != null && (beanType.isAssignableFrom(candidateType) || beanType == candidateType);
                            return isCandidate && reference.isEnabled(this);
                        }
                        return false;
                    })
                    .map(ref -> {
                        BeanDefinition<T> loadedBean;
                        try {
                            loadedBean = ref.load(this);
                        } catch (Throwable e) {
                            throw new BeanContextException("Error loading bean [" + ref.getName() + "]: " + e.getMessage(), e);
                        }
                        return loadedBean;
                    });

            if (filter != null) {
                candidateStream = candidateStream.filter(candidate -> !candidate.equals(filter));
            }
            Set<BeanDefinition<T>> candidates = candidateStream
                    .filter(candidate -> candidate.isEnabled(this))
                    .collect(Collectors.toSet());

            if (!candidates.isEmpty()) {
                filterReplacedBeans(candidates);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolved bean candidates {} for type: {}", candidates, beanType);
            }
            return candidates;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No bean candidates found for type: {}", beanType);
            }
            return Collections.emptySet();
        }
    }

    /**
     * Find bean candidates for the given type.
     *
     * @param instance The bean instance
     * @param <T>      The bean generic type
     * @return The candidates
     */
    protected @Nonnull <T> Collection<BeanDefinition> findBeanCandidatesForInstance(@Nonnull T instance) {
        ArgumentUtils.requireNonNull("instance", instance);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for instance: {}", instance);
        }
        Collection<BeanDefinitionReference> beanDefinitionsClasses = this.beanDefinitionsClasses;
        return beanCandidateCache.computeIfAbsent(instance.getClass(), aClass -> {
            // first traverse component definition classes and load candidates

            if (!beanDefinitionsClasses.isEmpty()) {

                List<BeanDefinition> candidates = beanDefinitionsClasses
                        .stream()
                        .filter(reference -> {
                            if (reference.isEnabled(this)) {
                                Class<?> candidateType = reference.getBeanType();

                                return candidateType != null && candidateType.isInstance(instance);
                            } else {
                                return false;
                            }
                        })
                        .map(ref -> ref.load(this))
                        .filter(candidate -> candidate.isEnabled(this))
                        .collect(Collectors.toList());

                if (candidates.size() > 1) {
                    // try narrow to exact type
                    candidates = candidates
                            .stream()
                            .filter(candidate ->
                                    !(candidate instanceof NoInjectionBeanDefinition) &&
                                            candidate.getBeanType() == instance.getClass()
                            )
                            .collect(Collectors.toList());
                    return candidates;
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Resolved bean candidates {} for instance: {}", candidates, instance);
                }
                return candidates;
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No bean candidates found for instance: {}", instance);
                }
                return Collections.emptySet();
            }
        });

    }

    /**
     * Registers an active configuration.
     *
     * @param configuration The configuration to register
     */
    protected void registerConfiguration(@Nonnull BeanConfiguration configuration) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        beanConfigurations.put(configuration.getName(), configuration);
        ClassLoadingReporter.reportPresent(configuration.getClass());
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
     */
    protected @Nullable <T> T doCreateBean(@Nullable BeanResolutionContext resolutionContext,
                                 @Nonnull BeanDefinition<T> beanDefinition,
                                 @Nullable Qualifier<T> qualifier,
                                 boolean isSingleton,
                                 @Nullable Map<String, Object> argumentValues) {
        Qualifier declaredQualifier = resolveDeclaredQualifier(beanDefinition);
        T bean;
        Class<T> beanType = beanDefinition.getBeanType();
        if (isSingleton) {
            BeanRegistration<T> beanRegistration = singletonObjects.get(new BeanKey(beanDefinition, declaredQualifier));
            if (beanRegistration != null) {
                if (qualifier == null) {
                    return beanRegistration.bean;
                } else if (qualifier.reduce(beanType, Stream.of(beanRegistration.beanDefinition)).findFirst().isPresent()) {
                    return beanRegistration.bean;
                }
            } else if (qualifier != null) {
                beanRegistration = singletonObjects.get(new BeanKey(beanDefinition, null));
                if (beanRegistration != null) {
                    if (qualifier.reduce(beanType, Stream.of(beanRegistration.beanDefinition)).findFirst().isPresent()) {
                        return beanRegistration.bean;
                    }
                }
            }
        }

        if (resolutionContext == null) {
            resolutionContext = new DefaultBeanResolutionContext(this, beanDefinition);
        }

        if (beanDefinition instanceof BeanFactory) {
            BeanFactory<T> beanFactory = (BeanFactory<T>) beanDefinition;
            try {
                if (beanFactory instanceof ParametrizedBeanFactory) {
                    ParametrizedBeanFactory<T> parametrizedBeanFactory = (ParametrizedBeanFactory<T>) beanFactory;
                    Argument<?>[] requiredArguments = parametrizedBeanFactory.getRequiredArguments();
                    if (argumentValues == null) {
                        throw new BeanInstantiationException(resolutionContext, "Missing bean arguments for type: " + beanType.getName() + ". Requires arguments: " + ArrayUtils.toString(requiredArguments));
                    }
                    Map<String, Object> convertedValues = new LinkedHashMap<>(argumentValues);
                    for (Argument<?> requiredArgument : requiredArguments) {
                        Object val = argumentValues.get(requiredArgument.getName());
                        if (val == null && !requiredArgument.isDeclaredNullable()) {
                            throw new BeanInstantiationException(resolutionContext, "Missing bean argument [" + requiredArgument + "].");
                        }
                        BeanResolutionContext finalResolutionContext = resolutionContext;
                        Object convertedValue = null;
                        if (val != null) {
                            convertedValue = ConversionService.SHARED.convert(val, requiredArgument).orElseThrow(() ->
                                    new BeanInstantiationException(finalResolutionContext, "Invalid bean argument [" + requiredArgument + "]. Cannot convert object [" + val + "] to required type: " + requiredArgument.getType())
                            );
                        }
                        convertedValues.put(requiredArgument.getName(), convertedValue);
                    }

                    bean = parametrizedBeanFactory.build(
                            resolutionContext,
                            this,
                            beanDefinition,
                            convertedValues
                    );
                } else {
                    bean = beanFactory.build(resolutionContext, this, beanDefinition);

                    if (bean == null) {
                        if (!(beanDefinition.isIterable() || beanDefinition.getAnnotationMetadata().hasAnnotation(Factory.class))) {
                            throw new BeanInstantiationException(resolutionContext, "Bean Factory [" + beanFactory + "] returned null");
                        }
                    } else {
                        if (bean instanceof Qualified) {
                            ((Qualified) bean).$withBeanQualifier(declaredQualifier);
                        }
                    }
                }
            } catch (Throwable e) {
                if (e instanceof DependencyInjectionException) {
                    throw e;
                }
                if (e instanceof BeanInstantiationException) {
                    throw e;
                } else {
                    if (!resolutionContext.getPath().isEmpty()) {
                        throw new BeanInstantiationException(resolutionContext, e);
                    } else {
                        throw new BeanInstantiationException(beanDefinition, e);
                    }
                }
            }
        } else {
            ConstructorInjectionPoint<T> constructor = beanDefinition.getConstructor();
            Argument[] requiredConstructorArguments = constructor.getArguments();
            if (requiredConstructorArguments.length == 0) {
                bean = constructor.invoke();
            } else {
                Object[] constructorArgs = new Object[requiredConstructorArguments.length];
                for (int i = 0; i < requiredConstructorArguments.length; i++) {
                    Class argument = requiredConstructorArguments[i].getType();
                    constructorArgs[i] = getBean(resolutionContext, argument);
                }
                bean = constructor.invoke(constructorArgs);
            }

            inject(resolutionContext, null, bean);
        }

        if (bean != null) {
            if (!BeanCreatedEventListener.class.isInstance(bean)) {
                if (CollectionUtils.isNotEmpty(beanCreationEventListeners)) {
                    BeanKey beanKey = new BeanKey(beanDefinition, qualifier);
                    for (BeanRegistration<BeanCreatedEventListener> registration : beanCreationEventListeners) {
                        BeanDefinition<BeanCreatedEventListener> definition = registration.getBeanDefinition();
                        List<Argument<?>> typeArguments = definition.getTypeArguments(BeanCreatedEventListener.class);
                        if (CollectionUtils.isEmpty(typeArguments) || typeArguments.get(0).getType().isAssignableFrom(beanType)) {
                            BeanCreatedEventListener listener = registration.getBean();
                            bean = (T) listener.onCreated(new BeanCreatedEvent(this, beanDefinition, beanKey, bean));
                            if (bean == null) {
                                throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onCreated event");
                            }
                        }
                    }
                }
            }
            if (beanDefinition instanceof ValidatedBeanDefinition) {
                bean = ((ValidatedBeanDefinition<T>) beanDefinition).validate(resolutionContext, bean);
            }
            if (LOG_LIFECYCLE.isDebugEnabled()) {
                LOG_LIFECYCLE.debug("Created bean [{}] from definition [{}] with qualifier [{}]", bean, beanDefinition, qualifier);
            }
        }

        return bean;
    }

    private <T> Qualifier resolveDeclaredQualifier(@Nonnull BeanDefinition<T> beanDefinition) {
        return beanDefinition
                .getAnnotationTypeByStereotype(javax.inject.Qualifier.class)
                .map(aClass -> {
                    if (aClass == Primary.class) {
                        return null;
                    }
                    return Qualifiers.byAnnotation(beanDefinition, aClass.getName());
                })
                .orElseGet(() -> resolveDynamicQualifier((BeanDefinition) beanDefinition));
    }

    /**
     * Fall back method to attempt to find a candidate for the given definitions.
     *
     * @param beanType   The bean type
     * @param qualifier  The qualifier
     * @param candidates The candidates
     * @param <T>        The generic time
     * @return The concrete bean definition
     */
    protected @Nonnull <T> BeanDefinition<T> findConcreteCandidate(
            @Nonnull Class<T> beanType,
            @Nullable Qualifier<T> qualifier,
            @Nonnull Collection<BeanDefinition<T>> candidates) {
        throw new NonUniqueBeanException(beanType, candidates.iterator());
    }

    /**
     * Processes parallel bean definitions.
     */
    protected void processParallelBeans() {
        new Thread(() -> {
            final List<BeanDefinitionReference> parallelBeans = beanDefinitionsClasses.stream()
                    .filter(bd -> bd.getAnnotationMetadata().hasDeclaredStereotype(Parallel.class) && bd.isEnabled(this))
                    .collect(Collectors.toList());


            Collection<BeanDefinition> parallelDefinitions = new ArrayList<>();
            parallelBeans.forEach(beanDefinitionReference -> {
                try {
                    if (isRunning()) {
                        synchronized (singletonObjects) {
                            loadContextScopeBean(beanDefinitionReference, parallelDefinitions::add);
                        }
                    }
                } catch (Throwable e) {
                    LOG.error("Parallel Bean definition [" + beanDefinitionReference.getName() + "] could not be loaded: " + e.getMessage(), e);
                    Boolean shutdownOnError = beanDefinitionReference.getAnnotationMetadata().getValue(Parallel.class, "shutdownOnError", Boolean.class).orElse(true);
                    if (shutdownOnError) {
                        stop();
                    }
                }
            });

            filterProxiedTypes((Collection) parallelDefinitions, true, false);
            filterReplacedBeans((Collection) parallelDefinitions);

            parallelDefinitions.forEach(beanDefinition -> ForkJoinPool.commonPool().execute(() -> {
                try {
                    if (isRunning()) {
                        synchronized (singletonObjects) {
                            loadContextScopeBean(beanDefinition);
                        }
                    }
                } catch (Throwable e) {
                    LOG.error("Parallel Bean definition [" + beanDefinition.getName() + "] could not be loaded: " + e.getMessage(), e);
                    Boolean shutdownOnError = beanDefinition.getAnnotationMetadata().getValue(Parallel.class, "shutdownOnError", Boolean.class).orElse(true);
                    if (shutdownOnError) {
                        stop();
                    }
                }
            }));
            parallelDefinitions.clear();

        }).start();
    }

    private <T> void filterReplacedBeans(Collection<? extends BeanType<T>> candidates) {
        List<BeanType<T>> replacedTypes = new ArrayList<>(2);

        for (BeanType<T> candidate : candidates) {
            if (candidate.getAnnotationMetadata().hasStereotype(Replaces.class)) {
                replacedTypes.add(candidate);
            }
        }
        if (!replacedTypes.isEmpty()) {

            candidates.removeIf(definition -> {
                if (!definition.isEnabled(this)) {
                    return true;
                }
                final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
                if (annotationMetadata.hasDeclaredStereotype(Infrastructure.class)) {
                    return false;
                }

                Optional<Class<?>> declaringType = Optional.empty();

                if (definition instanceof BeanDefinition) {
                    declaringType = ((BeanDefinition<?>) definition).getDeclaringType();
                }
                Function<Class, Boolean> comparisonFunction = typeMatches(definition, annotationMetadata);

                Optional<Class<?>> finalDeclaringType = declaringType;
                return replacedTypes.stream().anyMatch(replacingCandidate -> {
                    if (definition == replacingCandidate) {
                        // don't replace yourself
                        return false;
                    }
                    if (replacingCandidate instanceof ProxyBeanDefinition) {
                        if (((ProxyBeanDefinition<T>) replacingCandidate).getTargetDefinitionType() == definition.getClass()) {
                            return false;
                        }
                    }

                    final AnnotationValue<Replaces> replacesAnn = replacingCandidate.getAnnotation(Replaces.class);
                    Optional<Class<?>> beanType = replacesAnn.classValue();
                    Optional<Class<?>> factory = replacesAnn.classValue("factory");
                    if (replacesAnn.contains(NAMED_MEMBER)) {

                        final String qualifier = replacesAnn.stringValue(NAMED_MEMBER).orElse(null);
                        if (qualifier != null) {
                            final Class type = beanType.orElse(factory.orElse(null));
                            if (type != null) {
                                final Optional qualified = Qualifiers.<T>byName(qualifier)
                                        .qualify(type, Stream.of(definition));
                                if (qualified.isPresent()) {
                                    return true;
                                }
                            }
                        }
                    }

                    if (LOG.isDebugEnabled()) {
                        if (factory.isPresent()) {
                            LOG.debug("Bean [{}] replaces existing bean of type [{}] in factory type [{}]", replacingCandidate.getBeanType(), beanType.orElse(null), factory.get());
                        } else {
                            LOG.debug("Bean [{}] replaces existing bean of type [{}]", replacingCandidate.getBeanType(), beanType.orElse(null));
                        }
                    }
                    if (factory.isPresent() && finalDeclaringType.isPresent()) {
                        if (factory.get() == finalDeclaringType.get()) {
                            return !beanType.isPresent() || comparisonFunction.apply(beanType.get());
                        } else {
                            return false;
                        }
                    } else {
                        return beanType.map(comparisonFunction).orElse(false);
                    }
                });
            });
        }
    }

    private <T> Function<Class, Boolean> typeMatches(BeanType<T> definition, AnnotationMetadata annotationMetadata) {
        Class<T> bt;

        if (definition instanceof ProxyBeanDefinition) {
            bt = ((ProxyBeanDefinition<T>) definition).getTargetType();
        } else {
            bt = definition.getBeanType();
        }

        if (annotationMetadata.hasStereotype(INTRODUCTION_TYPE)) {
            Class<? super T> superclass = bt.getSuperclass();
            if (superclass == Object.class) {
                // interface introduction
                return (clazz) -> clazz.isAssignableFrom(bt);
            } else {
                // abstract class introduction
                return (clazz) -> clazz == superclass;
            }
        }
        if (annotationMetadata.hasStereotype(AROUND_TYPE)) {
            Class<? super T> superclass = bt.getSuperclass();
            return (clazz) -> clazz == superclass || clazz == bt;
        }
        if (annotationMetadata.hasAnnotation(DefaultImplementation.class)) {
            Optional<Class> defaultImpl = annotationMetadata.classValue(DefaultImplementation.class);
            if (!defaultImpl.isPresent()) {
                defaultImpl = annotationMetadata.classValue(DefaultImplementation.class, "name");
            }
            if (defaultImpl.filter(impl -> impl == bt).isPresent()) {
                return (clazz) -> clazz.isAssignableFrom(bt);
            }
        }

        return (clazz) -> clazz == bt;
    }

    private <T> void doInject(BeanResolutionContext resolutionContext, T instance, BeanDefinition definition) {
        definition.inject(resolutionContext, this, instance);
        if (definition instanceof InitializingBeanDefinition) {
            ((InitializingBeanDefinition) definition).initialize(resolutionContext, this, instance);
        }
    }

    private void loadContextScopeBean(BeanDefinitionReference contextScopeBean) {
        loadContextScopeBean(contextScopeBean, this::loadContextScopeBean);
    }

    private void loadContextScopeBean(BeanDefinitionReference contextScopeBean, Consumer<BeanDefinition> beanDefinitionConsumer) {
        if (contextScopeBean.isEnabled(this)) {
            BeanDefinition beanDefinition = contextScopeBean.load(this);
            if (beanDefinition.isEnabled(this)) {
                beanDefinitionConsumer.accept(beanDefinition);
            }
        }
    }

    private void loadContextScopeBean(BeanDefinition beanDefinition) {
        if (beanDefinition.isIterable()) {
            Collection<BeanDefinition> beanCandidates = findBeanCandidates(beanDefinition.getBeanType(), null);
            for (BeanDefinition beanCandidate : beanCandidates) {
                DefaultBeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(this, beanDefinition);

                createAndRegisterSingleton(
                        resolutionContext,
                        beanCandidate,
                        beanCandidate.getBeanType(),
                        null
                );
            }

        } else {

            createAndRegisterSingleton(new DefaultBeanResolutionContext(this, beanDefinition), beanDefinition, beanDefinition.getBeanType(), null);
        }
    }

    private <T> T getBeanInternal(@Nullable BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier, boolean throwNonUnique, boolean throwNoSuchBean) {
        // allow injection the bean context
        if (thisInterfaces.contains(beanType)) {
            return (T) this;
        }

        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Looking up existing bean for key: {}", beanKey);
        }

        T inFlightBean = resolutionContext != null ? resolutionContext.getInFlightBean(beanKey) : null;
        if (inFlightBean != null) {
            return inFlightBean;
        }

        BeanRegistration<T> beanRegistration = singletonObjects.get(beanKey);
        if (beanRegistration != null) {
            T bean = beanRegistration.bean;
            if (bean == null && throwNoSuchBean) {
                throw new NoSuchBeanException(beanType, qualifier);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Resolved existing bean [{}] for type [{}] and qualifier [{}]", beanRegistration.bean, beanType, qualifier);
                }
                return bean;
            }
        } else if (LOG.isTraceEnabled()) {
            LOG.trace("No existing bean found for bean key: {}", beanKey);
        }

        synchronized (singletonObjects) {

            Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(beanType, qualifier, throwNonUnique, false);
            T bean;

            if (concreteCandidate.isPresent()) {
                BeanDefinition<T> definition = concreteCandidate.get();

                bean = findExistingCompatibleSingleton(beanType, qualifier, definition);
                if (bean != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Resolved existing bean [{}] for type [{}] and qualifier [{}]", bean, beanType, qualifier);
                    }
                    return bean;
                }


                if (resolutionContext == null) {
                    resolutionContext = new DefaultBeanResolutionContext(this, definition);
                }

                if (definition.isProvided() && beanType == definition.getBeanType()) {
                    if (throwNoSuchBean) {
                        throw new NoSuchBeanException(beanType, qualifier);
                    }
                    return null;
                } else {
                    bean = getBeanForDefinition(resolutionContext, beanType, qualifier, throwNoSuchBean, definition);
                    if (bean == null && throwNoSuchBean) {
                        throw new NoSuchBeanException(beanType, qualifier);
                    } else {
                        return bean;
                    }
                }

            } else {
                bean = findExistingCompatibleSingleton(beanType, qualifier, null);
                if (bean == null && throwNoSuchBean) {
                    throw new NoSuchBeanException(beanType, qualifier);
                } else {
                    return bean;
                }
            }
        }
    }

    private <T> T getBeanForDefinition(
            BeanResolutionContext resolutionContext,
            Class<T> beanType, Qualifier<T> qualifier,
            boolean throwNoSuchBean,
            BeanDefinition<T> definition) {
        if (definition.isSingleton() && !definition.hasStereotype(SCOPED_PROXY_ANN)) {
            return createAndRegisterSingleton(resolutionContext, definition, beanType, qualifier);
        } else {
            return getScopedBeanForDefinition(resolutionContext, beanType, qualifier, throwNoSuchBean, definition);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getScopedBeanForDefinition(
            @Nullable BeanResolutionContext resolutionContext,
            Class<T> beanType,
            Qualifier<T> qualifier,
            boolean throwNoSuchBean,
            BeanDefinition<T> definition) {
        final boolean isProxy = definition.isProxy();
        final boolean isScopedProxyDefinition = definition.hasStereotype(SCOPED_PROXY_ANN);
        if (qualifier != PROXY_TARGET_QUALIFIER && isProxy && isScopedProxyDefinition) {
            Class<?> proxiedType = resolveProxiedType(beanType, definition);
            BeanKey key = new BeanKey(proxiedType, qualifier);
            BeanDefinition<T> finalDefinition = definition;
            BeanResolutionContext finalResolutionContext1 = resolutionContext;
            return (T) scopedProxies.computeIfAbsent(key, (Function<BeanKey, T>) beanKey -> {
                Qualifier<T> q = qualifier;
                if (q == null) {
                    q = resolveDeclaredQualifier(finalDefinition);
                }
                BeanDefinition<T> proxyDefinition = (BeanDefinition<T>) findProxyBeanDefinition((Class) proxiedType, q).orElse(finalDefinition);

                BeanResolutionContext currentResolutionContext = finalResolutionContext1 != null ? finalResolutionContext1 : new DefaultBeanResolutionContext(
                        this,
                        proxyDefinition
                );
                T createBean = doCreateBean(currentResolutionContext, proxyDefinition, qualifier, false, null);
                if (createBean instanceof Qualified) {
                    ((Qualified) createBean).$withBeanQualifier(qualifier);
                }
                if (createBean == null && throwNoSuchBean) {
                    throw new NoSuchBeanException(proxyDefinition.getBeanType(), qualifier);
                }
                return createBean;
            });
        } else {
            Optional<BeanResolutionContext.Segment> currentSegment = resolutionContext != null ? resolutionContext.getPath().currentSegment() : Optional.empty();
            Optional<CustomScope> registeredScope = Optional.empty();

            if (currentSegment.isPresent()) {
                Argument argument = currentSegment.get().getArgument();
                final Optional<Class<? extends Annotation>> scope = argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Scope.class);
                registeredScope = scope.flatMap(customScopeRegistry::findScope);
            }

            if (!isProxy && isScopedProxyDefinition && !registeredScope.isPresent()) {
                final List<Class<? extends Annotation>> scopeHierarchy = definition.getAnnotationTypesByStereotype(Scope.class);
                for (Class<? extends Annotation> scope : scopeHierarchy) {
                    registeredScope = customScopeRegistry.findScope(scope);
                    if (registeredScope.isPresent()) {
                        break;
                    }
                }
            }
            if (registeredScope.isPresent()) {
                CustomScope customScope = registeredScope.get();
                if (isProxy) {
                    definition = getProxyTargetBeanDefinition(beanType, qualifier);
                }
                BeanDefinition<T> finalDefinition = definition;

                if (resolutionContext == null) {
                    resolutionContext = new DefaultBeanResolutionContext(this, finalDefinition);
                }

                BeanResolutionContext finalResolutionContext = resolutionContext;
                return (T) customScope.get(
                        resolutionContext,
                        finalDefinition,
                        new BeanKey(beanType, qualifier),
                        new ParametrizedProvider() {
                            @Override
                            public Object get(Map argumentValues) {
                                Object createBean = doCreateBean(finalResolutionContext, finalDefinition, qualifier, false, argumentValues);
                                if (createBean == null && throwNoSuchBean) {
                                    throw new NoSuchBeanException(finalDefinition.getBeanType(), qualifier);
                                }
                                return createBean;
                            }

                            @Override
                            public Object get(Object... argumentValues) {
                                T createdBean = doCreateBean(finalResolutionContext, finalDefinition, beanType, qualifier, argumentValues);
                                if (createdBean == null && throwNoSuchBean) {
                                    throw new NoSuchBeanException(finalDefinition.getBeanType(), qualifier);
                                }
                                return createdBean;
                            }
                        }
                );
            } else {
                if (resolutionContext == null) {
                    resolutionContext = new DefaultBeanResolutionContext(this, definition);
                }

                T createBean = doCreateBean(resolutionContext, definition, qualifier, false, null);
                if (createBean == null && throwNoSuchBean) {
                    throw new NoSuchBeanException(definition.getBeanType(), qualifier);
                }
                return createBean;
            }
        }

    }

    private <T> Class<?> resolveProxiedType(Class<T> beanType, BeanDefinition<T> definition) {
        Class<?> proxiedType;
        if (definition instanceof ProxyBeanDefinition) {
            proxiedType = ((ProxyBeanDefinition<T>) definition).getTargetType();
        } else if (definition instanceof BeanDefinitionDelegate) {
            BeanDefinition<T> delegate = ((BeanDefinitionDelegate<T>) definition).getDelegate();
            if (delegate instanceof ProxyBeanDefinition) {
                proxiedType = ((ProxyBeanDefinition<T>) delegate).getTargetType();
            } else {
                proxiedType = beanType;
            }
        } else {
            proxiedType = beanType;
        }
        return proxiedType;
    }

    private <T> T findExistingCompatibleSingleton(Class<T> beanType, Qualifier<T> qualifier, BeanDefinition<T> definition) {
        T bean = null;
        for (Map.Entry<BeanKey, BeanRegistration> entry : singletonObjects.entrySet()) {
            BeanKey key = entry.getKey();
            if (qualifier == null || qualifier.equals(key.qualifier)) {
                BeanRegistration reg = entry.getValue();
                if (beanType.isInstance(reg.bean)) {
                    if (qualifier == null && definition != null) {
                        if (!reg.beanDefinition.equals(definition)) {
                            // different definition, so ignore
                            return null;
                        }
                    }
                    synchronized (singletonObjects) {
                        bean = (T) reg.bean;
                        registerSingletonBean(reg.beanDefinition, beanType, bean, qualifier, true);
                    }
                }
            } else if (key.qualifier == null) {
                BeanRegistration registration = entry.getValue();
                Object existing = registration.bean;
                if (beanType.isInstance(existing)) {
                    Optional<BeanDefinition> candidate = qualifier.reduce(beanType, Stream.of(registration.beanDefinition)).findFirst();
                    if (candidate.isPresent()) {
                        synchronized (singletonObjects) {
                            bean = (T) existing;
                            registerSingletonBean(candidate.get(), beanType, bean, qualifier, true);
                        }
                    }
                }
            }
        }
        return bean;
    }

    /**
     * Find a concrete candidate for the given qualifier.
     *
     * @param beanType        The bean type
     * @param qualifier       The qualifier
     * @param throwNonUnique  Whether to throw an exception if the bean is not found
     * @param includeProvided Whether to include provided resolution
     * @param <T>             The bean generic type
     * @return The concrete bean definition candidate
     */
    @SuppressWarnings("unchecked")
    private <T> Optional<BeanDefinition<T>> findConcreteCandidate(
            Class<T> beanType,
            Qualifier<T> qualifier,
            boolean throwNonUnique,
            boolean includeProvided) {
        return (Optional) beanConcreteCandidateCache.computeIfAbsent(new BeanKey(beanType, qualifier), beanKey ->
                (Optional) findConcreteCandidateNoCache(beanType, qualifier, throwNonUnique, includeProvided, true)
        );
    }

    private <T> Optional<BeanDefinition<T>> findConcreteCandidateNoCache(
            Class<T> beanType,
            Qualifier<T> qualifier,
            boolean throwNonUnique,
            boolean includeProvided,
            boolean filterProxied) {
        Collection<BeanDefinition<T>> candidates = new ArrayList<>(findBeanCandidates(beanType, null));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        filterProxiedTypes(candidates, filterProxied, false);

        if (!includeProvided) {
            candidates.removeIf(BeanDefinition::isProvided);
        }

        int size = candidates.size();
        BeanDefinition<T> definition = null;
        if (size > 0) {
            if (qualifier != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Qualifying bean [{}] for qualifier: {} ", beanType.getName(), qualifier);
                }
                Stream<BeanDefinition<T>> candidateStream = candidates.stream().filter(c -> !c.isAbstract());
                Stream<BeanDefinition<T>> qualified = qualifier.reduce(beanType, candidateStream);
                List<BeanDefinition<T>> beanDefinitionList = qualified.collect(Collectors.toList());
                if (beanDefinitionList.isEmpty()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("No qualifying beans of type [{}] found for qualifier: {} ", beanType.getName(), qualifier);
                    }
                    return Optional.empty();
                }

                definition = lastChanceResolve(beanType, qualifier, throwNonUnique, beanDefinitionList);
            } else {
                candidates.removeIf(BeanDefinition::isAbstract);
                if (candidates.size() == 1) {
                    definition = candidates.iterator().next();
                } else {

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Searching for @Primary for type [{}] from candidates: {} ", beanType.getName(), candidates);
                    }
                    definition = lastChanceResolve(beanType, null, throwNonUnique, candidates);
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            if (definition != null) {
                if (qualifier != null) {
                    LOG.debug("Found concrete candidate [{}] for type: {} {} ", definition, qualifier, beanType.getName());
                } else {
                    LOG.debug("Found concrete candidate [{}] for type: {} ", definition, beanType.getName());
                }
            }
        }
        return Optional.ofNullable(definition);
    }

    private <T> void filterProxiedTypes(Collection<BeanDefinition<T>> candidates, boolean filterProxied, boolean filterDelegates) {
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

                    if (!delegates.contains(delegate)) {
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

    private <T> BeanDefinition<T> lastChanceResolve(Class<T> beanType, Qualifier<T> qualifier, boolean throwNonUnique, Collection<BeanDefinition<T>> candidates) {
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
        } else {
            BeanDefinition<T> definition = null;
            candidates = candidates.stream().filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class)).collect(Collectors.toList());
            if (candidates.size() == 1) {
                return candidates.iterator().next();
            } else {
                Collection<BeanDefinition<T>> exactMatches = filterExactMatch(beanType, candidates);
                if (exactMatches.size() == 1) {
                    definition = exactMatches.iterator().next();
                } else if (throwNonUnique) {
                    definition = findConcreteCandidate(beanType, qualifier, candidates);
                }
                return definition;
            }
        }
    }

    private <T> T createAndRegisterSingleton(BeanResolutionContext resolutionContext, BeanDefinition<T> definition, Class<T> beanType, Qualifier<T> qualifier) {
        synchronized (singletonObjects) {
            if (definition instanceof NoInjectionBeanDefinition) {
                NoInjectionBeanDefinition<T> manuallyRegistered = (NoInjectionBeanDefinition) definition;
                T bean = (T) singletonObjects.get(new BeanKey(beanType, manuallyRegistered.getQualifier()));
                if (bean == null) {
                    throw new IllegalStateException("Manually registered singleton no longer present in bean context");
                }
                registerSingletonBean(definition, beanType, bean, qualifier, true);
                return bean;
            } else {
                T createdBean = doCreateBean(resolutionContext, definition, qualifier, true, null);
                registerSingletonBean(definition, beanType, createdBean, qualifier, true);
                return createdBean;
            }
        }
    }

    private void readAllBeanConfigurations() {
        Iterable<BeanConfiguration> beanConfigurations = resolveBeanConfigurations();
        for (BeanConfiguration beanConfiguration : beanConfigurations) {
            registerConfiguration(beanConfiguration);
        }
    }

    private <T> Collection<BeanDefinition<T>> filterExactMatch(final Class<T> beanType, Collection<BeanDefinition<T>> candidates) {
        Stream<BeanDefinition<T>> filteredResults = candidates
                .stream()
                .filter((BeanDefinition<T> candidate) -> candidate.getBeanType() == beanType);
        return filteredResults.collect(Collectors.toList());
    }

    private <T> void registerSingletonBean(BeanDefinition<T> beanDefinition, Class<T> beanType, T createdBean, Qualifier<T> qualifier, boolean singleCandidate) {
        // for only one candidate create link to bean type as singleton
        if (qualifier == null) {
            qualifier = resolveDynamicQualifier(beanDefinition);
        }

        BeanKey key = new BeanKey<>(beanDefinition, qualifier);
        if (LOG.isDebugEnabled()) {
            if (qualifier != null) {
                LOG.debug("Registering singleton bean {} for type [{} {}] using bean key {}", createdBean, qualifier, beanType.getName(), key);
            } else {
                LOG.debug("Registering singleton bean {} for type [{}] using bean key {}", createdBean, beanType.getName(), key);
            }
        }
        BeanRegistration<T> registration = new BeanRegistration<>(key, beanDefinition, createdBean);

        if (singleCandidate || key.typeArguments != null) {
            singletonObjects.put(key, registration);
        }

        boolean isNotProxyTarget = qualifier != PROXY_TARGET_QUALIFIER;
        if (isNotProxyTarget) {

            Class<?> createdType = createdBean != null ? createdBean.getClass() : beanType;
            boolean createdTypeDiffers = !createdType.equals(beanType);

            BeanKey createdBeanKey = new BeanKey(createdType, qualifier);
            Optional<Class<? extends Annotation>> qualifierAnn = beanDefinition.getAnnotationTypeByStereotype(javax.inject.Qualifier.class);
            if (qualifierAnn.isPresent()) {
                Class annotation = qualifierAnn.get();
                if (Primary.class == annotation) {
                    BeanKey primaryBeanKey = new BeanKey<>(beanType, null);
                    singletonObjects.put(primaryBeanKey, registration);
                    if (createdTypeDiffers) {
                        singletonObjects.put(new BeanKey<>(createdType, null), registration);
                    }
                } else {

                    BeanKey qualifierKey = new BeanKey<>(createdType, Qualifiers.byAnnotation(beanDefinition, annotation.getName()));
                    if (!qualifierKey.equals(createdBeanKey)) {
                        singletonObjects.put(qualifierKey, registration);
                    }
                }
            } else {
                if (!beanDefinition.isIterable()) {
                    BeanKey primaryBeanKey = new BeanKey<>(createdType, null);
                    singletonObjects.put(primaryBeanKey, registration);
                    if (qualifier != null) {
                        BeanKey qualifiedKey = new BeanKey<>(beanType, qualifier);
                        singletonObjects.put(qualifiedKey, registration);
                    }
                } else {
                    if (beanDefinition.isPrimary()) {
                        BeanKey primaryBeanKey = new BeanKey<>(beanType, null);
                        singletonObjects.put(primaryBeanKey, registration);
                        if (createdTypeDiffers) {
                            singletonObjects.put(new BeanKey<>(createdType, null), registration);
                        }
                    }
                }
            }
            singletonObjects.put(createdBeanKey, registration);
        }
    }

    private <T> Qualifier<T> resolveDynamicQualifier(BeanDefinition<T> beanDefinition) {
        Qualifier<T> qualifier = null;
        if (beanDefinition instanceof BeanDefinitionDelegate) {
            String name = ((BeanDefinitionDelegate<?>) beanDefinition).get(Named.class.getName(), String.class, null);
            if (name != null) {
                qualifier = Qualifiers.byName(name);
            }
        }
        if (qualifier == null) {
            Optional<String> optional = beanDefinition.stringValue(javax.inject.Named.class);
            qualifier = (Qualifier<T>) optional.map(name -> Qualifiers.byAnnotation(beanDefinition, name)).orElse(null);
        }
        return qualifier;
    }

    private void readAllBeanDefinitionClasses() {
        List<BeanDefinitionReference> contextScopeBeans = new ArrayList<>(20);
        List<BeanDefinitionReference> processedBeans = new ArrayList<>(10);
        List<BeanDefinitionReference> beanDefinitionReferences = resolveBeanDefinitionReferences();
        List<BeanDefinitionReference> disabled = new ArrayList<>(20);

        final boolean reportingEnabled = ClassLoadingReporter.isReportingEnabled();

        beanDefinitionsClasses.addAll(beanDefinitionReferences);

        Map<BeanConfiguration, Boolean> configurationEnabled = beanConfigurations.values().stream()
                .collect(Collectors.toMap(Function.identity(), bc -> bc.isEnabled(this)));

        for (BeanDefinitionReference beanDefinitionReference : beanDefinitionReferences) {
            boolean disabledByConfiguration = beanConfigurations.values()
                    .stream()
                    .filter(c -> c.isWithin(beanDefinitionReference))
                    .anyMatch(c -> !configurationEnabled.get(c));

            if (disabledByConfiguration) {
                disabled.add(beanDefinitionReference);

                if (reportingEnabled) {
                    ClassLoadingReporter.reportMissing(beanDefinitionReference.getBeanDefinitionName());
                    ClassLoadingReporter.reportMissing(beanDefinitionReference.getName());
                }
            } else {
                final List<AnnotationValue<Indexed>> indexes = beanDefinitionReference.getAnnotationMetadata().getAnnotationValuesByType(Indexed.class);
                if (CollectionUtils.isNotEmpty(indexes)) {
                    for (AnnotationValue<Indexed> index : indexes) {
                        final Class indexedType = index.classValue().orElse(null);
                        if (indexedType != null) {
                            resolveTypeIndex(indexedType);
                        }
                    }
                }
                if (beanDefinitionReference.isContextScope()) {
                    contextScopeBeans.add(beanDefinitionReference);
                }
                if (beanDefinitionReference.requiresMethodProcessing()) {
                    processedBeans.add(beanDefinitionReference);
                }
            }
        }

        beanDefinitionsClasses.removeAll(disabled);
        indexBeanDefinitions();
        disabled.clear();

        initializeEventListeners();
        initializeContext(contextScopeBeans, processedBeans);
    }

    private void indexBeanDefinitions() {
        for (BeanDefinitionReference beanDefinitionReference: beanDefinitionsClasses) {
            if (beanDefinitionReference.isPresent()) {
                final Class beanType = beanDefinitionReference.getBeanType();
                indexedTypes.stream()
                        .filter(t -> t == beanType || t.isAssignableFrom(beanType))
                        .forEach(indexedType -> {
                    final Collection<BeanDefinitionReference> indexed = resolveTypeIndex(indexedType);
                    indexed.add(beanDefinitionReference);
                });
            }
        }
    }

    @NotNull
    private Collection<BeanDefinitionReference> resolveTypeIndex(Class<?> indexedType) {
        return beanIndex.computeIfAbsent(indexedType, aClass -> {
            indexedTypes.add(indexedType);
            return new ArrayList<>(20);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<BeanDefinition<T>> findBeanCandidatesInternal(Class<T> beanType) {
        return (Collection) beanCandidateCache.computeIfAbsent(beanType, aClass -> (Collection) findBeanCandidates(beanType, null));
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> getBeansOfTypeInternal(@Nullable BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        boolean hasQualifier = qualifier != null;
        if (LOG.isDebugEnabled()) {
            if (hasQualifier) {
                LOG.debug("Resolving beans for type: {} {} ", qualifier, beanType.getName());
            } else {
                LOG.debug("Resolving beans for type: {}", beanType.getName());
            }
        }
        BeanKey<T> key = new BeanKey<>(beanType, qualifier);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Looking up existing beans for key: {}", key);
        }
        @SuppressWarnings("unchecked") Collection<T> existing = (Collection<T>) initializedObjectsByType.get(key);
        if (existing != null) {
            logResolvedExisting(beanType, qualifier, hasQualifier, existing);
            return existing;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("No beans found for key: {}", key);
        }

        synchronized (singletonObjects) {
            existing = (Collection<T>) initializedObjectsByType.get(key);
            if (existing != null) {
                logResolvedExisting(beanType, qualifier, hasQualifier, existing);
                return existing;
            }

            HashSet<T> beansOfTypeList = new HashSet<>();
            boolean allCandidatesAreSingleton = false;
            Collection<T> beans;
            Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(beanType);
            filterProxiedTypes(candidates, true, false);
            boolean hasCandidates = !candidates.isEmpty();
            if (hasQualifier && hasCandidates) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Qualifying bean [{}] from candidates {} for qualifier: {} ", beanType.getName(), candidates, qualifier);
                }
                Stream<BeanDefinition<T>> candidateStream = candidates.stream();
                candidateStream = applyBeanResolutionFilters(resolutionContext, candidateStream);

                List<BeanDefinition<T>> reduced = qualifier.reduce(beanType, candidateStream)
                        .collect(Collectors.toList());
                if (!reduced.isEmpty()) {
                    for (BeanDefinition<T> definition : reduced) {
                        if (definition.isSingleton()) {
                            allCandidatesAreSingleton = true;
                        }
                        addCandidateToList(resolutionContext, beanType, definition, beansOfTypeList, qualifier, reduced.size() == 1);
                    }
                    beans = beansOfTypeList;
                } else {

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found no matching beans of type [{}] for qualifier: {} ", beanType.getName(), qualifier);
                    }
                    allCandidatesAreSingleton = true;
                    beans = beansOfTypeList;
                }
            } else if (hasCandidates) {
                boolean hasNonSingletonCandidate = false;
                int candidateCount = candidates.size();
                Stream<BeanDefinition<T>> candidateStream = candidates.stream();
                candidateStream = applyBeanResolutionFilters(resolutionContext, candidateStream);

                List<BeanDefinition<T>> candidateList = candidateStream.collect(Collectors.toList());
                for (BeanDefinition<T> candidate : candidateList) {
                    if (!hasNonSingletonCandidate && !candidate.isSingleton()) {
                        hasNonSingletonCandidate = true;
                    }
                    addCandidateToList(resolutionContext, beanType, candidate, beansOfTypeList, qualifier, candidateCount == 1);
                }
                if (!hasNonSingletonCandidate) {
                    allCandidatesAreSingleton = true;
                }
                beans = beansOfTypeList;
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found no possible candidate beans of type [{}] for qualifier: {} ", beanType.getName(), qualifier);
                }
                allCandidatesAreSingleton = true;
                beans = beansOfTypeList;
            }

            if (Ordered.class.isAssignableFrom(beanType)) {
                beans = beans.stream().sorted(OrderUtil.COMPARATOR).collect(StreamUtils.toImmutableCollection());
            } else {
                beans = Collections.unmodifiableCollection(beans);
            }

            if (allCandidatesAreSingleton) {
                initializedObjectsByType.put(key, (Collection<Object>) beans);
            }
            if (LOG.isDebugEnabled() && !beans.isEmpty()) {
                if (hasQualifier) {
                    LOG.debug("Found {} beans for type [{} {}]: {} ", beans.size(), qualifier, beanType.getName(), beans);
                } else {
                    LOG.debug("Found {} beans for type [{}]: {} ", beans.size(), beanType.getName(), beans);
                }
            }

            return beans;
        }
    }

    private <T> void logResolvedExisting(Class<T> beanType, Qualifier<T> qualifier, boolean hasQualifier, Collection<T> existing) {
        if (LOG.isTraceEnabled()) {
            if (hasQualifier) {
                LOG.trace("Found {} existing beans for type [{} {}]: {} ", existing.size(), qualifier, beanType.getName(), existing);
            } else {
                LOG.trace("Found {} existing beans for type [{}]: {} ", existing.size(), beanType.getName(), existing);
            }
        }
    }

    private <T> Stream<BeanDefinition<T>> applyBeanResolutionFilters(@Nullable BeanResolutionContext resolutionContext, Stream<BeanDefinition<T>> candidateStream) {
        candidateStream = candidateStream.filter(c -> !c.isAbstract());

        BeanResolutionContext.Segment segment = resolutionContext != null ? resolutionContext.getPath().peek() : null;
        if (segment instanceof DefaultBeanResolutionContext.ConstructorSegment) {
            BeanDefinition declaringBean = segment.getDeclaringType();
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
        return candidateStream;
    }

    private <T> void addCandidateToList(
            @Nullable BeanResolutionContext resolutionContext,
            Class<T> beanType,
            BeanDefinition<T> candidate,
            Collection<T> beansOfTypeList,
            Qualifier<T> qualifier,
            boolean singleCandidate) {
        T bean;
        if (candidate.isSingleton()) {
            synchronized (singletonObjects) {
                if (resolutionContext == null) {
                    resolutionContext = new DefaultBeanResolutionContext(this, candidate);
                }
                bean = doCreateBean(resolutionContext, candidate, qualifier, true, null);
                registerSingletonBean(candidate, beanType, bean, qualifier, singleCandidate);
            }
        } else {
            bean = getScopedBeanForDefinition(resolutionContext, beanType, qualifier, true, candidate);
        }

        if (bean != null) {
            beansOfTypeList.add(bean);
        }
    }

    private <T> boolean isCandidatePresent(Class<T> beanType, Qualifier<T> qualifier) {
        final Collection<BeanDefinition<T>> candidates = findBeanCandidates(beanType, null);
        if (!candidates.isEmpty()) {
            filterReplacedBeans(candidates);
            Stream<BeanDefinition<T>> stream = candidates.stream();
            if (qualifier != null) {
                stream = qualifier.reduce(beanType, stream);
            }
            return stream.count() > 0;
        }
        return false;
    }

    private List<BeanRegistration> topologicalSort(Collection<BeanRegistration> beans) {
        List<BeanRegistration> sorted = new ArrayList<>();
        List<BeanRegistration> unsorted = new ArrayList<>(beans);

        //loop until all items have been sorted
        while (!unsorted.isEmpty()) {
            boolean acyclic = false;

            Iterator<BeanRegistration> i = unsorted.iterator();
            while (i.hasNext()) {
                BeanRegistration bean = i.next();
                boolean found = false;

                //determine if any components are in the unsorted list
                Collection<Class> components = bean.getBeanDefinition().getRequiredComponents();
                for (Class<?> clazz: components) {
                    if (unsorted.stream()
                            .map(BeanRegistration::getBeanDefinition)
                            .map(BeanDefinition::getBeanType)
                            .anyMatch(bt -> clazz.isAssignableFrom(bt))) {
                        found = true;
                        break;
                    }
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

        @Nonnull
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
    static final class BeanKey<T> implements BeanIdentifier {
        private final Class beanType;
        private final Qualifier qualifier;
        private final Class[] typeArguments;
        private final int hashCode;

        /**
         * A bean key for the given bean definition.
         * @param definition The definition
         * @param qualifier The qualifier
         */
        BeanKey(BeanDefinition<T> definition, Qualifier<T> qualifier) {
            this(definition.getBeanType(), qualifier, definition.getTypeParameters());
        }

        /**
         * @param beanType  The bean type
         * @param qualifier The qualifier
         * @param typeArguments The type arguments
         */
        BeanKey(Class<T> beanType, Qualifier<T> qualifier, @Nullable Class... typeArguments) {
            this.beanType = beanType;
            this.qualifier = qualifier;
            this.typeArguments = ArrayUtils.isEmpty(typeArguments) ? null : typeArguments;
            int result = Objects.hash(beanType, qualifier);
            result = 31 * result + Arrays.hashCode(this.typeArguments);
            this.hashCode = result;
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
            return (qualifier != null ? qualifier.toString() + " " : "") + beanType.getName();
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
            return beanType.equals(beanKey.beanType) &&
                    Objects.equals(qualifier, beanKey.qualifier) &&
                    Arrays.equals(typeArguments, beanKey.typeArguments);
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
            return Primary.class.getSimpleName();
        }
    }


    /**
     * @param <T> The bean type
     */
    private static class NoInjectionBeanDefinition<T> implements BeanDefinition<T>, BeanDefinitionReference<T> {
        private final Class<?> singletonClass;
        private final Map<Class<?>, List<Argument<?>>> typeArguments = new HashMap<>();

        private final Qualifier<T> qualifier;

        /**
         * @param singletonClass The singleton class
         */
        NoInjectionBeanDefinition(Class singletonClass, Qualifier<T> qualifier) {
            this.singletonClass = singletonClass;
            this.qualifier = qualifier;
        }

        @Nullable
        public Qualifier<T> getQualifier() {
            return qualifier;
        }

        @Override
        public Optional<Class<? extends Annotation>> getScope() {
            return Optional.of(Singleton.class);
        }

        @SuppressWarnings("unchecked")
        @Nonnull
        @Override
        public List<Argument<?>> getTypeArguments(Class<?> type) {
            List<Argument<?>> result = typeArguments.get(type);
            if (result == null) {
                Class[] classes = type.isInterface() ? GenericTypeUtils.resolveInterfaceTypeArguments(singletonClass, type) : GenericTypeUtils.resolveSuperTypeGenericArguments(singletonClass, type);
                result = Arrays.stream(classes).map((Function<Class, Argument<?>>) Argument::of).collect(Collectors.toList());
                typeArguments.put(type, result);
            }

            return result;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public boolean isProvided() {
            return false;
        }

        @Override
        public boolean isIterable() {
            return false;
        }

        @Override
        public boolean isPrimary() {
            return true;
        }

        @Override
        public Class getBeanType() {
            return singletonClass;
        }

        @Override
        public Optional<Class<?>> getDeclaringType() {
            return Optional.empty();
        }

        @Override
        public ConstructorInjectionPoint getConstructor() {
            throw new UnsupportedOperationException("Runtime singleton's cannot be constructed at runtime");
        }

        @Override
        public Collection<Class> getRequiredComponents() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MethodInjectionPoint> getInjectedMethods() {
            return Collections.emptyList();
        }

        @Override
        public Collection<FieldInjectionPoint> getInjectedFields() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MethodInjectionPoint> getPostConstructMethods() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MethodInjectionPoint> getPreDestroyMethods() {
            return Collections.emptyList();
        }

        @Override
        public String getName() {
            return singletonClass.getName();
        }

        @Override
        public boolean isEnabled(BeanContext beanContext) {
            return true;
        }

        @Override
        public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class[] argumentTypes) {
            return Optional.empty();
        }

        @Override
        public T inject(BeanContext context, T bean) {
            return bean;
        }

        @Override
        public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
            return bean;
        }

        @Override
        public Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
            return Collections.emptyList();
        }

        @Override
        public Stream<ExecutableMethod<T, ?>> findPossibleMethods(String name) {
            return Stream.empty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            NoInjectionBeanDefinition that = (NoInjectionBeanDefinition) o;

            return singletonClass.equals(that.singletonClass);
        }

        @Override
        public int hashCode() {
            return singletonClass.hashCode();
        }

        @Override
        public String getBeanDefinitionName() {
            return singletonClass.getName();
        }

        @Override
        public BeanDefinition<T> load() {
            return this;
        }

        @Override
        public BeanDefinition<T> load(BeanContext context) {
            return this;
        }

        @Override
        public boolean isContextScope() {
            return false;
        }

        @Override
        public boolean isPresent() {
            return true;
        }
    }
}
