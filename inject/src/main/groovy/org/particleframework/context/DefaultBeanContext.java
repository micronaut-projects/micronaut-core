package org.particleframework.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.event.BeanCreatedEvent;
import org.particleframework.context.event.BeanCreatedEventListener;
import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.context.exceptions.DependencyInjectionException;
import org.particleframework.context.exceptions.NoSuchBeanException;
import org.particleframework.context.exceptions.NonUniqueBeanException;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.inject.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The default context implementation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultBeanContext implements BeanContext {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultBeanContext.class);
    private final Iterator<BeanDefinitionClass> beanDefinitionClassIterator;
    private final Iterator<BeanConfiguration> beanConfigurationIterator;
    private final Map<String, BeanDefinitionClass> beanDefinitionsClasses = new ConcurrentHashMap<>(30);
    protected final Map<Class, BeanDefinition> beanDefinitions = new ConcurrentHashMap<>(30);
    protected final Map<String, BeanConfiguration> beanConfigurations = new ConcurrentHashMap<>(4);
    private final Cache<BeanKey, Collection<Object>> initializedObjectsByType = Caffeine.newBuilder()
            .maximumSize(30)
            .build();
    private final Map<BeanKey, BeanRegistration> singletonObjects = new ConcurrentHashMap<>(30);
    private final ClassLoader classLoader;
    private final Set<Class> thisInterfaces = ReflectionUtils.getAllInterfaces(getClass());

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
        this.classLoader = classLoader;
        this.beanDefinitionClassIterator = resolveBeanDefinitionClasses().iterator();
        this.beanConfigurationIterator = resolveBeanConfigurarions().iterator();
    }

    /**
     * The start method will read all bean definition classes found on the classpath and initialize any pre-required state
     */
    @Override
    public BeanContext start() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting BeanContext");
        }
        readAllBeanConfigurations();
        readAllBeanDefinitionClasses();

        if (LOG.isDebugEnabled()) {
            LOG.debug("BeanContext Started.");
        }
        return this;
    }

    /**
     * The close method will shut down the context calling {@link javax.annotation.PreDestroy} hooks on loaded singletons.
     */
    @Override
    public BeanContext stop() {
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
                    ((DisposableBeanDefinition) def).dispose(this, bean);
                } catch (Throwable e) {
                    LOG.error("Error disposing of bean registration [" + def.getName() + "]: " + e.getMessage(), e);
                }
            } else if (bean instanceof LifeCycle) {
                ((LifeCycle) bean).stop();
            }
        });

        return this;
    }

    @Override
    public <T> Iterable<T> findServices(Class<T> type, Predicate<String> condition) {
        return ServiceLoader.load(type, getClassLoader());
    }

    @Override
    public <R> Optional<MethodExecutionHandle<R>> findExecutionHandle(Class<?> beanType, String method, Class... arguments) {
        Optional<? extends BeanDefinition<?>> foundBean = findBeanDefinition(beanType);
        if (foundBean.isPresent()) {
            BeanDefinition<?> beanDefinition = foundBean.get();
            Optional<? extends ExecutableMethod<?, Object>> foundMethod = beanDefinition.findMethod(method, arguments);
            if (foundMethod.isPresent()) {

                Optional<MethodExecutionHandle<R>> executionHandle = foundMethod.map((ExecutableMethod executableMethod) ->
                        new BeanExecutionHandle<>(this, beanType, executableMethod)
                );
                return executionHandle;
            } else {
                return beanDefinition.findPossibleMethods(method)
                        .findFirst()
                        .map((ExecutableMethod executableMethod) ->
                                new BeanExecutionHandle<>(this, beanType, executableMethod)
                        );
            }
        }
        return Optional.empty();
    }

    @Override
    public <T> BeanContext registerSingleton(Class<T> beanType, T singleton) {
        if (singleton == null) {
            throw new IllegalArgumentException("Passed singleton cannot be null");
        }
        BeanKey beanKey = new BeanKey(beanType, null);
        synchronized (singletonObjects) {
            initializedObjectsByType.invalidateAll();

            BeanDefinition<T> beanDefinition = findConcreteCandidate(beanType, null, false, true);
            if (beanDefinition != null) {
                if (beanDefinition instanceof InjectableBeanDefinition) {
                    doInject(new DefaultBeanResolutionContext(this, beanDefinition), singleton, beanDefinition);
                    singletonObjects.put(beanKey, new BeanRegistration<>(beanDefinition, singleton));
                }
            } else {
                singletonObjects.put(beanKey, new BeanRegistration<>(new NoInjectionBeanDefinition<>(beanType), singleton));
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
    public <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType) {
        BeanKey beanKey = new BeanKey(beanType, null);
        BeanRegistration reg = singletonObjects.get(beanKey);
        if (reg != null) {
            return Optional.of(reg.beanDefinition);
        }
        Collection<BeanDefinition<T>> beanCandidates = findBeanCandidates(beanType);
        if (beanCandidates.isEmpty()) {
            return Optional.empty();
        } else {
            if (beanCandidates.size() == 1) {
                return Optional.of(beanCandidates.iterator().next());
            } else {
                throw new NonUniqueBeanException(beanType, beanCandidates.iterator());
            }
        }
    }

    @Override
    public boolean containsBean(Class beanType, Qualifier qualifier) {
        BeanKey beanKey = new BeanKey(beanType, qualifier);
        if (singletonObjects.containsKey(beanKey)) {
            return true;
        } else {
            Collection<BeanDefinition> beanCandidates = findBeanCandidates(beanType);
            Optional<BeanDefinition> definition = beanCandidates.stream().filter(beanDefinition -> !beanDefinition.isProvided()).findFirst();
            return definition.isPresent();
        }
    }

    @Override
    public <T> T getBean(Class<T> beanType, Qualifier<T> qualifier) {
        return getBean(null, beanType, qualifier);
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
    public <T> Stream<T> streamOfType(Class<T> beanType, Qualifier<T> qualifier) {
        return streamOfType(null, beanType, qualifier);
    }

    <T> Stream<T> streamOfType(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        Collection<BeanDefinition<T>> candidates = findBeanCandidates(beanType);
        if (candidates.isEmpty()) {
            return Stream.empty();
        } else {
            BeanKey beanKey = new BeanKey(beanType, qualifier);
            BeanRegistration<T> beanRegistration = singletonObjects.get(beanKey);
            if (beanRegistration != null) {
                return Stream.of(beanRegistration.bean);
            } else {
                Iterator<BeanDefinition<T>> candidateIterator = candidates.iterator();

                return Stream.generate(() -> {
                    BeanDefinition<T> definition = candidateIterator.next();

                    if (definition.isSingleton()) {
                        BeanRegistration<T> candidateRegistration = singletonObjects.get(beanKey);
                        if (candidateRegistration != null) {
                            return candidateRegistration.bean;
                        } else {
                            synchronized (singletonObjects) {
                                T createdBean = doCreateBean(resolutionContext, definition, qualifier, true);
                                registerSingletonBean(definition, beanType, createdBean, qualifier, candidates.size() == 1);
                                return createdBean;
                            }
                        }
                    } else {
                        return doCreateBean(resolutionContext, definition, qualifier, false);
                    }
                }).limit(candidates.size());
            }
        }
    }

    @Override
    public <T> T inject(T instance) {
        Class<?> beanType = instance.getClass();
        BeanDefinition definition = findConcreteCandidate(beanType, null, true, true);
        if (definition != null) {
            if (definition instanceof InjectableBeanDefinition) {
                ((InjectableBeanDefinition) definition).inject(this, instance);
            }
        }
        return instance;
    }

    @Override
    public <T> T createBean(Class<T> beanType, Qualifier<T> qualifier) {
        BeanDefinition<T> candidate = findConcreteCandidate(beanType, qualifier, true, false);
        if (candidate != null) {
            return doCreateBean(new DefaultBeanResolutionContext(this, candidate), candidate, qualifier, false);
        }
        throw new NoSuchBeanException(beanType);
    }

    @Override
    public <T> T destroyBean(Class<T> beanType) {
        T bean = null;
        BeanKey beanKey = new BeanKey(beanType, null);

        synchronized (singletonObjects) {
            if (singletonObjects.containsKey(beanKey)) {
                BeanRegistration<T> beanRegistration = singletonObjects.get(beanKey);
                bean = beanRegistration.bean;
                if (bean != null) {
                    singletonObjects.remove(beanKey);
                }
            }
        }

        if (bean != null) {
            BeanDefinition<T> definition = findConcreteCandidate(beanType, null, false, true);
            if (definition instanceof DisposableBeanDefinition) {
                ((DisposableBeanDefinition<T>) definition).dispose(this, bean);
            }

        }
        return bean;
    }

    <T> T inject(BeanResolutionContext resolutionContext, BeanDefinition requestingBeanDefinition, T instance) {
        Class<?> beanType = instance.getClass();
        BeanDefinition definition = findConcreteCandidate(beanType, null, false, true);
        if (definition != null) {
            if (requestingBeanDefinition != null && requestingBeanDefinition.equals(definition)) {
                // bail out, don't inject for bean definition in creation
                return instance;
            }
            doInject(resolutionContext, instance, definition);
        }
        return instance;
    }

    private <T> void doInject(BeanResolutionContext resolutionContext, T instance, BeanDefinition definition) {
        if (definition instanceof InjectableBeanDefinition) {
            ((InjectableBeanDefinition) definition).inject(resolutionContext, this, instance);
        }
        if (definition instanceof InitializingBeanDefinition) {
            ((InitializingBeanDefinition) definition).initialize(resolutionContext, this, instance);
        }
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


    <T> Provider<T> getBeanProvider(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        BeanRegistration<T> beanRegistration = singletonObjects.get(new BeanKey(beanType, qualifier));
        if (beanRegistration != null) {
            return new ResolvedProvider<>(beanRegistration.bean);
        }

        BeanDefinition<T> definition = findConcreteCandidate(beanType, qualifier, true, false);
        if (definition != null) {
            return new UnresolvedProvider<>(definition.getType(), this);
        } else {
            throw new NoSuchBeanException(beanType);
        }
    }

    public <T> T getBean(BeanResolutionContext resolutionContext, Class<T> beanType) {
        return getBean(resolutionContext, beanType, null);
    }

    public <T> T getBean(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        // allow injection the bean context
        if (thisInterfaces.contains(beanType)) {
            return (T) this;
        }

        BeanRegistration<T> beanRegistration = singletonObjects.get(new BeanKey(beanType, qualifier));
        if (beanRegistration != null) {
            return beanRegistration.bean;
        }

        BeanDefinition<T> definition = findConcreteCandidate(beanType, qualifier, true, false);

        if (definition != null) {

            if (definition.isProvided() && beanType == definition.getType()) {
                throw new NoSuchBeanException("No bean of type [" + beanType.getName() + "] exists");
            } else if (definition.isSingleton()) {

                return createAndRegisterSingleton(resolutionContext, definition, beanType, qualifier);
            } else {
                return doCreateBean(resolutionContext, definition, qualifier, false);
            }
        } else {
            T bean = findExistingCompatibleSingleton(beanType, qualifier);
            if (bean == null) {
                throw new NoSuchBeanException("No bean of type [" + beanType.getName() + "] exists");
            } else {
                return bean;
            }
        }
    }

    private <T> T findExistingCompatibleSingleton(Class<T> beanType, Qualifier<T> qualifier) {
        T bean = null;
        for (Map.Entry<BeanKey, BeanRegistration> entry : singletonObjects.entrySet()) {
            BeanKey key = entry.getKey();
            if (qualifier == null || qualifier.equals(key.qualifier)) {
                BeanRegistration reg = entry.getValue();
                if (beanType.isInstance(reg.bean)) {
                    synchronized (singletonObjects) {
                        bean = (T) reg.bean;
                        registerSingletonBean(reg.beanDefinition, beanType, bean, qualifier, true);
                    }
                }
            }
        }
        return bean;
    }

    <T> Optional<T> findBean(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        // allow injection the bean context
        if (thisInterfaces.contains(beanType)) {
            return Optional.of((T) this);
        }

        BeanRegistration<T> beanRegistration = singletonObjects.get(new BeanKey(beanType, qualifier));
        if (beanRegistration != null) {
            return Optional.of(beanRegistration.bean);
        }

        BeanDefinition<T> definition = findConcreteCandidate(beanType, qualifier, true, false);

        if (definition != null) {

            if (definition.isProvided() && beanType == definition.getType()) {
                return Optional.empty();
            } else if (definition.isSingleton()) {
                T bean = createAndRegisterSingleton(resolutionContext, definition, beanType, qualifier);
                return Optional.of(bean);
            } else {
                T bean = doCreateBean(resolutionContext, definition, qualifier, false);
                return Optional.of(bean);
            }
        } else {
            T bean = findExistingCompatibleSingleton(beanType, qualifier);
            if (bean == null) {
                return Optional.empty();
            } else {
                return Optional.of(bean);
            }
        }
    }


    /**
     * Resolves the {@link BeanDefinitionClass} class instances. Default implementation uses ServiceLoader pattern
     *
     * @return The bean definition classes
     */
    protected Iterable<BeanDefinitionClass> resolveBeanDefinitionClasses() {
        return ServiceLoader.load(BeanDefinitionClass.class, classLoader);
    }

    /**
     * Resolves the {@link BeanConfiguration} class instances. Default implementation uses ServiceLoader pattern
     *
     * @return The bean definition classes
     */
    protected Iterable<BeanConfiguration> resolveBeanConfigurarions() {
        return ServiceLoader.load(BeanConfiguration.class, classLoader);
    }

    /**
     * Registers an active configuration
     *
     * @param configuration The configuration to register
     */
    protected void registerConfiguration(BeanConfiguration configuration) {
        beanConfigurations.put(configuration.getName(), configuration);
    }

    private <T> T createAndRegisterSingleton(BeanResolutionContext resolutionContext, BeanDefinition<T> definition, Class<T> beanType, Qualifier<T> qualifier) {
        synchronized (singletonObjects) {
            T createdBean = doCreateBean(resolutionContext, definition, qualifier, true);
            registerSingletonBean(definition, beanType, createdBean, qualifier, true);
            return createdBean;
        }
    }

    private <T> T doCreateBean(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition, Qualifier<T> qualifier, boolean isSingleton) {
        BeanRegistration<T> beanRegistration = isSingleton ? singletonObjects.get(new BeanKey(beanDefinition.getType(), qualifier)) : null;
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
                bean = beanFactory.build(resolutionContext, this, beanDefinition);
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

            Collection<BeanCreatedEventListener> beanCreatedEventListeners = getBeansOfType(resolutionContext, BeanCreatedEventListener.class, null);
            for (BeanCreatedEventListener listener : beanCreatedEventListeners) {
                Optional<Class> targetType = GenericTypeUtils.resolveInterfaceTypeArgument(listener.getClass(), BeanCreatedEventListener.class);
                if (!targetType.isPresent() || targetType.get().isInstance(bean)) {
                    bean = (T) listener.onCreated(new BeanCreatedEvent(this, beanDefinition, bean));
                    if (bean == null) {
                        throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onCreated event");
                    }
                }
            }
        }
        if (beanDefinition instanceof ValidatedBeanDefinition) {
            bean = ((ValidatedBeanDefinition<T>) beanDefinition).validate(resolutionContext, bean);
        }
        return bean;
    }

    private <T> BeanDefinition<T> findConcreteCandidate(Class<T> beanType, Qualifier<T> qualifier, boolean throwNonUnique, boolean includeProvided) {
        Collection<BeanDefinition<T>> candidates = findBeanCandidates(beanType);

        if (!includeProvided) {
            candidates = candidates.stream()
                    .filter(def -> !def.isProvided())
                    .collect(Collectors.toList());
        }


        int size = candidates.size();
        BeanDefinition<T> definition = null;
        if (size > 0) {
            if (size == 1) {
                definition = candidates.iterator().next();
            } else {
                if (qualifier != null) {
                    Stream<BeanDefinition<T>> qualified = qualifier.reduce(beanType, candidates.stream());
                    List<BeanDefinition<T>> beanDefinitionList = qualified.collect(Collectors.toList());
                    if(beanDefinitionList.isEmpty()) {
                        return null;
                    }

                    Optional<BeanDefinition<T>> primary = beanDefinitionList.stream()
                            .filter((candidate) -> candidate.getType().getAnnotation(Primary.class) != null)
                            .findFirst();
                    if (primary.isPresent()) {
                        return primary.get();
                    } else {

                        Optional<BeanDefinition<T>> first = beanDefinitionList.stream().findFirst();
                        if (!first.isPresent()) {
                            if (throwNonUnique) {
                                throw new NonUniqueBeanException(beanType, candidates.iterator());
                            }
                        }
                        else {
                            definition = first.get();
                        }
                    }
                } else {
                    Optional<BeanDefinition<T>> primary = candidates.stream()
                            .filter((candidate) -> candidate.getType().getAnnotation(Primary.class) != null)
                            .findFirst();
                    if (primary.isPresent()) {
                        return primary.get();
                    } else {
                        Collection<BeanDefinition<T>> exactMatches = filterExactMatch(beanType, candidates);
                        if (exactMatches.size() == 1) {
                            definition = exactMatches.iterator().next();
                        } else {

                            if (throwNonUnique) {
                                throw new NonUniqueBeanException(beanType, candidates.iterator());
                            }
                        }
                    }
                }
            }
        }
        return definition;
    }

    private void readAllBeanConfigurations() {
        while (beanConfigurationIterator.hasNext()) {
            BeanConfiguration configuration = beanConfigurationIterator.next();
            registerConfiguration(configuration);
        }

        if(LOG.isDebugEnabled()) {
            String activeConfigurations = beanConfigurations.values()
                                                .stream()
                                                .filter(config -> config.isEnabled(this))
                                                .map(BeanConfiguration::getName)
                                                .collect(Collectors.joining(","));
            LOG.debug("Loaded active configurations: {}", activeConfigurations);
        }
    }

    private <T> Collection<BeanDefinition<T>> filterExactMatch(final Class<T> beanType, Collection<BeanDefinition<T>> candidates) {
        Stream<BeanDefinition<T>> filteredResults = candidates
                .stream()
                .filter((BeanDefinition<T> candidate) ->
                        candidate.getType() == beanType
                );
        return filteredResults.collect(Collectors.toList());
    }

    private <T> void registerSingletonBean(BeanDefinition<T> beanDefinition, Class<T> beanType, T createdBean, Qualifier<T> qualifier, boolean singleCandidate) {
        // for only one candidate create link to bean type as singleton
        BeanRegistration<T> registration = new BeanRegistration<>(beanDefinition, createdBean);
        if (singleCandidate) {
            singletonObjects.put(new BeanKey(beanType, qualifier), registration);
        }

        singletonObjects.put(new BeanKey(createdBean.getClass(), qualifier), registration);
    }

    private void readAllBeanDefinitionClasses() {
        List<BeanDefinitionClass> contextScopeBeans = new ArrayList<>();
        Map<String, BeanDefinitionClass> replacements = new LinkedHashMap<>();
        while (beanDefinitionClassIterator.hasNext()) {
            BeanDefinitionClass beanDefinitionClass = beanDefinitionClassIterator.next();
            if (beanDefinitionClass.isEnabled(this)) {
                String replacesBeanTypeName = beanDefinitionClass.getReplacesBeanTypeName();
                if (replacesBeanTypeName != null) {
                    replacements.put(replacesBeanTypeName, beanDefinitionClass);
                }
                if (beanDefinitionClass.isContextScope()) {
                    contextScopeBeans.add(beanDefinitionClass);
                } else {
                    beanDefinitionsClasses.put(beanDefinitionClass.getBeanTypeName(), beanDefinitionClass);
                }
            }
        }

        Collection<BeanDefinitionClass> values = new HashSet<>(beanDefinitionsClasses.values());
        values.forEach(beanDefinitionClass -> {
                    for (BeanConfiguration configuration : beanConfigurations.values()) {
                        boolean enabled = configuration.isEnabled(this);
                        if (!enabled && configuration.isWithin(beanDefinitionClass)) {
                            beanDefinitionsClasses.remove(beanDefinitionClass.getBeanTypeName());
                            contextScopeBeans.remove(beanDefinitionClass);
                        }
                    }
                }
        );

        // This logic handles the @Replaces annotation
        // we go through all of the replacements and if the replacement hasn't been discarded
        // we lookup the bean to be replaced and remove it from the bean definitions and context scope beans
        for (Map.Entry<String, BeanDefinitionClass> replacement : replacements.entrySet()) {
            BeanDefinitionClass replacementBeanClass = replacement.getValue();
            String beanNameToBeReplaced = replacement.getKey();
            if (beanDefinitionsClasses.containsValue(replacementBeanClass) && beanDefinitionsClasses.containsKey(beanNameToBeReplaced)) {
                BeanDefinitionClass removedClass = beanDefinitionsClasses.remove(beanNameToBeReplaced);
                contextScopeBeans.remove(removedClass);
            }
        }

        initializeContext(contextScopeBeans);
    }

    protected void initializeContext(List<BeanDefinitionClass> contextScopeBeans) {
        for (BeanDefinitionClass contextScopeBean : contextScopeBeans) {
            BeanDefinition beanDefinition = contextScopeBean.load();
            beanDefinitions.put(beanDefinition.getType(), beanDefinition);
            createAndRegisterSingleton(new DefaultBeanResolutionContext(this, beanDefinition), beanDefinition, beanDefinition.getType(), null);
        }
    }

    private <T> Collection<BeanDefinition<T>> findBeanCandidates(Class<T> beanType) {
        Collection<BeanDefinition<T>> candidates = new HashSet<>();
        // first traverse component definition classes and load candidates
        for (Map.Entry<String, BeanDefinitionClass> beanDefinitionClassEntry : beanDefinitionsClasses.entrySet()) {
            Class candidateType = beanDefinitionClassEntry.getValue().getBeanType();
            if (beanType.isAssignableFrom(candidateType)) {
                // load it
                BeanDefinitionClass beanDefinitionClass = beanDefinitionClassEntry.getValue();
                try {
                    BeanDefinition<T> beanDefinition = beanDefinitionClass.load();
                    if (beanDefinition != null) {

                        candidates.add(beanDefinition);
                    }
                } catch (NoClassDefFoundError noClassDefFoundError) {
                    throw new BeanInstantiationException("Bean definition ["+beanDefinitionClass.getBeanTypeName()+"] could not be loaded due to missing dependencies: " + noClassDefFoundError.getMessage(), noClassDefFoundError);
                }
            }
        }
        for (BeanDefinition<T> candidate : candidates) {
            beanDefinitions.put(candidate.getType(), candidate);
            beanDefinitionsClasses.remove(candidate.getType());
        }

        for (Map.Entry<Class, BeanDefinition> componentDefinitionEntry : beanDefinitions.entrySet()) {
            BeanDefinition beanDefinition = componentDefinitionEntry.getValue();
            if (!candidates.contains(beanDefinition) && beanType.isAssignableFrom(beanDefinition.getType())) {
                candidates.add(beanDefinition);
            }
        }

        return candidates;
    }

    private <T> Collection<T> getBeansOfTypeInternal(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        BeanKey key = new BeanKey(beanType, qualifier);
        Collection<T> existing = (Collection<T>) initializedObjectsByType.getIfPresent(key);
        if (existing != null) {
            return Collections.unmodifiableCollection(existing);
        }

        Collection<T> beansOfTypeList = new ConcurrentLinkedQueue<>();

        BeanRegistration<T> beanReg = singletonObjects.get(key);
        boolean allCandidatesAreSingleton = false;
        Collection<T> beans;
        if (beanReg != null) {
            allCandidatesAreSingleton = true;
            // unique bean found
            beansOfTypeList.add(beanReg.bean);
            beans = Collections.unmodifiableCollection(beansOfTypeList);
        } else {
            if (qualifier == null) {
                for (BeanRegistration reg : singletonObjects.values()) {
                    if (beanType.isInstance(reg.bean)) {
                        beansOfTypeList.add((T) reg.bean);
                    }
                }
            }
            Collection<BeanDefinition<T>> candidates = findBeanCandidates(beanType);
            if (qualifier != null) {
                Optional<BeanDefinition<T>> qualified = qualifier.qualify(beanType, candidates.stream());
                if (qualified.isPresent()) {
                    BeanDefinition<T> definition = qualified.get();
                    if (definition.isSingleton()) {
                        allCandidatesAreSingleton = true;
                    }
                    addCandidateToList(resolutionContext, beanType, definition, beansOfTypeList, qualifier, true);
                    beans = Collections.unmodifiableCollection(beansOfTypeList);
                } else {
                    beans = Collections.emptyList();
                }
            } else if (!candidates.isEmpty()) {
                boolean hasNonSingletonCandidate = false;
                int candidateCount = candidates.size();
                for (BeanDefinition<T> candidate : candidates) {
                    if (!hasNonSingletonCandidate && !candidate.isSingleton()) {
                        hasNonSingletonCandidate = true;
                    }
                    addCandidateToList(resolutionContext, beanType, candidate, beansOfTypeList, qualifier, candidateCount == 1);
                }
                if (!hasNonSingletonCandidate) {
                    allCandidatesAreSingleton = true;
                }
                beans = Collections.unmodifiableCollection(beansOfTypeList);
            } else {
                allCandidatesAreSingleton = true;
                beans = Collections.unmodifiableCollection(beansOfTypeList);
            }
        }
        if (allCandidatesAreSingleton) {
            initializedObjectsByType.put(key, (Collection<Object>) beans);
        }
        return beans;
    }

    private <T> void addCandidateToList(BeanResolutionContext resolutionContext, Class<T> beanType, BeanDefinition<T> candidate, Collection<T> beansOfTypeList, Qualifier<T> qualifier, boolean singleCandidate) {
        if (candidate.isSingleton()) {
            synchronized (singletonObjects) {
                T createdBean = doCreateBean(resolutionContext, candidate, qualifier, true);
                registerSingletonBean(candidate, beanType, createdBean, qualifier, singleCandidate);
                beansOfTypeList.add(createdBean);
            }
        } else {
            T createdBean = doCreateBean(resolutionContext, candidate, qualifier, false);
            beansOfTypeList.add(createdBean);
        }
    }

    private static final class BeanExecutionHandle<T, R> implements MethodExecutionHandle<R> {
        private final BeanContext beanContext;
        private final Class<T> beanType;
        private final ExecutableMethod<T, R> method;

        public BeanExecutionHandle(BeanContext beanContext, Class<T> beanType, ExecutableMethod<T, R> method) {
            this.beanContext = beanContext;
            this.beanType = beanType;
            this.method = method;
        }

        @Override
        public Class getDeclaringType() {
            return beanType;
        }

        @Override
        public Argument[] getArguments() {
            return method.getArguments();
        }

        @Override
        public R invoke(Object... arguments) {
            return method.invoke(beanContext.getBean(beanType), arguments);
        }

        @Override
        public String toString() {
            return method.toString();
        }

        @Override
        public ReturnType<R> getReturnType() {
            return method.getReturnType();
        }
    }

    private static final class BeanRegistration<T> {
        private final BeanDefinition<T> beanDefinition;
        private T bean;

        BeanRegistration(BeanDefinition<T> beanDefinition, T bean) {
            this.beanDefinition = beanDefinition;
            this.bean = bean;
        }

        @Override
        public String toString() {
            return "BeanRegistration: " + bean;
        }
    }

    private static final class BeanKey {
        private final Class beanType;
        private final Qualifier qualifier;

        BeanKey(Class beanType, Qualifier qualifier) {
            this.beanType = beanType;
            this.qualifier = qualifier;
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
    }

    private static class NoInjectionBeanDefinition<T> implements BeanDefinition<T> {
        private final Class singletonClass;

        public NoInjectionBeanDefinition(Class singletonClass) {
            this.singletonClass = singletonClass;
        }

        @Override
        public Annotation getScope() {
            return null;
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
        public Class getType() {
            return singletonClass;
        }

        @Override
        public ConstructorInjectionPoint getConstructor() {
            return null;
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
        public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class[] argumentTypes) {
            Optional<Method> method = ReflectionUtils.findMethod(singletonClass, name, argumentTypes);
            return method.map(theMethod -> new ReflectionExecutableMethod(this, theMethod));

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
    }

}
