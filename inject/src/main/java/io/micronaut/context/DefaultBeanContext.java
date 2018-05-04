/*
 * Copyright 2017-2018 original authors
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.context.scope.CustomScopeRegistry;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.StreamSoftServiceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.StreamUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionMethodReference;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.ParametrizedBeanFactory;
import io.micronaut.inject.ParametrizedProvider;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The default context implementations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultBeanContext implements BeanContext {

    private static final Qualifier PROXY_TARGET_QUALIFIER = Qualifiers.byType(ProxyTarget.class);
    protected static final Logger LOG = LoggerFactory.getLogger(DefaultBeanContext.class);

    protected final AtomicBoolean running = new AtomicBoolean(false);

    private final Collection<BeanDefinitionReference> beanDefinitionsClasses = new ConcurrentLinkedQueue<>();
    private final Map<String, BeanConfiguration> beanConfigurations = new ConcurrentHashMap<>(4);
    private final Map<BeanKey, Boolean> containsBeanCache = new ConcurrentHashMap<>(30);

    private final Cache<BeanKey, Collection<Object>> initializedObjectsByType = Caffeine.newBuilder()
                                                                                        .maximumSize(30)
                                                                                        .build();
    private final Cache<BeanKey, Optional<BeanDefinition>> beanConcreteCandidateCache = Caffeine.newBuilder()
                                                                                                .maximumSize(30)
                                                                                                .build();
    private final Cache<Class, Collection<BeanDefinition>> beanCandidateCache = Caffeine.newBuilder()
                                                                                        .maximumSize(30)
                                                                                        .build();

    final Map<BeanKey, BeanRegistration> singletonObjects = new ConcurrentHashMap<>(30);

    private final ClassLoader classLoader;
    private final Set<Class> thisInterfaces = ReflectionUtils.getAllInterfaces(getClass());
    private final CustomScopeRegistry customScopeRegistry = new DefaultCustomScopeRegistry(this);
    private final ResourceLoader resourceLoader;

    /**
     * Construct a new bean context using the same classloader that loaded this DefaultBeanContext class
     */
    public DefaultBeanContext() {
        this(BeanContext.class.getClassLoader());
    }

    /**
     * Construct a new bean context with the given class loader
     *
     * @param classLoader The class loader
     */
    public DefaultBeanContext(ClassLoader classLoader) {
        this(ClassPathResourceLoader.defaultLoader(classLoader));
    }

    /**
     * Construct a new bean context with the given class loader
     *
     * @param resourceLoader The resource loader
     */
    public DefaultBeanContext(ClassPathResourceLoader resourceLoader) {
        this.classLoader = resourceLoader.getClassLoader();
        this.resourceLoader = resourceLoader;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * The start method will read all bean definition classes found on the classpath and initialize any pre-required state
     */
    @Override
    public synchronized BeanContext start() {
        if (running.compareAndSet(false, true)) {
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
        return this;
    }

    /**
     * The close method will shut down the context calling {@link javax.annotation.PreDestroy} hooks on loaded singletons.
     */
    @Override
    public BeanContext stop() {
        if (running.compareAndSet(true, false)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping BeanContext");
            }
            publishEvent(new ShutdownEvent(this));
            // need to sort registered singletons so that beans with that require other beans appear first
            ArrayList<BeanRegistration> objects = new ArrayList<>(singletonObjects.values());
            objects.sort((o1, o2) -> {
                    BeanDefinition bd1 = o1.beanDefinition;
                    BeanDefinition bd2 = o2.beanDefinition;

                    Collection requiredComponents1 = bd1.getRequiredComponents();
                    Collection requiredComponents2 = bd2.getRequiredComponents();
                    Integer requiredComponentCount1 = requiredComponents1.size();
                    Integer requiredComponentCount2 = requiredComponents2.size();
                    return requiredComponentCount1.compareTo(requiredComponentCount2);
                }
            );

            objects.forEach(beanRegistration -> {
                BeanDefinition def = beanRegistration.beanDefinition;
                Object bean = beanRegistration.bean;
                if (def instanceof DisposableBeanDefinition) {
                    try {
                        //noinspection unchecked
                        ((DisposableBeanDefinition) def).dispose(this, bean);
                    } catch (Throwable e) {
                        if(LOG.isErrorEnabled()) {
                            LOG.error("Error disposing of bean registration [" + def.getName() + "]: " + e.getMessage(), e);
                        }
                    }
                }
                if(def instanceof Closeable) {
                    try {
                        ((Closeable)def).close();
                    } catch (Throwable e) {
                        if(LOG.isErrorEnabled()) {
                            LOG.error("Error disposing of bean registration [" + def.getName() + "]: " + e.getMessage(), e);
                        }
                    }
                }
                if (bean instanceof LifeCycle) {
                    ((LifeCycle) bean).stop();
                }
            });
        }
        return this;
    }

    @Override
    @Nonnull
    public AnnotatedElement resolveElement(Class<?> type) {
        Optional<? extends BeanDefinition<?>> candidate = findConcreteCandidate(type, null, false, false);
        if(candidate.isPresent()) {
            return candidate.get();
        }
        return type;
    }

    @Override
    @Nonnull
    public AnnotationMetadata resolveMetadata(Class<?> type) {
        Optional<? extends BeanDefinition<?>> candidate = findConcreteCandidate(type, null, false, false);
        if(candidate.isPresent()) {
            return candidate.get();
        }
        return AnnotationMetadata.EMPTY_METADATA;
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
    public Collection<BeanRegistration<?>> getBeanRegistrations(Qualifier<?> qualifier) {
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
    public <T> Collection<BeanRegistration<T>> getBeanRegistrations(Class<T> beanType) {
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

    @SuppressWarnings("unchecked")
    @Override
    public <R> Optional<MethodExecutionHandle<R>> findExecutionHandle(Class<?> beanType, String method, Class... arguments) {
        return findExecutionHandle(beanType, null, method, arguments);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> Optional<MethodExecutionHandle<R>> findExecutionHandle(Class<?> beanType, Qualifier<?> qualifier, String method, Class... arguments) {
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
                    .map((ExecutableMethod executableMethod) -> new BeanExecutionHandle(this, beanType, qualifier, executableMethod));
            }
        }
        return Optional.empty();
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findExecutableMethod(Class<T> beanType, String method, Class[] arguments) {
        if (beanType != null) {

            Optional<BeanDefinition<T>> foundBean = findBeanDefinition(beanType);
            if (foundBean.isPresent()) {
                BeanDefinition<T> beanDefinition = foundBean.get();
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
    public <R> Optional<MethodExecutionHandle<R>> findExecutionHandle(Object bean, String method, Class[] arguments) {
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
    public <T> BeanContext registerSingleton(Class<T> type, T singleton, Qualifier<T> qualifier, boolean inject) {
        if (singleton == null) {
            throw new IllegalArgumentException("Passed singleton cannot be null");
        }
        BeanKey<T> beanKey = new BeanKey<>(type, qualifier);
        synchronized (singletonObjects) {
            initializedObjectsByType.invalidateAll();

            BeanDefinition<T> beanDefinition = inject ? findBeanCandidatesForInstance(singleton).stream().findFirst().orElse(null) : null;
            if (beanDefinition != null && beanDefinition.getBeanType().isInstance(singleton)) {
                doInject(new DefaultBeanResolutionContext(this, beanDefinition), singleton, beanDefinition);
                singletonObjects.put(beanKey, new BeanRegistration<>(beanKey, beanDefinition, singleton));
            } else {
                NoInjectionBeanDefinition<T> dynamicRegistration = new NoInjectionBeanDefinition<>(type);
                beanDefinitionsClasses.add(dynamicRegistration);
                singletonObjects.put(beanKey, new BeanRegistration<>(beanKey, dynamicRegistration, singleton));
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
    public <T> boolean containsBean(Class<T> beanType, Qualifier<T> qualifier) {
        BeanKey<T> beanKey = new BeanKey<>(beanType, qualifier);
        if (containsBeanCache.containsKey(beanKey)) {
            return containsBeanCache.get(beanKey);
        } else {
            boolean result = singletonObjects.containsKey(beanKey) ||
                findConcreteCandidateNoCache(beanType, qualifier, false, false, false).isPresent();

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

    <T> Stream<T> streamOfType(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        return getBeansOfTypeInternal(resolutionContext, beanType, qualifier).stream();
    }

    @Override
    public <T> T inject(T instance) {
        Objects.requireNonNull(instance, "Instance cannot be null");

        Collection<BeanDefinition> candidates = findBeanCandidatesForInstance(instance);
        if (candidates.size() == 1) {
            BeanDefinition<T> beanDefinition = candidates.stream().findFirst().get();
            beanDefinition.inject(new DefaultBeanResolutionContext(this, beanDefinition), this, instance);

        } else if (!candidates.isEmpty()) {
            throw new BeanContextException("Multiple possible bean candidates found for injection: " + candidates);
        }
        return instance;
    }

    @Override
    public <T> T createBean(Class<T> beanType, Qualifier<T> qualifier) {
        return createBean(null, beanType, qualifier);
    }

    @Override
    public <T> T createBean(Class<T> beanType, Qualifier<T> qualifier, Map<String, Object> argumentValues) {
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
    public <T> T createBean(Class<T> beanType, Qualifier<T> qualifier, Object... args) {
        Optional<BeanDefinition<T>> candidate = findConcreteCandidate(beanType, qualifier, true, false);
        if (candidate.isPresent()) {
            BeanDefinition<T> definition = candidate.get();
            DefaultBeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(this, definition);
            return doCreateBean(resolutionContext, definition, beanType, qualifier, args);
        }
        throw new NoSuchBeanException(beanType);
    }

    protected <T> T doCreateBean(BeanResolutionContext resolutionContext, BeanDefinition<T> definition, Class<T> beanType, Qualifier<T> qualifier, Object... args) {
        Map<String, Object> argumentValues;
        if (definition instanceof ParametrizedBeanFactory) {
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
                        argumentValues.put(requiredArgument.getName(), ConversionService.SHARED.convert(val, requiredArgument).orElseThrow(() ->
                            new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. Cannot convert object [" + val + "] to required type: " + requiredArgument.getType())
                        ));
                    } else {
                        // attempt resolve from context
                        Optional<?> existingBean = findBean(resolutionContext, requiredArgument.getType(), null);
                        argumentValues.put(requiredArgument.getName(), existingBean.orElseThrow(() ->
                            new BeanInstantiationException(resolutionContext, "Invalid bean @Argument [" + requiredArgument + "]. No bean found for type: " + requiredArgument.getType())
                        ));
                    }
                } finally {
                    path.pop();
                }
            }
        } else {
            argumentValues = Collections.emptyMap();
        }
        T createdBean = doCreateBean(resolutionContext, definition, qualifier, false, argumentValues);
        if (createdBean == null) {
            throw new NoSuchBeanException(beanType);
        }
        return createdBean;
    }

    @Override
    public <T> T destroyBean(Class<T> beanType) {
        T bean = null;
        BeanKey<T> beanKey = new BeanKey<>(beanType, null);

        synchronized (singletonObjects) {
            if (singletonObjects.containsKey(beanKey)) {
                @SuppressWarnings("unchecked") BeanRegistration<T> beanRegistration = singletonObjects.get(beanKey);
                bean = beanRegistration.bean;
                if (bean != null) {
                    singletonObjects.remove(beanKey);
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

    <T> T createBean(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
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

    <T> T inject(BeanResolutionContext resolutionContext, BeanDefinition requestingBeanDefinition, T instance) {
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


    <T> Collection<T> getBeansOfType(BeanResolutionContext resolutionContext, Class<T> beanType) {
        return getBeansOfTypeInternal(resolutionContext, beanType, null);
    }

    <T> Collection<T> getBeansOfType(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        return getBeansOfTypeInternal(resolutionContext, beanType, qualifier);
    }

    <T> Provider<T> getBeanProvider(BeanResolutionContext resolutionContext, Class<T> beanType) {
        return getBeanProvider(resolutionContext, beanType, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProxyTargetBean(Class<T> beanType, Qualifier<T> qualifier) {
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        BeanDefinition<T> definition = getProxiedBeanDefinition(beanType, qualifier);
        return getBeanForDefinition(new DefaultBeanResolutionContext(this, definition), beanType, proxyQualifier, true, definition);
    }

    @Override
    public <T, R> Optional<ExecutableMethod<T, R>> findProxyTargetMethod(Class<T> beanType, String method, Class[] arguments) {
        BeanDefinition<T> definition = getProxiedBeanDefinition(beanType, null);
        return definition.findMethod(method, arguments);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<BeanDefinition<T>> findProxiedBeanDefinition(Class<T> beanType, Qualifier<T> qualifier) {
        Qualifier<T> proxyQualifier = qualifier != null ? Qualifiers.byQualifiers(qualifier, PROXY_TARGET_QUALIFIER) : PROXY_TARGET_QUALIFIER;
        BeanKey key = new BeanKey(beanType, proxyQualifier);

        return (Optional) beanConcreteCandidateCache.get(key, beanKey -> {
            BeanRegistration<T> beanRegistration = singletonObjects.get(beanKey);
            if (beanRegistration != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Resolved existing bean [{}] for type [{}] and qualifier [{}]", beanRegistration.bean, beanType, qualifier);
                }
                return Optional.of(beanRegistration.beanDefinition);
            }
            return findConcreteCandidateNoCache((Class) beanType, qualifier, true, false, false);
        });

    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<BeanDefinition<?>> getBeanDefinitions(Qualifier<Object> qualifier) {
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
                    .parallel()
                    .map(ref -> ref.load(this))
                    .filter(candidate -> candidate.isEnabled(this))
            );
            candidates = candidateStream.collect(Collectors.toList());

        } else {
            return (Collection<BeanDefinition<?>>) Collections.EMPTY_MAP;
        }
        filterProxiedTypes(candidates, true, true);
        return candidates;
    }

    @SuppressWarnings("unchecked")
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
            return (Collection<BeanDefinition<?>>) collection;
        }

        return (Collection<BeanDefinition<?>>) Collections.EMPTY_MAP;
    }


    public <T> T getBean(BeanResolutionContext resolutionContext, Class<T> beanType) {
        return getBeanInternal(resolutionContext, beanType, null, true, true);
    }

    public <T> T getBean(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        return getBeanInternal(resolutionContext, beanType, qualifier, true, true);
    }

    public <T> Optional<T> findBean(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
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
    public void publishEvent(Object event) {
        if (event != null) {
            streamOfType(ApplicationEventListener.class, Qualifiers.byTypeArguments(event.getClass()))
                .forEach(listener -> {
                        if (listener.supports(event)) {
                            try {
                                listener.onApplicationEvent(event);
                            } catch (ClassCastException ex) {
                                String msg = ex.getMessage();
                                if (msg == null || msg.startsWith(event.getClass().getName())) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Incompatible listener for event: " + listener, ex);
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

    /**
     * Invalidates the bean caches
     */
    void invalidateCaches() {
        beanCandidateCache.invalidateAll();
        initializedObjectsByType.invalidateAll();
    }

    <T> Provider<T> getBeanProvider(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        @SuppressWarnings("unchecked") BeanRegistration<T> beanRegistration = singletonObjects.get(new BeanKey(beanType, qualifier));
        if (beanRegistration != null) {
            return new ResolvedProvider<>(beanRegistration.bean);
        }

        Optional<BeanDefinition<T>> concreteCandidate = findConcreteCandidate(beanType, qualifier, true, false);
        if (concreteCandidate.isPresent()) {
            BeanDefinition<T> definition = concreteCandidate.get();
            return new UnresolvedProvider<>(definition.getBeanType(), this);
        } else {
            throw new NoSuchBeanException(beanType);
        }
    }

    /**
     * Resolves the {@link BeanDefinitionReference} class instances. Default implementation uses ServiceLoader pattern
     *
     * @return The bean definition classes
     */
    protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        return StreamSoftServiceLoader.loadPresentParallel(BeanDefinitionReference.class, classLoader).collect(Collectors.toList());
    }

    /**
     * Resolves the {@link BeanConfiguration} class instances. Default implementation uses ServiceLoader pattern
     *
     * @return The bean definition classes
     */
    protected Iterable<BeanConfiguration> resolveBeanConfigurations() {
        return ServiceLoader.load(BeanConfiguration.class, classLoader);
    }

    /**
     * Initialize the context with the given {@link Context} scope beans
     *
     * @param contextScopeBeans The context scope beans
     * @param processedBeans    The beans that require {@link ExecutableMethodProcessor} handling
     */
    protected void initializeContext(List<BeanDefinitionReference> contextScopeBeans, List<BeanDefinitionReference> processedBeans) {
        for (BeanDefinitionReference contextScopeBean : contextScopeBeans) {
            try {

                BeanDefinition beanDefinition = contextScopeBean.load(this);
                if (beanDefinition.isEnabled(this)) {

                    if(beanDefinition.isIterable()) {
                        Collection<BeanDefinition> beanCandidates = findBeanCandidates(beanDefinition.getBeanType(), null);
                        for (BeanDefinition beanCandidate : beanCandidates) {
                            createAndRegisterSingleton(new DefaultBeanResolutionContext(this, beanDefinition), beanCandidate, beanCandidate.getBeanType(), null);
                        }

                    }
                    else {

                        createAndRegisterSingleton(new DefaultBeanResolutionContext(this, beanDefinition), beanDefinition, beanDefinition.getBeanType(), null);
                    }
                }
            } catch (Throwable e) {
                throw new BeanInstantiationException("Bean definition [" + contextScopeBean.getName() + "] could not be loaded: " + e.getMessage(), e);
            }
        }

        if (!processedBeans.isEmpty()) {

            @SuppressWarnings("unchecked") Stream<BeanDefinitionMethodReference<?, ?>> methodStream = processedBeans
                .parallelStream()
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
            Map<Class<? extends Annotation>, List<BeanDefinitionMethodReference<?, ?>>> byAnnotation = methodStream
                .collect(
                    Collectors.groupingBy((Function<ExecutableMethod<?, ?>, Class<? extends Annotation>>) executableMethod ->
                        executableMethod.getAnnotationTypeByStereotype(Executable.class)
                            .orElseThrow(() ->
                                new IllegalStateException("BeanDefinition.requiresMethodProcessing() returned true but method has no @Executable definition. This should never happen. Please report an issue.")
                            )));

            // Find ExecutableMethodProcessor for each annotation and process the BeanDefinitionMethodReference
            for (Map.Entry<Class<? extends Annotation>, List<BeanDefinitionMethodReference<?, ?>>> entry : byAnnotation.entrySet()) {
                Class<? extends Annotation> annotationType = entry.getKey();
                streamOfType(ExecutableMethodProcessor.class, Qualifiers.byTypeArguments(annotationType))
                    .forEach(processor -> {
                        for (BeanDefinitionMethodReference<?, ?> method : entry.getValue()) {
                            //noinspection unchecked
                            processor.process(method.getBeanDefinition(), method);
                        }
                    });
            }
        }
    }

    /**
     * Find bean candidates for the given type
     *
     * @param beanType The bean type
     * @param filter   A bean definition to filter out
     * @param <T>      The bean generic type
     * @return The candidates
     */
    @SuppressWarnings("unchecked")
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(Class<T> beanType, BeanDefinition<?> filter) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for type: {}", beanType);
        }
        // first traverse component definition classes and load candidates

        Collection<BeanDefinitionReference> beanDefinitionsClasses = this.beanDefinitionsClasses;
        if (!beanDefinitionsClasses.isEmpty()) {

            Stream<BeanDefinition<T>> candidateStream = beanDefinitionsClasses
                .parallelStream()
                .filter(reference -> {
                    Class<?> candidateType = reference.getBeanType();
                    return candidateType != null && (beanType.isAssignableFrom(candidateType) || beanType == candidateType);
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
            List<BeanDefinition<T>> candidates = candidateStream
                .filter(candidate -> candidate.isEnabled(this))
                .collect(Collectors.toList());

            List<Class> replacedTypes = new ArrayList<>(2);
            for (BeanDefinition<T> candidate : candidates) {
                Optional<Class> replacesType = candidate.getAnnotationMetadata().getValue(Replaces.class, Class.class);

                replacesType.ifPresent(aClass -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Bean [{}] replaces existing bean of type [{}]", candidate.getBeanType(), aClass);
                    }
                    replacedTypes.add(aClass);
                });
            }
            if(!replacedTypes.isEmpty()) {
                candidates.removeIf(definition -> replacedTypes.contains(definition.getBeanType()));
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
     * Find bean candidates for the given type
     *
     * @param instance The bean instance
     * @param <T>      The bean generic type
     * @return The candidates
     */
    protected <T> Collection<BeanDefinition> findBeanCandidatesForInstance(T instance) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finding candidate beans for instance: {}", instance);
        }
        Collection<BeanDefinitionReference> beanDefinitionsClasses = this.beanDefinitionsClasses;
        return beanCandidateCache.get(instance.getClass(), aClass -> {
            // first traverse component definition classes and load candidates

            if (!beanDefinitionsClasses.isEmpty()) {

                List<BeanDefinition> candidates = beanDefinitionsClasses
                    .parallelStream()
                    .filter(reference -> {
                        Class<?> candidateType = reference.getBeanType();

                        return candidateType != null && candidateType.isInstance(instance);
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
     * Registers an active configuration
     *
     * @param configuration The configuration to register
     */
    protected void registerConfiguration(BeanConfiguration configuration) {
        beanConfigurations.put(configuration.getName(), configuration);
    }

    /**
     * Execution the creation of a bean
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param beanDefinition    The {@link BeanDefinition}
     * @param qualifier         The {@link Qualifier}
     * @param isSingleton       Whether the bean is a singleton
     * @param argumentValues    Any argument values passed to create the bean
     * @param <T>               The bean generic type
     * @return The created bean
     */
    protected <T> T doCreateBean(BeanResolutionContext resolutionContext,
                                 BeanDefinition<T> beanDefinition,
                                 Qualifier<T> qualifier,
                                 boolean isSingleton,
                                 Map<String, Object> argumentValues) {
        BeanRegistration<T> beanRegistration = isSingleton ? singletonObjects.get(new BeanKey(beanDefinition.getBeanType(), qualifier)) : null;
        T bean;
        if (beanRegistration != null) {
            return beanRegistration.bean;
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
                        throw new BeanInstantiationException(resolutionContext, "Missing bean arguments for type: " + beanDefinition.getBeanType().getName());
                    }
                    Map<String, Object> convertedValues = new LinkedHashMap<>(argumentValues);
                    for (Argument<?> requiredArgument : requiredArguments) {
                        Object val = argumentValues.get(requiredArgument.getName());
                        if (val == null) {
                            throw new BeanInstantiationException(resolutionContext, "Missing bean argument [" + requiredArgument + "].");
                        }
                        BeanResolutionContext finalResolutionContext = resolutionContext;
                        convertedValues.put(requiredArgument.getName(), ConversionService.SHARED.convert(val, requiredArgument).orElseThrow(() ->
                            new BeanInstantiationException(finalResolutionContext, "Invalid bean argument [" + requiredArgument + "]. Cannot convert object [" + val + "] to required type: " + requiredArgument.getType())
                        ));
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
                        throw new BeanInstantiationException(resolutionContext, "Bean Factory [" + beanFactory + "] returned null");
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

        if (!BeanCreatedEventListener.class.isInstance(bean)) {

            Collection<BeanCreatedEventListener> beanCreatedEventListeners = getBeansOfType(resolutionContext, BeanCreatedEventListener.class, Qualifiers.byTypeArguments(beanDefinition.getBeanType()));
            for (BeanCreatedEventListener listener : beanCreatedEventListeners) {
                bean = (T) listener.onCreated(new BeanCreatedEvent(this, beanDefinition, bean));
                if (bean == null) {
                    throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onCreated event");
                }
            }
        }
        if (beanDefinition instanceof ValidatedBeanDefinition) {
            bean = ((ValidatedBeanDefinition<T>) beanDefinition).validate(resolutionContext, bean);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created bean [{}] from definition [{}] with qualifier [{}]", bean, beanDefinition, qualifier);
        }
        return bean;
    }

    /**
     * Fall back method to attempt to find a candidate for the given definitions
     *
     * @param beanType   The bean type
     * @param qualifier  The qualifier
     * @param candidates The candidates
     * @param <T>        The generic time
     * @return The concrete bean definition
     */
    protected <T> BeanDefinition<T> findConcreteCandidate(Class<T> beanType, Qualifier<T> qualifier, Collection<BeanDefinition<T>> candidates) {
        throw new NonUniqueBeanException(beanType, candidates.iterator());
    }

    private <T> void doInject(BeanResolutionContext resolutionContext, T instance, BeanDefinition definition) {
        definition.inject(resolutionContext, this, instance);
        if (definition instanceof InitializingBeanDefinition) {
            ((InitializingBeanDefinition) definition).initialize(resolutionContext, this, instance);
        }
    }

    private <T> T getBeanInternal(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier, boolean throwNonUnique, boolean throwNoSuchBean) {
        // allow injection the bean context
        if (thisInterfaces.contains(beanType)) {
            return (T) this;
        }

        BeanRegistration<T> beanRegistration = singletonObjects.get(new BeanKey(beanType, qualifier));
        if (beanRegistration != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolved existing bean [{}] for type [{}] and qualifier [{}]", beanRegistration.bean, beanType, qualifier);
            }
            return beanRegistration.bean;
        }
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
                return getBeanForDefinition(resolutionContext, beanType, qualifier, throwNoSuchBean, definition);
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

    private <T> T getBeanForDefinition(
        BeanResolutionContext resolutionContext,
        Class<T> beanType, Qualifier<T> qualifier,
        boolean throwNoSuchBean,
        BeanDefinition<T> definition) {
        if (definition.isSingleton()) {
            return createAndRegisterSingleton(resolutionContext, definition, beanType, qualifier);
        } else {
            return getScopedBeanForDefinition(resolutionContext, beanType, qualifier, throwNoSuchBean, definition);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getScopedBeanForDefinition(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier, boolean throwNoSuchBean, BeanDefinition<T> definition) {
        boolean isProxy = definition instanceof ProxyBeanDefinition;
        Optional<BeanResolutionContext.Segment> currentSegment = resolutionContext.getPath().currentSegment();
        Optional<Class<? extends Annotation>> scope = Optional.empty();
        if (currentSegment.isPresent()) {
            scope = AnnotationUtil.findAnnotationWithStereoType(Scope.class, currentSegment.get().getArgument().getAnnotations()).map(Annotation::annotationType);
        }
        if (!scope.isPresent()) {
            scope = isProxy ? Optional.empty() : definition.getScope();
        }

        Optional<CustomScope> registeredScope = scope.flatMap(customScopeRegistry::findScope);
        if (registeredScope.isPresent()) {
            CustomScope customScope = registeredScope.get();
            if (isProxy) {
                definition = getProxiedBeanDefinition(beanType, qualifier);
            }
            BeanDefinition<T> finalDefinition = definition;
            return (T) customScope.get(
                resolutionContext,
                finalDefinition,
                new BeanKey(beanType, qualifier),
                new ParametrizedProvider() {
                    @Override
                    public Object get(Map argumentValues) {
                        Object createBean = doCreateBean(resolutionContext, finalDefinition, qualifier, false, argumentValues);
                        if (createBean == null && throwNoSuchBean) {
                            throw new NoSuchBeanException(finalDefinition.getBeanType(), qualifier);
                        }
                        return createBean;

                    }

                    @Override
                    public Object get(Object... argumentValues) {
                        T createdBean = doCreateBean(resolutionContext, finalDefinition, beanType, qualifier, argumentValues);
                        if (createdBean == null && throwNoSuchBean) {
                            throw new NoSuchBeanException(finalDefinition.getBeanType(), qualifier);
                        }
                        return createdBean;
                    }
                }
            );
        } else {
            T createBean = doCreateBean(resolutionContext, definition, qualifier, false, null);
            if (createBean == null && throwNoSuchBean) {
                throw new NoSuchBeanException(definition.getBeanType(), qualifier);
            }
            return createBean;
        }
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

    /*
     * Find a concrete candidate for the given qualifier
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param throwNonUnique Whether to throw an exception if the bean is not found
     * @param includeProvided Whether to include provided resolution
     * @param <T> The bean generic type
     * @return The concrete bean definition candidate
     */
    @SuppressWarnings("unchecked")
    private <T> Optional<BeanDefinition<T>> findConcreteCandidate(
        Class<T> beanType,
        Qualifier<T> qualifier,
        boolean throwNonUnique,
        boolean includeProvided) {
        return (Optional) beanConcreteCandidateCache.get(new BeanKey(beanType, qualifier), beanKey ->
            (Optional) findConcreteCandidateNoCache(beanType, qualifier, throwNonUnique, includeProvided, true)
        );
    }

    private <T> Optional<BeanDefinition<T>> findConcreteCandidateNoCache(Class<T> beanType, Qualifier<T> qualifier, boolean throwNonUnique, boolean includeProvided, boolean filterProxied) {
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

                Optional<BeanDefinition<T>> primary = beanDefinitionList.stream()
                    .findFirst();
                definition = primary.orElseGet(() -> lastChanceResolve(beanType, qualifier, throwNonUnique, beanDefinitionList));
            } else {
                candidates.removeIf(BeanDefinition::isAbstract);
                if (candidates.size() == 1) {
                    definition = candidates.iterator().next();
                } else {

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Searching for @Primary for type [{}] from candidates: {} ", beanType.getName(), candidates);
                    }
                    Optional<BeanDefinition<T>> primary = candidates.stream()
                        .filter(BeanDefinition::isPrimary)
                        .findFirst();
                    if (primary.isPresent()) {
                        definition = primary.get();
                    } else {
                        definition = lastChanceResolve(beanType, null, throwNonUnique, candidates);
                    }
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
        Set<Class> proxiedTypes = new HashSet<>();
        Iterator<BeanDefinition<T>> i = candidates.iterator();
        Collection<BeanDefinition<T>> delegates = filterDelegates ? new ArrayList<>() : Collections.emptyList();
        while (i.hasNext()) {
            BeanDefinition<T> candidate = i.next();
            if (candidate instanceof ProxyBeanDefinition) {
                if (filterProxied) {
                    proxiedTypes.add(((ProxyBeanDefinition) candidate).getTargetDefinitionType());
                } else {
                    proxiedTypes.add(candidate.getClass());
                }
            } else if (filterDelegates && candidate instanceof BeanDefinitionDelegate) {
                i.remove();

                BeanDefinition<T> delegate = ((BeanDefinitionDelegate<T>) candidate).getDelegate();
                if (!delegates.contains(delegate)) {
                    delegates.add(delegate);
                }
            }
        }
        if (filterDelegates) {
            candidates.addAll(delegates);
        }
        if (!proxiedTypes.isEmpty()) {
            candidates.removeIf(candidate -> proxiedTypes.contains(candidate.getClass()));
        }
    }

    private <T> BeanDefinition<T> lastChanceResolve(Class<T> beanType, Qualifier<T> qualifier, boolean throwNonUnique, Collection<BeanDefinition<T>> candidates) {
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
            T createdBean = doCreateBean(resolutionContext, definition, qualifier, true, null);
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

    private <T> void registerSingletonBean(BeanDefinition<T> beanDefinition, Class<T> beanType, T createdBean, Qualifier<T> qualifier, boolean singleCandidate) {
        // for only one candidate create link to bean type as singleton
        if (qualifier == null) {
            if (beanDefinition instanceof BeanDefinitionDelegate) {
                String name = ((BeanDefinitionDelegate<?>) beanDefinition).get(Named.class.getName(), String.class, null);
                if (name != null) {
                    qualifier = Qualifiers.byName(name);
                }
            }
            if (qualifier == null) {
                Optional<String> optional = beanDefinition.getValue(javax.inject.Named.class, String.class);
                qualifier = (Qualifier<T>) optional.map(name -> Qualifiers.byAnnotation(beanDefinition, name)).orElse(null);
            }
        }
        if (LOG.isDebugEnabled()) {
            if (qualifier != null) {
                LOG.debug("Registering singleton bean for type [{} {}]: {} ", qualifier, beanType.getName(), createdBean);
            } else {
                LOG.debug("Registering singleton bean for type [{}]: {} ", beanType.getName(), createdBean);
            }
        }
        BeanKey key = new BeanKey<>(beanType, qualifier);
        BeanRegistration<T> registration = new BeanRegistration<>(key, beanDefinition, createdBean);

        if (singleCandidate) {
            singletonObjects.put(key, registration);
        }

        Class<?> createdType = createdBean.getClass();
        BeanKey createdBeanKey = new BeanKey(createdType, qualifier);
        Optional<Class<? extends Annotation>> qualifierAnn = beanDefinition.getAnnotationTypeByStereotype(javax.inject.Qualifier.class);
        if (qualifierAnn.isPresent()) {
            Class annotation = qualifierAnn.get();
            if (Primary.class == annotation) {
                BeanKey primaryBeanKey = new BeanKey<>(beanType, null);
                singletonObjects.put(primaryBeanKey, registration);
            } else {

                BeanKey qualifierKey = new BeanKey(createdType, Qualifiers.byAnnotation(beanDefinition, annotation.getName()));
                if (!qualifierKey.equals(createdBeanKey)) {
                    singletonObjects.put(qualifierKey, registration);
                }
            }
        } else {
            if (!beanDefinition.isIterable()) {
                BeanKey primaryBeanKey = new BeanKey<>(createdType, null);
                singletonObjects.put(primaryBeanKey, registration);
            }
        }
        singletonObjects.put(createdBeanKey, registration);
    }

    private void readAllBeanDefinitionClasses() {
        List<BeanDefinitionReference> contextScopeBeans = new ArrayList<>();
        List<BeanDefinitionReference> processedBeans = new ArrayList<>();
        Map<String, BeanDefinitionReference> beanDefinitionsClassesByType = new HashMap<>();
        Map<String, BeanDefinitionReference> beanDefinitionsClassesByDefinition = new HashMap<>();
        List<BeanDefinitionReference> beanDefinitionReferences = resolveBeanDefinitionReferences();

        for (BeanDefinitionReference beanDefinitionReference : beanDefinitionReferences) {
            if (!beanDefinitionReference.isEnabled(this)) {
                continue;
            } else {
                Optional<BeanConfiguration> beanConfiguration = beanConfigurations.values().stream().filter(c -> c.isWithin(beanDefinitionReference)).findFirst();
                if (beanConfiguration.isPresent() && !beanConfiguration.get().isEnabled(this)) {
                    if(AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                        AbstractBeanContextConditional.LOG.debug(
                                "Bean [{}] will not be loaded because the configuration [{}] is not enabled",
                                beanDefinitionReference.getName(),
                                beanConfiguration);
                    }
                    continue;
                }
            }
            beanDefinitionsClassesByType.put(beanDefinitionReference.getName(), beanDefinitionReference);
            beanDefinitionsClassesByDefinition.put(beanDefinitionReference.toString(), beanDefinitionReference);
            if (beanDefinitionReference.isContextScope()) {
                contextScopeBeans.add(beanDefinitionReference);
            }
            if (beanDefinitionReference.requiresMethodProcessing()) {
                processedBeans.add(beanDefinitionReference);
            }
        }

        this.beanDefinitionsClasses.addAll(beanDefinitionsClassesByDefinition.values());

        initializeContext(contextScopeBeans, processedBeans);
    }


    @SuppressWarnings("unchecked")
    private <T> Collection<BeanDefinition<T>> findBeanCandidatesInternal(Class<T> beanType) {
        return (Collection) beanCandidateCache.get(beanType, aClass -> (Collection) findBeanCandidates(beanType, null));
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> getBeansOfTypeInternal(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        boolean hasQualifier = qualifier != null;
        if (LOG.isDebugEnabled()) {
            if (hasQualifier) {
                LOG.debug("Resolving beans for type: {} {} ", qualifier, beanType.getName());
            } else {
                LOG.debug("Resolving beans for type: {}", beanType.getName());
            }
        }
        BeanKey<T> key = new BeanKey<>(beanType, qualifier);
        @SuppressWarnings("unchecked") Collection<T> existing = (Collection<T>) initializedObjectsByType.getIfPresent(key);
        if (existing != null) {
            if (LOG.isTraceEnabled()) {
                if (hasQualifier) {
                    LOG.trace("Found {} existing beans for type [{} {}]: {} ", existing.size(), qualifier, beanType.getName(), existing);
                } else {
                    LOG.trace("Found {} existing beans for type [{}]: {} ", existing.size(), beanType.getName(), existing);
                }
            }
            return existing;
        }

        HashSet<T> beansOfTypeList = new HashSet<>();
        Collection<BeanDefinition<T>> processedDefinitions = new ArrayList<>();

        boolean allCandidatesAreSingleton = false;
        Collection<T> beans;
        for (Map.Entry<BeanKey, BeanRegistration> entry : singletonObjects.entrySet()) {
            BeanRegistration reg = entry.getValue();
            Object instance = reg.bean;
            if (beanType.isInstance(instance)) {
                if (!beansOfTypeList.contains(instance)) {
                    if (!hasQualifier) {

                        if (LOG.isTraceEnabled()) {
                            Qualifier registeredQualifier = entry.getKey().qualifier;
                            if (registeredQualifier != null) {
                                LOG.trace("Found existing bean for type {} {}: {} ", beanType.getName(), instance);
                            } else {
                                LOG.trace("Found existing bean for type {}: {} ", beanType.getName(), instance);
                            }
                        }

                        beansOfTypeList.add((T) instance);
                        processedDefinitions.add(reg.beanDefinition);
                    } else {
                        Optional result = qualifier.reduce(beanType, Stream.of(reg.beanDefinition)).findFirst();
                        if (result.isPresent()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Found existing bean for type {} {}: {} ", qualifier, beanType.getName(), instance);
                            }

                            beansOfTypeList.add((T) instance);
                            processedDefinitions.add(reg.beanDefinition);
                        }
                    }
                }
            }
        }
        Collection<BeanDefinition<T>> candidates = findBeanCandidatesInternal(beanType);
        if (hasQualifier) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Qualifying bean [{}] for qualifier: {} ", beanType.getName(), qualifier);
            }
            Stream<BeanDefinition<T>> candidateStream = candidates.stream();
            candidateStream = applyBeanResolutionFilters(resolutionContext, candidateStream);

            List<BeanDefinition<T>> reduced = qualifier.reduce(beanType, candidateStream)
                .collect(Collectors.toList());
            if (!reduced.isEmpty()) {
                for (BeanDefinition<T> definition : reduced) {
                    if (processedDefinitions.contains(definition)) continue;
                    if (definition.isSingleton()) {
                        allCandidatesAreSingleton = true;
                    }
                    addCandidateToList(resolutionContext, beanType, definition, beansOfTypeList, qualifier, reduced.size() == 1);
                }
                beans = beansOfTypeList;
            } else {

                if (LOG.isDebugEnabled() && beansOfTypeList.isEmpty()) {
                    LOG.debug("Found no matching beans of type [{}] for qualifier: {} ", beanType.getName(), qualifier);
                }
                allCandidatesAreSingleton = true;
                beans = beansOfTypeList;
            }
        } else if (!candidates.isEmpty()) {
            boolean hasNonSingletonCandidate = false;
            int candidateCount = candidates.size();
            Stream<BeanDefinition<T>> candidateStream = candidates.stream();
            candidateStream = applyBeanResolutionFilters(resolutionContext, candidateStream)
                .filter(c -> !processedDefinitions.contains(c));

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

    private <T> Stream<BeanDefinition<T>> applyBeanResolutionFilters(BeanResolutionContext resolutionContext, Stream<BeanDefinition<T>> candidateStream) {
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

    private <T> void addCandidateToList(BeanResolutionContext resolutionContext, Class<T> beanType, BeanDefinition<T> candidate, Collection<T> beansOfTypeList, Qualifier<T> qualifier, boolean singleCandidate) {
        T bean;
        if (candidate.isSingleton()) {
            synchronized (singletonObjects) {
                bean = doCreateBean(resolutionContext, candidate, qualifier, true, null);
                registerSingletonBean(candidate, beanType, bean, qualifier, singleCandidate);
            }
        } else {
            bean = getScopedBeanForDefinition(resolutionContext, beanType, qualifier, true, candidate);
        }

        beansOfTypeList.add(bean);
    }

    private static abstract class AbstractExecutionHandle<T, R> implements MethodExecutionHandle<R> {
        protected final ExecutableMethod<T, R> method;

        public AbstractExecutionHandle(ExecutableMethod<T, R> method) {
            this.method = method;
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

    private static final class ObjectExecutionHandle<T, R> extends AbstractExecutionHandle<T, R> {
        private final T target;

        ObjectExecutionHandle(T target, ExecutableMethod<T, R> method) {
            super(method);
            this.target = target;
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

    private static final class BeanExecutionHandle<T, R> extends AbstractExecutionHandle<T, R> {
        private final BeanContext beanContext;
        private final Class<T> beanType;
        private final Qualifier<T> qualifier;

        public BeanExecutionHandle(BeanContext beanContext, Class<T> beanType, Qualifier<T> qualifier, ExecutableMethod<T, R> method) {
            super(method);
            this.beanContext = beanContext;
            this.beanType = beanType;
            this.qualifier = qualifier;
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
            return method.invoke(beanContext.getBean(beanType, qualifier), arguments);
        }

    }

    static final class BeanKey<T> implements BeanIdentifier {
        private final Class beanType;
        private final Qualifier qualifier;

        BeanKey(Class<T> beanType, Qualifier<T> qualifier) {
            this.beanType = beanType;
            this.qualifier = qualifier;
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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BeanKey that = (BeanKey) o;

            if (!beanType.equals(that.beanType)) return false;
            return qualifier != null ? qualifier.equals(that.qualifier) : that.qualifier == null;
        }

        @Override
        public int hashCode() {
            int result = beanType.hashCode();
            result = 31 * result + (qualifier != null ? qualifier.hashCode() : 0);
            return result;
        }

        @Override
        public String getName() {
            if (qualifier instanceof Named) {
                return ((Named) qualifier).getName();
            }
            return Primary.class.getSimpleName();
        }
    }

    private static class NoInjectionBeanDefinition<T> implements BeanDefinition<T>, BeanDefinitionReference<T> {
        private final Class<?> singletonClass;

        public NoInjectionBeanDefinition(Class singletonClass) {
            this.singletonClass = singletonClass;
        }

        @Override
        public Optional<Class<? extends Annotation>> getScope() {
            return Optional.of(Singleton.class);
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
            Optional<Method> method = ReflectionUtils.findMethod(singletonClass, name, argumentTypes);
            return method.map(theMethod -> new ReflectionExecutableMethod(this, theMethod));
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
            return ReflectionUtils.findMethodsByName(singletonClass, name)
                .map((method -> new ReflectionExecutableMethod(this, method)));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            NoInjectionBeanDefinition that = (NoInjectionBeanDefinition) o;

            return singletonClass.equals(that.singletonClass);
        }

        @Override
        public int hashCode() {
            return singletonClass.hashCode();
        }

        @Override
        public boolean hasDeclaredAnnotation(String annotation) {
            return false;
        }

        @Override
        public boolean hasAnnotation(String annotation) {
            return false;
        }

        @Override
        public boolean hasStereotype(String annotation) {
            return false;
        }

        @Override
        public boolean hasDeclaredStereotype(String annotation) {
            return false;
        }

        @Override
        public Set<String> getAnnotationNamesByStereotype(String stereotype) {
            return Collections.emptySet();
        }

        @Override
        public ConvertibleValues<Object> getValues(String annotation) {
            return ConvertibleValues.empty();
        }

        @Override
        public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
            return OptionalValues.empty();
        }

        @Override
        public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
            return Optional.empty();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            return singletonClass.getAnnotation(annotationClass);
        }

        @Override
        public Annotation[] getAnnotations() {
            return singletonClass.getAnnotations();
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return singletonClass.getDeclaredAnnotations();
        }

        @Override
        public String getBeanDefinitionName() {
            return singletonClass.getName();
        }

        @Override
        public String getReplacesBeanTypeName() {
            return null;
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
