/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.annotation.*;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.event.*;
import io.micronaut.context.exceptions.*;
import io.micronaut.context.processor.AnnotationProcessor;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.context.scope.CustomScopeRegistry;
import io.micronaut.core.annotation.*;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.core.util.*;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.core.value.ValueResolver;
import io.micronaut.inject.*;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualified;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.validation.BeanDefinitionValidator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
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

    final Map<BeanKey, BeanRegistration> singletonObjects = new ConcurrentHashMap<>(100);
    final Map<BeanIdentifier, Object> singlesInCreation = new ConcurrentHashMap<>(5);
    final Map<BeanKey, Provider<Object>> scopedProxies = new ConcurrentHashMap<>(20);

    Set<Map.Entry<Class, List<BeanInitializedEventListener>>> beanInitializedEventListeners;

    private final Collection<BeanDefinitionReference> beanDefinitionsClasses = new ConcurrentLinkedQueue<>();
    private final Map<String, BeanConfiguration> beanConfigurations = new HashMap<>(10);
    private final Map<BeanKey, Boolean> containsBeanCache = new ConcurrentHashMap<>(30);
    private final Map<CharSequence, Object> attributes = Collections.synchronizedMap(new HashMap<>(5));

    private final Map<BeanKey, Collection> initializedObjectsByType = new ConcurrentHashMap<>(50);
    private final Map<BeanKey, Optional<BeanDefinition>> beanConcreteCandidateCache =
            new ConcurrentLinkedHashMap.Builder<BeanKey, Optional<BeanDefinition>>().maximumWeightedCapacity(30).build();
    private final Map<Argument, Collection<BeanDefinition>> beanCandidateCache = new ConcurrentLinkedHashMap.Builder<Argument, Collection<BeanDefinition>>().maximumWeightedCapacity(30).build();
    private final Map<Class, Collection<BeanDefinitionReference>> beanIndex = new ConcurrentHashMap<>(12);

    private final ClassLoader classLoader;
    private final Set<Class> thisInterfaces = CollectionUtils.setOf(
            BeanDefinitionRegistry.class,
            BeanContext.class,
            AnnotationMetadataResolver.class,
            BeanLocator.class,
            ApplicationEventPublisher.class,
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
    private Set<Map.Entry<Class, List<BeanCreatedEventListener>>> beanCreationEventListeners;
    private BeanDefinitionValidator beanValidator;

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
        this.customScopeRegistry = new DefaultCustomScopeRegistry(this, classLoader);
        Set<Class<? extends Annotation>> eagerInitAnnotated = contextConfiguration.getEagerInitAnnotated();
        this.eagerInitStereotypes = eagerInitAnnotated
                .stream().map(Class::getName).toArray(String[]::new);
        this.eagerInitStereotypesPresent = eagerInitStereotypes.length > 0;
        this.eagerInitSingletons = eagerInitStereotypesPresent && eagerInitAnnotated.contains(Singleton.class);
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
            attributes.clear();

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
                try {
                    disposeBean(def.asArgument(), bean, def);
                } catch (BeanDestructionException e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }

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
        } else {
            Optional<? extends BeanDefinition<?>> candidate = findConcreteCandidate(
                    null,
                    Argument.of(type),
                    null,
                    false,
                    false
            );
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
        return result;
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
        return result;
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

            @SuppressWarnings("rawtypes")
            @Override
            public Object getTarget() {
                Object target = this.target;
                if (target == null) {
                    synchronized (this) { // double check
                        target = this.target;
                        if (target == null) {
                            try (BeanResolutionContext context = newResolutionContext(beanDefinition, null)) {
                                BeanDefinition rawDefinition = beanDefinition;
                                target = getBeanForDefinition(
                                        context,
                                        Argument.of(rawDefinition.getBeanType()),
                                        rawDefinition.getDeclaredQualifier(),
                                        true,
                                        rawDefinition
                                );
                            }

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
        ArgumentUtils.requireNonNull("type", type);
        ArgumentUtils.requireNonNull("singleton", singleton);
        BeanKey<T> beanKey = new BeanKey<>(type, qualifier);
        synchronized (singletonObjects) {

            initializedObjectsByType.clear();
            beanCandidateCache.remove(Argument.of(type));
            BeanDefinition<T> beanDefinition = inject ? findConcreteCandidate(
                    null,
                    beanKey.beanType,
                    qualifier,
                    false,
                    false).orElse(null) : null;
            if (beanDefinition != null && beanDefinition.getBeanType().isInstance(singleton)) {
                try (BeanResolutionContext context = newResolutionContext(beanDefinition, null)) {
                    doInject(context, singleton, beanDefinition);
                }
                singletonObjects.put(beanKey, new BeanRegistration<>(beanKey, beanDefinition, singleton));
                BeanKey concreteKey = new BeanKey(singleton.getClass(), qualifier);
                singletonObjects.put(concreteKey, new BeanRegistration<>(concreteKey, beanDefinition, singleton));
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

    @NonNull
    private BeanResolutionContext newResolutionContext(BeanDefinition<?> beanDefinition, @Nullable BeanResolutionContext currentContext) {
        if (currentContext == null) {
            return new AbstractBeanResolutionContext(this, beanDefinition) {
                @Override
                public <T> void addInFlightBean(BeanIdentifier beanIdentifier, T instance) {
                    singlesInCreation.put(beanIdentifier, instance);
                }

                @Override
                public void removeInFlightBean(BeanIdentifier beanIdentifier) {
                    singlesInCreation.remove(beanIdentifier);
                }

                @Nullable
                @Override
                public <T> T getInFlightBean(BeanIdentifier beanIdentifier) {
                    return (T) singlesInCreation.get(beanIdentifier);
                }
            };
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
        return findConcreteCandidate(null, beanType, qualifier, true, false)
                .orElseThrow(() -> new NoSuchBeanException(beanType, qualifier));
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Argument<T> beanType, Qualifier<T> qualifier) {
        if (Objects.requireNonNull(beanType, "Bean type cannot be null").equalsType(Argument.OBJECT_ARGUMENT)) {
            // optimization for object resolve
            return Optional.empty();
        }

        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);
        @SuppressWarnings("unchecked") BeanRegistration<T> reg = singletonObjects.get(beanKey);
        if (reg != null) {
            return Optional.of(reg.getBeanDefinition());
        }
        Collection<BeanDefinition<T>> beanCandidates = new ArrayList<>(findBeanCandidatesInternal(null, beanKey.beanType));
        if (qualifier != null) {
            beanCandidates = qualifier.reduce(beanType.getType(), beanCandidates.stream()).collect(Collectors.toList());
        }
        filterProxiedTypes(beanCandidates, true, true);
        if (beanCandidates.isEmpty()) {
            return Optional.empty();
        } else {
            if (beanCandidates.size() == 1) {
                return Optional.of(beanCandidates.iterator().next());
            } else {
                return findConcreteCandidate(null, beanKey.beanType, null, false, true);
            }
        }
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType, Qualifier<T> qualifier) {
        return findBeanDefinition(
                Argument.of(beanType),
                qualifier
        );
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType) {
        return getBeanDefinitions(Argument.of(beanType));
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> beanType) {
        Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(
                null,
                Objects.requireNonNull(beanType, "Bean type cannot be null")
        );
        return Collections.unmodifiableCollection(candidates);
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType, Qualifier<T> qualifier) {
        return getBeanDefinitions(
                Argument.of(Objects.requireNonNull(beanType, "Bean type cannot be null")),
                qualifier
        );
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
            boolean result = singletonObjects.containsKey(beanKey) ||
                    isCandidatePresent(beanKey.beanType, qualifier);

            containsBeanCache.put(beanKey, result);
            return result;
        }
    }

    @Override
    public @NonNull
    <T> T getBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        try {
            //noinspection ConstantConditions
            return getBeanInternal(
                    null,
                    Argument.of(beanType),
                    qualifier,
                    true,
                    true
            );
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getSimpleName(), e.getMessage());
            }
            throw new NoSuchBeanException(beanType, qualifier);
        }
    }

    @Override
    public @NonNull
    <T> T getBean(@NonNull Class<T> beanType) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        try {
            //noinspection ConstantConditions
            return getBeanInternal(
                    null,
                    Argument.of(beanType),
                    null,
                    true,
                    true
            );
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getSimpleName(), e.getMessage());
            }
            throw new NoSuchBeanException(beanType, null);
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
        return getBeanRegistrations(null, Argument.of(beanType), qualifier)
                .stream()
                .map(BeanRegistration::getBean)
                .collect(Collectors.toList());
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
    protected <T> Stream<T> streamOfType(BeanResolutionContext resolutionContext, Argument<T> beanType, Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeanRegistrations(resolutionContext, beanType, qualifier).stream()
                .map(BeanRegistration::getBean);
    }

    @Override
    public @NonNull
    <T> T inject(@NonNull T instance) {
        Objects.requireNonNull(instance, "Instance cannot be null");

        Collection<BeanDefinition> candidates = findBeanCandidatesForInstance(instance);
        if (candidates.size() == 1) {
            BeanDefinition<T> beanDefinition = candidates.stream().findFirst().get();
            try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
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
            }

        } else if (!candidates.isEmpty()) {
            final Iterator iterator = candidates.iterator();
            throw new NonUniqueBeanException(instance.getClass(), iterator);
        }
        return instance;
    }

    @Override
    public @NonNull
    <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return createBean(null, beanType, qualifier);
    }

    @Override
    public @NonNull
    <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Map<String, Object> argumentValues) {
        ArgumentUtils.requireNonNull("beanType", beanType);

        final Argument<T> beanArg = Argument.of(beanType);
        Optional<BeanDefinition<T>> candidate = findConcreteCandidate(
                null,
                beanArg,
                qualifier,
                true,
                false
        );
        if (candidate.isPresent()) {
            try (BeanResolutionContext resolutionContext = newResolutionContext(candidate.get(), null)) {
                T createdBean = doCreateBean(
                        resolutionContext,
                        candidate.get(),
                        qualifier,
                        beanArg,
                        false,
                        argumentValues
                );
                if (createdBean == null) {
                    throw new NoSuchBeanException(beanType);
                }
                return createdBean;
            }
        }
        throw new NoSuchBeanException(beanType);
    }

    @Override
    public @NonNull
    <T> T createBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable Object... args) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        final Argument<T> beanArg = Argument.of(beanType);
        Optional<BeanDefinition<T>> candidate = findConcreteCandidate(
                null,
                beanArg,
                qualifier,
                true,
                false
        );
        if (candidate.isPresent()) {
            BeanDefinition<T> definition = candidate.get();
            try (BeanResolutionContext resolutionContext = newResolutionContext(definition, null)) {
                return doCreateBean(
                        resolutionContext,
                        definition,
                        Argument.of(beanType),
                        qualifier,
                        args
                );
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
    protected @NonNull
    <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                       @NonNull BeanDefinition<T> definition,
                       @NonNull Argument<T> beanType,
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
                        } else {
                            if (!requiredArgument.isDeclaredNullable()) {
                                throw new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. Argument cannot be null");
                            }
                        }
                    } else {
                        // attempt resolve from context
                        Optional<?> existingBean = findBean(resolutionContext, argumentType, null);
                        if (existingBean.isPresent()) {
                            argumentValues.put(requiredArgument.getName(), existingBean.get());
                        } else {
                            if (!requiredArgument.isDeclaredNullable()) {
                                throw new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. No bean found for type: " + argumentType);
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
        T createdBean = doCreateBean(resolutionContext, definition, qualifier, beanType, false, argumentValues);
        if (createdBean == null) {
            throw new NoSuchBeanException(beanType);
        }
        return createdBean;
    }

    @Override
    public <T> T destroyBean(Argument<T> beanType, Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        T bean = null;
        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);

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
            Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(
                    null,
                    beanKey.beanType,
                    null,
                    false,
                    true
            );
            T finalBean = bean;
            concreteCandidate.ifPresent(definition -> {
                disposeBean(beanType, finalBean, definition);
            });
        }
        return bean;
    }

    @Override
    @NonNull
    public <T> T destroyBean(T bean) {
        Objects.requireNonNull(bean, "Bean cannot be null");
        final Argument arg = Argument.of(bean.getClass());
        final BeanDefinition concreteCandidate = (BeanDefinition) findConcreteCandidate(
                null,
                arg,
                null,
                false,
                false
        ).orElse(null);
        if (concreteCandidate != null) {
            disposeBean(arg, bean, concreteCandidate);
        }
        return bean;
    }

    @Override
    public @Nullable
    <T> T destroyBean(@NonNull Class<T> beanType) {
        return destroyBean(Argument.of(beanType), null);
    }

    private <T> void disposeBean(Argument<T> beanType, T finalBean, BeanDefinition<T> definition) {
        final List<BeanPreDestroyEventListener> preDestroyEventListeners = resolveListeners(
                BeanPreDestroyEventListener.class, beanType
        );

        T beanToDestroy = finalBean;
        if (CollectionUtils.isNotEmpty(preDestroyEventListeners)) {
            for (BeanPreDestroyEventListener<T> listener : preDestroyEventListeners) {
                try {
                    final BeanPreDestroyEvent<T> event =
                            new BeanPreDestroyEvent<>(this, definition, beanToDestroy);
                    beanToDestroy = Objects.requireNonNull(
                            listener.onPreDestroy(event),
                            "PreDestroy event listener illegally returned null: " + listener.getClass()
                    );
                } catch (Exception e) {
                    throw new BeanDestructionException(definition, e);
                }
            }
        }
        if (definition instanceof DisposableBeanDefinition) {
            try {
                ((DisposableBeanDefinition<T>) definition).dispose(this, finalBean);
            } catch (Exception e) {
                throw new BeanDestructionException(definition, e);
            }
        }

        if (beanToDestroy instanceof Closeable) {
            try {
                ((Closeable) beanToDestroy).close();
            } catch (Throwable e) {
                throw new BeanDestructionException(definition, e);
            }
        }
        if (beanToDestroy instanceof LifeCycle) {
            ((LifeCycle) beanToDestroy).stop();
        }

        final List<BeanDestroyedEventListener> postDestroyListeners = resolveListeners(
                BeanDestroyedEventListener.class, beanType
        );
        if (CollectionUtils.isNotEmpty(postDestroyListeners)) {
            for (BeanDestroyedEventListener<T> listener : postDestroyListeners) {
                try {
                    final BeanDestroyedEvent<T> event =
                            new BeanDestroyedEvent<>(this, definition, beanToDestroy);
                    listener.onDestroyed(event);
                } catch (Exception e) {
                    throw new BeanDestructionException(definition, e);
                }
            }
        }
    }

    @NonNull
    private <T extends EventListener> List<T> resolveListeners(Class<T> type, Argument<?> genericType) {
        final Collection<BeanDefinition<T>> preDestroyListeners =
                getBeanDefinitions(type);

        if (CollectionUtils.isNotEmpty(preDestroyListeners)) {

            final Qualifier<T> q =
                    Qualifiers.byGenerics(genericType.getType());
            final Stream<BeanDefinition<T>> listenerStream = preDestroyListeners
                    .stream();
            return q.reduce(type, listenerStream)
                    .map(this::getBean)
                    .sorted(OrderUtil.COMPARATOR)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
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
    protected @NonNull
    <T> T createBean(@Nullable BeanResolutionContext resolutionContext, @NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);

        final Argument<T> beanArgument = Argument.of(beanType);
        Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(
                resolutionContext,
                beanArgument,
                qualifier,
                true,
                false
        );
        if (concreteCandidate.isPresent()) {
            BeanDefinition<T> candidate = concreteCandidate.get();
            try (BeanResolutionContext context = newResolutionContext(candidate, resolutionContext)) {
                T createBean = doCreateBean(
                        context,
                        candidate,
                        qualifier,
                        beanArgument,
                        false, null
                );
                if (createBean == null) {
                    throw new NoSuchBeanException(beanType);
                }
                return createBean;
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
    protected @NonNull
    <T> T inject(@NonNull BeanResolutionContext resolutionContext, @Nullable BeanDefinition requestingBeanDefinition, @NonNull T instance) {
        @SuppressWarnings("unchecked") Class<T> beanType = (Class<T>) instance.getClass();
        Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(
                resolutionContext,
                Argument.of(beanType),
                null,
                false,
                true
        );
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
    protected @NonNull
    <T> Collection<T> getBeansOfType(@Nullable BeanResolutionContext resolutionContext, @NonNull Argument<T> beanType) {
        return getBeanRegistrations(resolutionContext, beanType, null)
                .stream().map(BeanRegistration::getBean)
                .collect(Collectors.toList());
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
    protected @NonNull
    <T> Collection<T> getBeansOfType(
            @Nullable BeanResolutionContext resolutionContext,
            @NonNull Argument<T> beanType,
            @Nullable Qualifier<T> qualifier) {
        return getBeanRegistrations(resolutionContext, beanType, qualifier)
                .stream().map(BeanRegistration::getBean)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull
    <T> T getProxyTargetBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        try (BeanResolutionContext resolutionContext = newResolutionContext(definition, null)) {

            return getBeanForDefinition(
                    resolutionContext,
                    Argument.of(beanType),
                    proxyQualifier,
                    true,
                    definition
            );
        }
    }

    @Override
    public @NonNull
    <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(@NonNull Class<T> beanType, @NonNull String method, @NonNull Class[] arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, null);
        return definition.findMethod(method, arguments);
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(Class<T> beanType, Qualifier<T> qualifier, String method, Class... arguments) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        ArgumentUtils.requireNonNull("method", method);
        BeanDefinition<T> definition = getProxyTargetBeanDefinition(beanType, qualifier);
        return definition.findMethod(method, arguments);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @NonNull
    <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findProxyTargetBeanDefinition(
                Argument.of(beanType),
                qualifier
        );
    }

    @Override
    public <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(Argument<T> beanType, Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        BeanKey<T> key = new BeanKey<>(beanType, proxyQualifier);

        Optional beanDefinition = beanConcreteCandidateCache.get(key);
        //noinspection OptionalAssignedToNull
        if (beanDefinition == null) {
            BeanRegistration<T> beanRegistration = singletonObjects.get(key);
            if (beanRegistration != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Resolved existing bean [{}] for type [{}] and qualifier [{}]", beanRegistration.bean, beanType, qualifier);
                }
                beanDefinition = Optional.of(beanRegistration.beanDefinition);
            } else {
                beanDefinition = findConcreteCandidateNoCache(
                        null,
                        beanType,
                        proxyQualifier,
                        true,
                        false
                );
            }

            beanConcreteCandidateCache.put(key, beanDefinition);
        }
        return beanDefinition;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull
    Collection<BeanDefinition<?>> getBeanDefinitions(@Nullable Qualifier<Object> qualifier) {
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
            filterProxiedTypes(candidates, true, true);
            filterReplacedBeans(null, candidates);
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull
    Collection<BeanDefinition<?>> getAllBeanDefinitions() {
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
    @Override
    public @NonNull
    Collection<BeanDefinitionReference<?>> getBeanDefinitionReferences() {
        if (!beanDefinitionsClasses.isEmpty()) {
            final List refs = beanDefinitionsClasses.stream().filter(ref -> ref.isEnabled(this))
                    .collect(Collectors.toList());

            return Collections.unmodifiableList(refs);
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
    public @NonNull
    <T> T getBean(@Nullable BeanResolutionContext resolutionContext, @NonNull Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        //noinspection ConstantConditions
        return getBeanInternal(
                resolutionContext,
                Argument.of(beanType),
                null,
                true,
                true
        );
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull BeanDefinition<T> definition) {
        ArgumentUtils.requireNonNull("definition", definition);
        Qualifier<T> declaredQualifier = definition.getDeclaredQualifier();
        BeanKey<T> key = new BeanKey<>(definition, declaredQualifier);
        if (definition.isSingleton()) {
            BeanRegistration beanRegistration = singletonObjects.get(key);
            if (beanRegistration != null) {
                return (T) beanRegistration.bean;
            }
        }
        try (BeanResolutionContext context = newResolutionContext(definition, null)) {
            return getBeanForDefinition(
                    context,
                    key.beanType,
                    declaredQualifier,
                    true,
                    definition
            );
        }
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
    public @NonNull
    <T> T getBean(@Nullable BeanResolutionContext resolutionContext, @NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBean(
                resolutionContext,
                Argument.of(beanType),
                qualifier
        );
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
    public @NonNull
    <T> T getBean(@Nullable BeanResolutionContext resolutionContext, @NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        final T bean = getBeanInternal(
                resolutionContext,
                beanType,
                qualifier,
                true,
                true
        );
        if (bean == null) {
            throw new NoSuchBeanException(beanType, qualifier);
        }
        return bean;
    }

    @Override
    public <T> T getBean(Argument<T> beanType, Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        final T bean = getBeanInternal(
                null,
                beanType,
                qualifier,
                true,
                true
        );
        if (bean == null) {
            throw new NoSuchBeanException(beanType, qualifier);
        }
        return bean;
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
    public @NonNull
    <T> Optional<T> findBean(@Nullable BeanResolutionContext resolutionContext, @NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findBean(
                resolutionContext,
                Argument.of(beanType),
                qualifier
        );
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
    public @NonNull
    <T> Optional<T> findBean(@Nullable BeanResolutionContext resolutionContext, @NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        // allow injection the bean context
        if (thisInterfaces.contains(beanType.getType())) {
            return Optional.of((T) this);
        }

        try {
            T bean = getBeanInternal(
                    resolutionContext,
                    beanType,
                    qualifier,
                    true,
                    false
            );
            if (bean == null) {
                return Optional.empty();
            } else {
                return Optional.of(bean);
            }
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getSimpleName(), e.getMessage());
            }
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void publishEvent(@NonNull Object event) {
        //noinspection ConstantConditions
        if (event != null) {
            if (EVENT_LOGGER.isDebugEnabled()) {
                EVENT_LOGGER.debug("Publishing event: {}", event);
            }
            Collection<ApplicationEventListener> eventListeners = getBeansOfType(ApplicationEventListener.class, Qualifiers.byTypeArguments(event.getClass()));

            eventListeners = eventListeners.stream().sorted(OrderUtil.COMPARATOR).collect(Collectors.toList());

            notifyEventListeners(event, eventListeners);
        }
    }

    private void notifyEventListeners(@NonNull Object event, Collection<ApplicationEventListener> eventListeners) {
        if (!eventListeners.isEmpty()) {
            if (EVENT_LOGGER.isTraceEnabled()) {
                EVENT_LOGGER.trace("Established event listeners {} for event: {}", eventListeners, event);
            }
            for (ApplicationEventListener listener : eventListeners) {
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
        }
    }

    @Override
    public @NonNull
    Future<Void> publishEventAsync(@NonNull Object event) {
        Objects.requireNonNull(event, "Event cannot be null");
        CompletableFuture<Void> future = new CompletableFuture<>();
        Collection<ApplicationEventListener> eventListeners = streamOfType(ApplicationEventListener.class, Qualifiers.byTypeArguments(event.getClass()))
                .sorted(OrderUtil.COMPARATOR).collect(Collectors.toList());

        Executor executor = findBean(Executor.class, Qualifiers.byName("scheduled"))
                .orElseGet(ForkJoinPool::commonPool);
        executor.execute(() -> {
            try {
                notifyEventListeners(event, eventListeners);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public @NonNull
    <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
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
     * Resolves the {@link BeanDefinitionReference} class instances. Default implementation uses ServiceLoader pattern.
     *
     * @return The bean definition classes
     */
    protected @NonNull
    List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        final SoftServiceLoader<BeanDefinitionReference> definitions = SoftServiceLoader.load(BeanDefinitionReference.class, classLoader);
        List<ServiceDefinition<BeanDefinitionReference>> list = new ArrayList<>(300);
        for (ServiceDefinition<BeanDefinitionReference> definition : definitions) {
            list.add(definition);
        }
        return list.parallelStream()
                .filter(ServiceDefinition::isPresent)
                .map(ServiceDefinition::load)
                .filter(BeanDefinitionReference::isPresent)
                .collect(Collectors.toList());
    }

    /**
     * Resolves the {@link BeanConfiguration} class instances. Default implementation uses ServiceLoader pattern.
     *
     * @return The bean definition classes
     */
    protected @NonNull
    Iterable<BeanConfiguration> resolveBeanConfigurations() {
        final SoftServiceLoader<BeanConfiguration> definitions = SoftServiceLoader.load(BeanConfiguration.class, classLoader);
        List<ServiceDefinition<BeanConfiguration>> list = new ArrayList<>(300);
        for (ServiceDefinition<BeanConfiguration> definition : definitions) {
            list.add(definition);
        }
        return list.parallelStream()
                .filter(ServiceDefinition::isPresent)
                .map(ServiceDefinition::load)
                .collect(Collectors.toList());
    }

    /**
     * Initialize the event listeners.
     */
    protected void initializeEventListeners() {
        final Collection<BeanDefinition<BeanCreatedEventListener>> beanCreatedDefinitions = getBeanDefinitions(BeanCreatedEventListener.class);
        final HashMap<Class, List<BeanCreatedEventListener>> beanCreatedListeners = new HashMap<>(beanCreatedDefinitions.size());

        //noinspection ArraysAsListWithZeroOrOneArgument
        beanCreatedListeners.put(AnnotationProcessor.class, Arrays.asList(new AnnotationProcessorListener()));
        for (BeanDefinition<BeanCreatedEventListener> beanCreatedDefinition : beanCreatedDefinitions) {
            try (BeanResolutionContext context = newResolutionContext(beanCreatedDefinition, null)) {
                final BeanCreatedEventListener listener;
                final Qualifier qualifier = beanCreatedDefinition.getDeclaredQualifier();
                if (beanCreatedDefinition.isSingleton()) {
                    listener = createAndRegisterSingleton(
                            context,
                            beanCreatedDefinition,
                            beanCreatedDefinition.getBeanType(),
                            qualifier
                    );
                } else {
                    listener = doCreateBean(
                            context,
                            beanCreatedDefinition,
                            Argument.of(BeanCreatedEventListener.class),
                            qualifier
                    );
                }
                List<Argument<?>> typeArguments = beanCreatedDefinition.getTypeArguments(BeanCreatedEventListener.class);
                Argument<?> argument = CollectionUtils.last(typeArguments);
                if (argument == null) {
                    argument = Argument.OBJECT_ARGUMENT;
                }
                beanCreatedListeners.computeIfAbsent(argument.getType(), aClass -> new ArrayList<>(10))
                        .add(listener);

            }
        }
        for (List<BeanCreatedEventListener> listenerList : beanCreatedListeners.values()) {
            OrderUtil.sort(listenerList);
        }

        final HashMap<Class, List<BeanInitializedEventListener>> beanInitializedListeners = new HashMap<>(beanCreatedDefinitions.size());
        final Collection<BeanDefinition<BeanInitializedEventListener>> beanInitializedDefinitions = getBeanDefinitions(BeanInitializedEventListener.class);
        for (BeanDefinition<BeanInitializedEventListener> definition : beanInitializedDefinitions) {
            try (BeanResolutionContext context = newResolutionContext(definition, null)) {
                final Qualifier qualifier = definition.getDeclaredQualifier();
                final BeanInitializedEventListener listener;
                if (definition.isSingleton()) {
                    listener = createAndRegisterSingleton(
                            context,
                            definition,
                            definition.getBeanType(),
                            qualifier
                    );
                } else {
                    listener = doCreateBean(
                            context,
                            definition,
                            Argument.of(BeanInitializedEventListener.class),
                            qualifier
                    );
                }
                List<Argument<?>> typeArguments = definition.getTypeArguments(BeanInitializedEventListener.class);
                Argument<?> argument = CollectionUtils.last(typeArguments);
                if (argument == null) {
                    argument = Argument.OBJECT_ARGUMENT;
                }
                beanInitializedListeners.computeIfAbsent(argument.getType(), aClass -> new ArrayList<>(10))
                        .add(listener);

            }
        }
        for (List<BeanInitializedEventListener> listenerList : beanInitializedListeners.values()) {
            OrderUtil.sort(listenerList);
        }

        this.beanCreationEventListeners = beanCreatedListeners.entrySet();
        this.beanInitializedEventListeners = beanInitializedListeners.entrySet();
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
            final Collection<BeanDefinition> contextBeans = new ArrayList<>(contextScopeBeans.size());

            for (BeanDefinitionReference contextScopeBean : contextScopeBeans) {
                try {
                    loadContextScopeBean(contextScopeBean, contextBeans::add);
                } catch (Throwable e) {
                    throw new BeanInstantiationException("Bean definition [" + contextScopeBean.getName() + "] could not be loaded: " + e.getMessage(), e);
                }
            }
            filterProxiedTypes((Collection) contextBeans, true, false);
            filterReplacedBeans(null, (Collection) contextBeans);

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
    protected @NonNull
    <T> Collection<BeanDefinition<T>> findBeanCandidates(@NonNull Class<T> beanType, @Nullable BeanDefinition<?> filter) {
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
    protected @NonNull
    <T> Collection<BeanDefinition<T>> findBeanCandidates(
            @Nullable BeanResolutionContext resolutionContext,
            @NonNull Argument<T> beanType,
            @Nullable BeanDefinition<?> filter,
            boolean filterProxied) {
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

        if (!beanDefinitionsClasses.isEmpty()) {

            Stream<BeanDefinition<T>> candidateStream = beanDefinitionsClasses
                    .stream()
                    .filter(reference ->
                            reference.isCandidateBean(beanType.getType()) && reference.isEnabled(this, resolutionContext)
                    )
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
                    .filter(candidate -> candidate.isEnabled(this, resolutionContext))
                    .collect(Collectors.toSet());

            if (!candidates.isEmpty()) {
                if (filterProxied) {
                    filterProxiedTypes(candidates, true, false);
                }
                filterReplacedBeans(resolutionContext, candidates);
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
    protected @NonNull
    <T> Collection<BeanDefinition> findBeanCandidatesForInstance(@NonNull T instance) {
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
                                            candidate.getBeanType() == beanClass
                            )
                            .collect(Collectors.toList());
                    beanDefinitions = candidates;
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
     * @param isSingleton       Whether the bean is a singleton
     * @param argumentValues    Any argument values passed to create the bean
     * @param <T>               The bean generic type
     * @return The created bean
     * @deprecated Use {@link #doCreateBean(BeanResolutionContext, BeanDefinition, Qualifier, Argument, boolean, Map)} instead.
     */
    @Nullable
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
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param beanDefinition    The {@link BeanDefinition}
     * @param qualifier         The {@link Qualifier}
     * @param qualifierBeanType The bean type used in the qualifier
     * @param isSingleton       Whether the bean is a singleton
     * @param argumentValues    Any argument values passed to create the bean
     * @param <T>               The bean generic type
     * @return The created bean
     */
    protected @Nullable
    <T> T doCreateBean(@NonNull BeanResolutionContext resolutionContext,
                       @NonNull BeanDefinition<T> beanDefinition,
                       @Nullable Qualifier<T> qualifier,
                       @Nullable Argument<T> qualifierBeanType,
                       boolean isSingleton,
                       @Nullable Map<String, Object> argumentValues) {
        if (argumentValues == null) {
            argumentValues = Collections.emptyMap();
        }
        Qualifier<T> resolvedQualifier = getQualifierForBeanType(qualifierBeanType, qualifier);
        Qualifier<T> declaredQualifier = beanDefinition.getDeclaredQualifier();
        Class<T> beanType = beanDefinition.getBeanType();
        if (isSingleton) {
            BeanRegistration<T> beanRegistration = singletonObjects.get(new BeanKey<>(beanDefinition, declaredQualifier));
            final Class<T> beanClass = qualifierBeanType.getType();
            if (beanRegistration != null) {
                if (resolvedQualifier == null || resolvedQualifier.reduce(beanClass, Stream.of(beanRegistration.beanDefinition)).findFirst().isPresent()) {
                    return beanRegistration.bean;
                }
            } else if (resolvedQualifier != null) {
                beanRegistration = singletonObjects.get(new BeanKey<>(beanDefinition, null));
                if (beanRegistration != null && resolvedQualifier.reduce(beanClass, Stream.of(beanRegistration.beanDefinition)).findFirst().isPresent()) {
                    return beanRegistration.bean;
                }
            }
        }

        T bean;
        if (beanDefinition instanceof BeanFactory) {
            BeanFactory<T> beanFactory = (BeanFactory<T>) beanDefinition;
            try {
                if (beanFactory instanceof ParametrizedBeanFactory) {
                    ParametrizedBeanFactory<T> parametrizedBeanFactory = (ParametrizedBeanFactory<T>) beanFactory;
                    Argument<?>[] requiredArguments = parametrizedBeanFactory.getRequiredArguments();
                    Map<String, Object> convertedValues = new LinkedHashMap<>(argumentValues);
                    for (Argument<?> requiredArgument : requiredArguments) {
                        String argumentName = requiredArgument.getName();
                        Object val = argumentValues.get(argumentName);
                        if (val == null && !requiredArgument.isDeclaredNullable()) {
                            throw new BeanInstantiationException(resolutionContext, "Missing bean argument [" + requiredArgument + "] for type: " + beanType.getName() + ". Required arguments: " + ArrayUtils.toString(requiredArguments));
                        }
                        Object convertedValue = null;
                        if (val != null) {
                            if (requiredArgument.getType().isInstance(val)) {
                                convertedValue = val;
                            } else {
                                convertedValue = ConversionService.SHARED.convert(val, requiredArgument).orElseThrow(() ->
                                        new BeanInstantiationException(resolutionContext, "Invalid bean argument [" + requiredArgument + "]. Cannot convert object [" + val + "] to required type: " + requiredArgument.getType())
                                );
                            }
                        }
                        convertedValues.put(argumentName, convertedValue);
                    }

                    bean = parametrizedBeanFactory.build(
                            resolutionContext,
                            this,
                            beanDefinition,
                            convertedValues
                    );
                } else {
                    boolean propagateQualifier = beanDefinition.isProxy() && declaredQualifier instanceof Named;
                    if (propagateQualifier) {
                        resolutionContext.setAttribute(BeanDefinition.NAMED_ATTRIBUTE, ((Named) declaredQualifier).getName());
                    }
                    resolutionContext.setCurrentQualifier(declaredQualifier != null ? declaredQualifier : qualifier);
                    try {
                        bean = beanFactory.build(resolutionContext, this, beanDefinition);
                    } finally {
                        resolutionContext.setCurrentQualifier(null);
                        if (propagateQualifier) {
                            resolutionContext.removeAttribute(BeanDefinition.NAMED_ATTRIBUTE);
                        }
                    }

                    if (bean == null) {
                        throw new BeanInstantiationException(resolutionContext, "Bean Factory [" + beanFactory + "] returned null");
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
                if (e instanceof DisabledBeanException) {
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
            Argument<?>[] requiredConstructorArguments = constructor.getArguments();
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
        }

        if (bean != null) {
            Qualifier<T> finalQualifier = qualifier != null ? qualifier : declaredQualifier;
            if (!BeanCreatedEventListener.class.isInstance(bean) && CollectionUtils.isNotEmpty(beanCreationEventListeners)) {
                for (Map.Entry<Class, List<BeanCreatedEventListener>> entry : beanCreationEventListeners) {
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
            if (beanDefinition instanceof ValidatedBeanDefinition) {
                bean = ((ValidatedBeanDefinition<T>) beanDefinition).validate(resolutionContext, bean);
            }
            if (LOG_LIFECYCLE.isDebugEnabled()) {
                LOG_LIFECYCLE.debug("Created bean [{}] from definition [{}] with qualifier [{}]", bean, beanDefinition, finalQualifier);
            }
        }

        return bean;
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
    protected @NonNull
    <T> BeanDefinition<T> findConcreteCandidate(
            @NonNull Class<T> beanType,
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
                            synchronized (singletonObjects) {
                                loadContextScopeBean(beanDefinitionReference, parallelDefinitions::add);
                            }
                        } catch (Throwable e) {
                            LOG.error("Parallel Bean definition [" + beanDefinitionReference.getName() + "] could not be loaded: " + e.getMessage(), e);
                            Boolean shutdownOnError = beanDefinitionReference.getAnnotationMetadata().booleanValue(Parallel.class, "shutdownOnError").orElse(true);
                            if (shutdownOnError) {
                                stop();
                            }
                        }
                    });

                    filterProxiedTypes((Collection) parallelDefinitions, true, false);
                    filterReplacedBeans(null, (Collection) parallelDefinitions);

                    parallelDefinitions.forEach(beanDefinition -> ForkJoinPool.commonPool().execute(() -> {
                        try {
                            synchronized (singletonObjects) {
                                loadContextScopeBean(beanDefinition);
                            }
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

                candidates.removeIf(definition -> {
                    if (!definition.isEnabled(this, resolutionContext)) {
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
                    return replacementTypes.stream().anyMatch(replacingCandidate -> {
                        if (definition == replacingCandidate) {
                            // don't replace yourself
                            return false;
                        }
                        if (replacingCandidate instanceof ProxyBeanDefinition && ((ProxyBeanDefinition<T>) replacingCandidate).getTargetDefinitionType() == definition.getClass()) {
                            return false;
                        }

                        final AnnotationValue<Replaces> replacesAnn = replacingCandidate.getAnnotation(Replaces.class);
                        Class beanType = replacesAnn.classValue().orElse(getCanonicalBeanType(replacingCandidate));
                        Optional<Class<?>> factory = replacesAnn.classValue("factory");
                        if (replacesAnn.contains(NAMED_MEMBER)) {

                            final String qualifier = replacesAnn.stringValue(NAMED_MEMBER).orElse(null);
                            if (qualifier != null) {
                                final Optional qualified = Qualifiers.<T>byName(qualifier)
                                        .qualify(beanType, Stream.of(definition));
                                return qualified.isPresent();
                            }
                        }

                        if (LOG.isDebugEnabled()) {
                            if (factory.isPresent()) {
                                LOG.debug("Bean [{}] replaces existing bean of type [{}] in factory type [{}]", replacingCandidate.getBeanType(), beanType, factory.get());
                            } else {
                                LOG.debug("Bean [{}] replaces existing bean of type [{}]", replacingCandidate.getBeanType(), beanType);
                            }
                        }
                        if (factory.isPresent() && finalDeclaringType.isPresent()) {
                            if (factory.get() == finalDeclaringType.get()) {
                                return comparisonFunction.apply(beanType);
                            } else {
                                return false;
                            }
                        } else {
                            return comparisonFunction.apply(beanType);
                        }
                    });
                });
            }
        }
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

    private <T> Function<Class, Boolean> typeMatches(BeanType<T> definition, AnnotationMetadata annotationMetadata) {
        Class<T> bt;

        if (definition instanceof ProxyBeanDefinition) {
            bt = ((ProxyBeanDefinition<T>) definition).getTargetType();
        } else if (definition instanceof AdvisedBeanType) {
            //noinspection unchecked
            bt = (Class<T>) ((AdvisedBeanType<T>) definition).getInterceptedType();
        } else {
            bt = definition.getBeanType();
            if (annotationMetadata.hasStereotype(INTRODUCTION_TYPE)) {
                Class<? super T> superclass = bt.getSuperclass();
                if (superclass == Object.class) {
                    // interface introduction
                    return clazz -> clazz.isAssignableFrom(bt);
                } else {
                    // abstract class introduction
                    return clazz -> clazz == superclass;
                }
            }
            if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_AROUND)) {
                Class<? super T> superclass = bt.getSuperclass();
                return clazz -> clazz == superclass || clazz == bt;
            }
        }

        if (annotationMetadata.hasAnnotation(DefaultImplementation.class)) {
            Optional<Class> defaultImpl = annotationMetadata.classValue(DefaultImplementation.class);
            if (!defaultImpl.isPresent()) {
                defaultImpl = annotationMetadata.classValue(DefaultImplementation.class, "name");
            }
            if (defaultImpl.filter(impl -> impl == bt).isPresent()) {
                return clazz -> clazz.isAssignableFrom(bt);
            } else {
                return clazz -> clazz == bt;
            }
        }

        return clazz -> clazz != Object.class && clazz.isAssignableFrom(bt);
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
                try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
                    createAndRegisterSingleton(
                            resolutionContext,
                            beanCandidate,
                            beanCandidate.getBeanType(),
                            beanCandidate.hasAnnotation(Context.class) ? null : beanDefinition.getDeclaredQualifier()
                    );
                }
            }

        } else {
            try (BeanResolutionContext resolutionContext = newResolutionContext(beanDefinition, null)) {
                createAndRegisterSingletonInternal(resolutionContext, beanDefinition, beanDefinition.asArgument(), null);
            }
        }
    }

    private <T> T getBeanInternal(
            @Nullable BeanResolutionContext resolutionContext,
            @NonNull Argument<T> beanType,
            Qualifier<T> qualifier,
            boolean throwNonUnique,
            boolean throwNoSuchBean) {
        // allow injection the bean context
        final Class<T> beanClass = beanType.getType();
        if (thisInterfaces.contains(beanClass)) {
            return (T) this;
        }

        if (beanClass == InjectionPoint.class) {
            final BeanResolutionContext.Path path = resolutionContext != null ? resolutionContext.getPath() : null;

            if (CollectionUtils.isNotEmpty(path)) {
                final Iterator<BeanResolutionContext.Segment<?>> i = path.iterator();
                final BeanResolutionContext.Segment<?> injectionPointSegment = i.next();
                if (i.hasNext()) {
                    BeanResolutionContext.Segment segment = i.next();
                    final BeanDefinition declaringBean = segment.getDeclaringType();
                    if (declaringBean.hasStereotype(INTRODUCTION_TYPE)) {
                        if (!i.hasNext()) {
                            if (!injectionPointSegment.getArgument().isNullable()) {
                                throw new BeanContextException("Failed to obtain injection point. No valid injection path present in path: " + path);
                            } else {
                                return null;
                            }
                        } else {
                            segment = i.next();
                        }
                    }
                    return (T) segment.getInjectionPoint();
                } else {
                    if (!injectionPointSegment.getArgument().isNullable()) {
                        throw new BeanContextException("Failed to obtain injection point. No valid injection path present in path: " + path);
                    } else {
                        return null;
                    }
                }
            } else {
                throw new BeanContextException("Failed to obtain injection point. No valid injection path present in path: " + path);
            }
        }
        final Qualifier<T> resolvedQualifier = getQualifierForBeanType(beanType, qualifier);
        BeanKey<T> beanKey = new BeanKey<>(beanType, resolvedQualifier);

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

            Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(
                    resolutionContext,
                    beanType,
                    qualifier,
                    throwNonUnique,
                    false
            );
            T bean;

            if (concreteCandidate.isPresent()) {
                BeanDefinition<T> definition = concreteCandidate.get();

                bean = findExistingCompatibleSingleton(
                        definition.asArgument(),
                        beanType,
                        qualifier,
                        definition
                );
                if (bean != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Resolved existing bean [{}] for type [{}] and qualifier [{}]", bean, beanType, qualifier);
                    }
                    return bean;
                }

                if (definition.isProvided() && beanType.getType() == definition.getBeanType()) {
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
                bean = findExistingCompatibleSingleton(beanType, null, qualifier, null);
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
            Argument<T> beanType,
            Qualifier<T> qualifier,
            boolean throwNoSuchBean,
            BeanDefinition<T> definition) {
        try (BeanResolutionContext context = newResolutionContext(definition, resolutionContext)) {
            final BeanResolutionContext.Path path = context.getPath();
            final boolean isNewPath = path.isEmpty();
            if (isNewPath) {
                path.pushBeanCreate(
                        definition,
                        beanType
                );
            }
            try {
                if (definition.isSingleton() && !definition.hasStereotype(SCOPED_PROXY_ANN)) {
                    return createAndRegisterSingleton(context, definition, beanType.getType(), qualifier);
                } else {
                    return getScopedBeanForDefinition(context, beanType, qualifier, throwNoSuchBean, definition);
                }
            } finally {
                if (isNewPath) {
                    path.pop();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getScopedBeanForDefinition(
            final @NonNull BeanResolutionContext resolutionContext,
            Argument<T> beanType,
            Qualifier<T> qualifier,
            boolean throwNoSuchBean,
            BeanDefinition<T> definition) {
        final boolean isProxy = definition.isProxy();
        final boolean isScopedProxyDefinition = definition.hasStereotype(SCOPED_PROXY_ANN);
        final Class<T> beanClass = beanType.getType();
        if (qualifier != PROXY_TARGET_QUALIFIER && isProxy && isScopedProxyDefinition) {
            Class<?> proxiedType = resolveProxiedType(beanClass, definition);
            BeanKey key = new BeanKey(proxiedType, qualifier);
            BeanDefinition<T> finalDefinition = definition;
            return (T) scopedProxies.computeIfAbsent(key, beanKey -> ProviderUtils.memoized(() -> {
                Qualifier<T> q = qualifier;
                if (q == null) {
                    q = finalDefinition.getDeclaredQualifier();
                }
                BeanDefinition<T> proxyDefinition = (BeanDefinition<T>) findProxyBeanDefinition((Class) proxiedType, q).orElse(finalDefinition);


                T createBean = doCreateBean(
                        resolutionContext,
                        proxyDefinition,
                        qualifier,
                        beanType,
                        false,
                        null
                );
                if (createBean instanceof Qualified) {
                    ((Qualified) createBean).$withBeanQualifier(qualifier);
                }
                if (createBean == null && throwNoSuchBean) {
                    throw new NoSuchBeanException(proxyDefinition.getBeanType(), qualifier);
                }
                return createBean;
            })).get();
        } else {
            Optional<BeanResolutionContext.Segment<?>> currentSegment = resolutionContext.getPath().currentSegment();
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
                    definition = getProxyTargetBeanDefinition(
                            beanType,
                            qualifier
                    );
                }
                BeanDefinition<T> finalDefinition = definition;

                final BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);
                return (T) customScope.get(
                        resolutionContext,
                        finalDefinition,
                        beanKey,
                        new ParametrizedProvider() {
                            @Override
                            public Object get(Map argumentValues) {
                                Object createBean = doCreateBean(
                                        resolutionContext,
                                        finalDefinition,
                                        qualifier,
                                        beanType,
                                        false,
                                        argumentValues
                                );
                                if (createBean == null && throwNoSuchBean) {
                                    throw new NoSuchBeanException(finalDefinition.getBeanType(), qualifier);
                                }
                                return createBean;
                            }

                            @Override
                            public Object get(Object... argumentValues) {
                                T createdBean = doCreateBean(
                                        resolutionContext,
                                        finalDefinition,
                                        beanType,
                                        qualifier,
                                        argumentValues
                                );
                                if (createdBean == null && throwNoSuchBean) {
                                    throw new NoSuchBeanException(finalDefinition.getBeanType(), qualifier);
                                }
                                return createdBean;
                            }
                        }
                );
            } else {
                T createBean = doCreateBean(
                        resolutionContext,
                        definition,
                        qualifier,
                        beanType,
                        false,
                        null
                );
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

    private <T> T findExistingCompatibleSingleton(
            @NonNull Argument<T> beanType,
            @Nullable Argument<?> requestedType,
            @Nullable Qualifier<T> qualifier,
            @Nullable BeanDefinition<T> definition) {
        T bean = null;
        final Class<T> beanClass = beanType.getType();
        for (Map.Entry<BeanKey, BeanRegistration> entry : singletonObjects.entrySet()) {
            BeanKey<?> key = entry.getKey();
            if (qualifier == null || qualifier.equals(key.qualifier)) {
                BeanRegistration reg = entry.getValue();
                if (beanClass.isInstance(reg.bean)) {
                    final Set exposedTypes = reg.getBeanDefinition().getExposedTypes();
                    if (!exposedTypes.isEmpty() && !exposedTypes.contains(beanClass)) {
                        continue;
                    }

                    if (qualifier == null && definition != null && !reg.beanDefinition.equals(definition)) {
                        // different definition, so ignore
                        continue;
                    }
                    synchronized (singletonObjects) {
                        bean = (T) reg.bean;
                        registerSingletonBean(
                                reg.beanDefinition,
                                beanType,
                                bean,
                                qualifier,
                                true
                        );
                        break;
                    }
                }
            } else if (key.qualifier == null) {
                BeanRegistration registration = entry.getValue();
                Object existing = registration.bean;
                if (beanType.getType().isInstance(registration.bean)) {
                    Optional<BeanDefinition<T>> candidate = qualifier.qualify(beanClass, Stream.of(registration.beanDefinition));
                    if (!candidate.isPresent() && requestedType != null && requestedType != beanType) {
                        candidate = qualifier.qualify((Class<T>) requestedType.getType(), Stream.of(registration.beanDefinition));
                    }
                    if (candidate.isPresent()) {
                        synchronized (singletonObjects) {
                            bean = (T) existing;
                            final BeanDefinition<T> beanDefinition = candidate.get();
                            final Set<Class<?>> exposedTypes = beanDefinition.getExposedTypes();
                            if (!exposedTypes.isEmpty() && !exposedTypes.contains(beanClass)) {
                                continue;
                            }
                            registerSingletonBean(
                                    beanDefinition,
                                    beanType,
                                    bean,
                                    qualifier,
                                    true
                            );
                            break;
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Optional<BeanDefinition<T>> findConcreteCandidate(
            BeanResolutionContext resolutionContext,
            Argument<T> beanType,
            Qualifier<T> qualifier,
            boolean throwNonUnique,
            boolean includeProvided) {
        BeanKey bk = new BeanKey(beanType, qualifier);
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

    private <T> Optional<BeanDefinition<T>> findConcreteCandidateNoCache(
            BeanResolutionContext resolutionContext,
            Argument<T> beanType,
            Qualifier<T> qualifier,
            boolean throwNonUnique,
            boolean filterProxied) {
        Collection<BeanDefinition<T>> candidates = new ArrayList<>(findBeanCandidates(resolutionContext, beanType, null, filterProxied));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Qualifier<T> finalQualifier = getQualifierForBeanType(beanType, qualifier);
        filterProxiedTypes(candidates, filterProxied, false);

        int size = candidates.size();
        BeanDefinition<T> definition = null;
        if (size > 0) {
            if (finalQualifier != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Qualifying bean [{}] for qualifier: {} ", beanType.getName(), finalQualifier);
                }
                Stream<BeanDefinition<T>> candidateStream = candidates.stream().filter(c -> {
                    if (!c.isAbstract()) {
                        if (c instanceof NoInjectionBeanDefinition) {
                            NoInjectionBeanDefinition noInjectionBeanDefinition = (NoInjectionBeanDefinition) c;
                            return finalQualifier.contains(noInjectionBeanDefinition.qualifier);
                        }
                        return true;
                    }
                    return false;
                });


                Stream<BeanDefinition<T>> qualified = finalQualifier.reduce(beanType.getType(), candidateStream);
                List<BeanDefinition<T>> beanDefinitionList = qualified.collect(Collectors.toList());
                if (beanDefinitionList.isEmpty()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("No qualifying beans of type [{}] found for qualifier: {} ", beanType.getName(), finalQualifier);
                    }
                    return Optional.empty();
                }

                definition = lastChanceResolve(
                        beanType,
                        finalQualifier,
                        qualifier,
                        throwNonUnique,
                        beanDefinitionList
                );
            } else {
                candidates.removeIf(BeanDefinition::isAbstract);
                if (candidates.size() == 1) {
                    definition = candidates.iterator().next();
                } else {

                    if (candidates.isEmpty()) {
                        throw new NoSuchBeanException(beanType, null);
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Searching for @Primary for type [{}] from candidates: {} ", beanType.getName(), candidates);
                        }
                        definition = lastChanceResolve(
                                beanType,
                                null,
                                qualifier,
                                throwNonUnique,
                                candidates
                        );
                    }
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

    private <T> Qualifier<T> getQualifierForBeanType(Argument<T> beanType, Qualifier<T> qualifier) {
        final Argument<?>[] typeParameters = beanType.getTypeParameters();

        if (qualifier instanceof AnyQualifier) {
            qualifier = null;
        }

        if (ArrayUtils.isNotEmpty(typeParameters)) {
            if (qualifier != null) {
                qualifier = Qualifiers.byQualifiers(
                        qualifier,
                        getTypeArgumentQualifier(typeParameters)
                );
            } else {
                qualifier = getTypeArgumentQualifier(typeParameters);
            }
        }
        return qualifier;
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

    private <T> BeanDefinition<T> lastChanceResolve(
            Argument<T> beanType,
            Qualifier<T> qualifier,
            Qualifier<T> specifiedQualifier,
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
        } else {
            BeanDefinition<T> definition = null;
            candidates = candidates.stream().filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class)).collect(Collectors.toList());
            if (candidates.size() == 1) {
                return candidates.iterator().next();
            } else {
                Collection<BeanDefinition<T>> exactMatches = filterExactMatch(beanClass, candidates);
                if (exactMatches.size() == 1) {
                    definition = exactMatches.iterator().next();
                } else if (throwNonUnique) {
                    definition = findConcreteCandidate(beanClass, specifiedQualifier, candidates);
                }
                return definition;
            }
        }
    }

    @NotNull
    private <T> Qualifier<T> getTypeArgumentQualifier(Argument[] typeParameters) {
        return Qualifiers.byGenerics(
                Arrays.stream(typeParameters).map(TypeInformation::getType)
                        .toArray(Class[]::new)
        );
    }

    private <T> T createAndRegisterSingleton(
            BeanResolutionContext resolutionContext,
            BeanDefinition<T> definition,
            Class<T> beanType,
            Qualifier<T> qualifier) {
        synchronized (singletonObjects) {
            return createAndRegisterSingletonInternal(resolutionContext, definition, Argument.of(beanType), qualifier);
        }
    }

    private <T> T createAndRegisterSingletonInternal(BeanResolutionContext resolutionContext, BeanDefinition<T> definition, Argument<T> beanType, Qualifier<T> qualifier) {
        if (definition instanceof NoInjectionBeanDefinition) {
            NoInjectionBeanDefinition<T> manuallyRegistered = (NoInjectionBeanDefinition) definition;
            BeanRegistration<T> reg = singletonObjects.get(new BeanKey(manuallyRegistered.getBeanType(), manuallyRegistered.getQualifier()));
            if (reg == null) {
                throw new IllegalStateException("Manually registered singleton no longer present in bean context");
            }
            registerSingletonBean(definition, beanType, reg.bean, qualifier, true);
            return reg.bean;
        } else {
            T createdBean = doCreateBean(resolutionContext, definition, qualifier, beanType, true, null);
            registerSingletonBean(definition, beanType, createdBean, qualifier, true);
            return createdBean;
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

    private <T> void registerSingletonBean(
            BeanDefinition<T> beanDefinition,
            Argument<T> beanType,
            T createdBean,
            Qualifier<T> qualifier,
            boolean singleCandidate) {
        // for only one candidate create link to bean type as singleton
        if (qualifier == null) {
            qualifier = beanDefinition.getDeclaredQualifier();
        }

        final Set<Class<?>> exposedTypes = beanDefinition.getExposedTypes();
        final boolean hasNoExposedTypes = CollectionUtils.isEmpty(exposedTypes);
        if (hasNoExposedTypes) {
            qualifier = getQualifierForBeanType(beanType, qualifier);
            BeanKey<T> key = new BeanKey<>(beanDefinition, qualifier);
            if (LOG.isDebugEnabled()) {
                if (qualifier != null) {
                    LOG.debug("Registering singleton bean {} for type [{} {}] using bean key {}", createdBean, qualifier, beanType.getName(), key);
                } else {
                    LOG.debug("Registering singleton bean {} for type [{}] using bean key {}", createdBean, beanType.getName(), key);
                }
            }
            BeanRegistration<T> registration = new BeanRegistration<>(key, beanDefinition, createdBean);
          
            if (singleCandidate || key.beanType.getTypeParameters().length > 0) {
                singletonObjects.put(key, registration);
            }
          
            final Class<T> beanClass = beanType.getType();

            boolean isNotProxyTarget = qualifier != PROXY_TARGET_QUALIFIER;
            if (isNotProxyTarget) {
                Class<?> createdType = createdBean != null ? createdBean.getClass() : beanClass;
                boolean createdTypeDiffers = !createdType.equals(beanClass);

                BeanKey<?> createdBeanKey = new BeanKey(createdType, qualifier);
                Qualifier<T> declaredQualifier = beanDefinition.getDeclaredQualifier();
                if (declaredQualifier != null) {
                    BeanKey qualifierKey = new BeanKey(createdType, declaredQualifier);
                    if (!qualifierKey.equals(createdBeanKey)) {
                        singletonObjects.put(qualifierKey, registration);
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
        } else {
            for (Class<?> exposedType : exposedTypes) {
                BeanKey<?> key = new BeanKey(exposedType, qualifier);
                if (LOG.isDebugEnabled()) {
                    if (qualifier != null) {
                        LOG.debug("Registering singleton bean {} for type [{} {}] using bean key {}", createdBean, qualifier, exposedType.getName(), key);
                    } else {
                        LOG.debug("Registering singleton bean {} for type [{}] using bean key {}", createdBean, exposedType.getName(), key);
                    }
                }
                BeanRegistration<T> registration = new BeanRegistration<>(key, beanDefinition, createdBean);
                singletonObjects.put(key, registration);
                if (qualifier != null && beanDefinition.isPrimary()) {
                    BeanKey<?> primaryKey = new BeanKey(exposedType, null);
                    singletonObjects.put(primaryKey, registration);
                }
            }
        }
    }

    private void readAllBeanDefinitionClasses() {
        List<BeanDefinitionReference> contextScopeBeans = new ArrayList<>(20);
        List<BeanDefinitionReference> processedBeans = new ArrayList<>(10);
        List<BeanDefinitionReference> parallelBeans = new ArrayList<>(10);
        List<BeanDefinitionReference> beanDefinitionReferences = resolveBeanDefinitionReferences();
        beanDefinitionsClasses.addAll(beanDefinitionReferences);

        Map<BeanConfiguration, Boolean> configurationEnabled = beanConfigurations.values().stream()
                .collect(Collectors.toMap(Function.identity(), bc -> bc.isEnabled(this)));
        final Set<Map.Entry<BeanConfiguration, Boolean>> configurationEnabledEntries = configurationEnabled.entrySet();

        reference:
        for (BeanDefinitionReference beanDefinitionReference : beanDefinitionReferences) {
            for (Map.Entry<BeanConfiguration, Boolean> entry : configurationEnabledEntries) {
                if (entry.getKey().isWithin(beanDefinitionReference) && !entry.getValue()) {
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
            beanDefinitions = findBeanCandidates(resolutionContext, beanType, null, true);
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
    protected <T> BeanRegistration<T> getBeanRegistration(@Nullable BeanResolutionContext resolutionContext, @NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        final BeanDefinition<T> beanDefinition = getBeanDefinition(beanType, qualifier);
        final T bean = getBeanInternal(resolutionContext, beanType, qualifier, true, true);
        return new BeanRegistration<>(new BeanKey<>(beanDefinition, qualifier), beanDefinition, bean);
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
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(
            @Nullable BeanResolutionContext resolutionContext,
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
        final Class<T> beanClass = beanType.getType();
        final Qualifier<T> finalQualifier = getQualifierForBeanType(beanType, qualifier);
        hasQualifier = finalQualifier != null;
        BeanKey<T> key = new BeanKey<>(beanType, finalQualifier);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Looking up existing beans for key: {}", key);
        }
        @SuppressWarnings("unchecked") Collection<BeanRegistration<T>> existing =
                (Collection<BeanRegistration<T>>) initializedObjectsByType.get(key);
        if (existing != null) {
            logResolvedExisting(beanType, finalQualifier, hasQualifier, existing);
            return existing;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("No beans found for key: {}", key);
        }

        synchronized (singletonObjects) {
            existing = (Collection<BeanRegistration<T>>) initializedObjectsByType.get(key);
            if (existing != null) {
                logResolvedExisting(beanType, finalQualifier, hasQualifier, existing);
                return existing;
            }

            HashSet<BeanRegistration<T>> beansOfTypeList;
            boolean allCandidatesAreSingleton = false;
            boolean hasOrderAnnotation = false;
            Collection<BeanRegistration<T>> beanRegistrations;
            Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(resolutionContext, beanType);
            filterProxiedTypes(candidates, true, false);
            boolean hasCandidates = !candidates.isEmpty();
            if (hasQualifier && hasCandidates) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Qualifying bean [{}] from candidates {} for qualifier: {} ", beanType.getName(), candidates, finalQualifier);
                }
                Stream<BeanDefinition<T>> candidateStream = candidates.stream();
                candidateStream = applyBeanResolutionFilters(resolutionContext, candidateStream);

                List<BeanDefinition<T>> reduced = finalQualifier.reduce(beanClass, candidateStream)
                        .collect(Collectors.toList());
                if (!reduced.isEmpty()) {
                    beansOfTypeList = new HashSet<>(reduced.size());
                    for (BeanDefinition<T> definition : reduced) {
                        if (definition.isSingleton()) {
                            allCandidatesAreSingleton = true;
                        }
                        if (definition.hasAnnotation(Order.class)) {
                            hasOrderAnnotation = true;
                        }
                        addCandidateToList(resolutionContext, beanType, definition, beansOfTypeList, finalQualifier, reduced.size() == 1);
                    }
                    beanRegistrations = beansOfTypeList;
                } else {

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Found no matching beans of type [{}] for qualifier: {} ", beanType.getName(), finalQualifier);
                    }
                    allCandidatesAreSingleton = true;
                    beanRegistrations = Collections.emptySet();
                }
            } else if (hasCandidates) {
                boolean hasNonSingletonCandidate = false;
                int candidateCount = candidates.size();
                Stream<BeanDefinition<T>> candidateStream = candidates.stream();
                candidateStream = applyBeanResolutionFilters(resolutionContext, candidateStream);

                List<BeanDefinition<T>> candidateList = candidateStream.collect(Collectors.toList());
                beansOfTypeList = new HashSet<>(candidateCount);
                for (BeanDefinition<T> candidate : candidateList) {
                    if (!hasNonSingletonCandidate && !candidate.isSingleton()) {
                        hasNonSingletonCandidate = true;
                    }
                    if (candidate.hasAnnotation(Order.class)) {
                        hasOrderAnnotation = true;
                    }
                    addCandidateToList(resolutionContext, beanType, candidate, beansOfTypeList, finalQualifier, candidateCount == 1);
                }
                if (!hasNonSingletonCandidate) {
                    allCandidatesAreSingleton = true;
                }
                beanRegistrations = beansOfTypeList;
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found no possible candidate beans of type [{}] for qualifier: {} ", beanType.getName(), finalQualifier);
                }
                allCandidatesAreSingleton = true;
                beanRegistrations = Collections.emptySet();
            }

            Collection<BeanRegistration<T>> beans = Collections.emptyList();
            if (beanRegistrations != Collections.EMPTY_SET) {
                Stream<BeanRegistration<T>> stream = beanRegistrations.stream();
                if (Ordered.class.isAssignableFrom(beanClass)) {
                    beans = stream
                            .sorted(OrderUtil.COMPARATOR)
                            .collect(StreamUtils.toImmutableCollection());
                } else {
                    if (hasOrderAnnotation) {
                        stream = stream.sorted(BEAN_REGISTRATION_COMPARATOR);
                    }
                    beans = stream
                            .collect(StreamUtils.toImmutableCollection());
                }
            }

            if (allCandidatesAreSingleton) {
                initializedObjectsByType.put(key, beans);
            }
            if (LOG.isDebugEnabled() && !beans.isEmpty()) {
                if (hasQualifier) {
                    LOG.debug("Found {} beans for type [{} {}]: {} ", beanRegistrations.size(), finalQualifier, beanType.getName(), beanRegistrations);
                } else {
                    LOG.debug("Found {} beans for type [{}]: {} ", beanRegistrations.size(), beanType.getName(), beanRegistrations);
                }
            }

            return beans;
        }
    }

    private <T> void logResolvedExisting(Argument<T> beanType, Qualifier<T> qualifier, boolean hasQualifier, Collection<BeanRegistration<T>> existing) {
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
        if (segment instanceof AbstractBeanResolutionContext.ConstructorSegment) {
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
            Argument<T> beanType,
            BeanDefinition<T> candidate,
            Collection<BeanRegistration<T>> beansOfTypeList,
            Qualifier<T> qualifier,
            boolean singleCandidate) {
        T bean = null;
        try {
            if (candidate.isSingleton()) {
                synchronized (singletonObjects) {
                    try (BeanResolutionContext context = newResolutionContext(candidate, resolutionContext)) {
                        if (candidate instanceof NoInjectionBeanDefinition) {
                            NoInjectionBeanDefinition noibd = (NoInjectionBeanDefinition) candidate;
                            final BeanKey key = new BeanKey(noibd.singletonClass, noibd.qualifier);
                            final BeanRegistration beanRegistration = singletonObjects.get(key);
                            if (beanRegistration != null) {
                                bean = (T) beanRegistration.bean;
                            } else {
                                throw new IllegalStateException("Singleton not present for key: " + key);
                            }
                        } else {
                            bean = doCreateBean(
                                    context,
                                    candidate,
                                    qualifier,
                                    beanType,
                                    true,
                                    null
                            );
                            registerSingletonBean(
                                    candidate,
                                    beanType,
                                    bean,
                                    qualifier,
                                    singleCandidate
                            );
                        }
                    }
                }
            } else {
                try (BeanResolutionContext context = newResolutionContext(candidate, resolutionContext)) {
                    bean = getScopedBeanForDefinition(context, beanType, qualifier, true, candidate);
                }
            }
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", beanType.getTypeName(), e.getMessage());
            }
        }

        if (bean != null) {
            beansOfTypeList.add(new BeanRegistration(null, candidate, bean));
        }
    }

    private <T> boolean isCandidatePresent(Argument<T> beanType, Qualifier<T> qualifier) {
        qualifier = getQualifierForBeanType(beanType, qualifier);
        final Collection<BeanDefinition<T>> candidates = findBeanCandidates(null, beanType, null, true);
        if (!candidates.isEmpty()) {
            filterReplacedBeans(null, candidates);
            Stream<BeanDefinition<T>> stream = candidates.stream();
            if (qualifier != null) {
                stream = qualifier.reduce(beanType.getType(), stream);
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
                for (Class<?> clazz : components) {
                    if (unsorted.stream()
                            .map(BeanRegistration::getBeanDefinition)
                            .map(BeanDefinition::getBeanType)
                            .anyMatch(clazz::isAssignableFrom)) {
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
    static final class BeanKey<T> implements BeanIdentifier {
        private final Argument<T> beanType;
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
     * @param <T> The bean type
     */
    private static final class NoInjectionBeanDefinition<T> implements BeanDefinition<T>, BeanDefinitionReference<T> {
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

        @NonNull
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
            throw new UnsupportedOperationException("Bean of type [" + getBeanType() + "] is a manually registered singleton that was registered with the context via BeanContext.registerBean(..) and cannot be created directly");
        }

        @Override
        public Collection<Class<?>> getRequiredComponents() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MethodInjectionPoint<T, ?>> getInjectedMethods() {
            return Collections.emptyList();
        }

        @Override
        public Collection<FieldInjectionPoint<T, ?>> getInjectedFields() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MethodInjectionPoint<T, ?>> getPostConstructMethods() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MethodInjectionPoint<T, ?>> getPreDestroyMethods() {
            return Collections.emptyList();
        }

        @Override
        @NonNull
        public String getName() {
            return singletonClass.getName();
        }

        @Override
        public boolean isEnabled(BeanContext beanContext) {
            return true;
        }

        @Override
        public boolean isEnabled(@NonNull BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            return true;
        }

        @Override
        public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>[] argumentTypes) {
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
