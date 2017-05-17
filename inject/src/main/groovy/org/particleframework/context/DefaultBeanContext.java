package org.particleframework.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.context.exceptions.DependencyInjectionException;
import org.particleframework.context.exceptions.NoSuchBeanException;
import org.particleframework.context.exceptions.NonUniqueBeanException;
import org.particleframework.inject.*;

import javax.inject.Provider;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The default context implementation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultBeanContext implements BeanContext {

    private final Iterator<BeanDefinitionClass> componentDefinitionClassIterator;
    private final Map<Class, BeanDefinitionClass> componentDefinitionsClasses = new ConcurrentHashMap<>(50);
    private final Map<Class, BeanDefinition> componentDefinitions = new ConcurrentHashMap<>(50);
    private final Cache<BeanKey, Collection<Object>> initializedObjectsByType = Caffeine.newBuilder()
            .maximumSize(50)
            .build();
    private final Map<BeanKey, Object> singletonObjects = new ConcurrentHashMap<>(50);
    private final ClassLoader classLoader;

    public DefaultBeanContext() {
        this(BeanContext.class.getClassLoader());
    }

    public DefaultBeanContext(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.componentDefinitionClassIterator = resolveComponentDefinitionClasses().iterator();
    }

    @Override
    public void start() {
        consumeAllComponentDefinitionClasses();
    }

    @Override
    public void close() throws IOException {
        // TODO: run shutdown hooks
    }

    @Override
    public <T> T getBean(Class<T> beanType, Qualifier<T> qualifier) {
        return getBean(null, beanType, qualifier);
    }

    @Override
    public <T> Iterable<T> getBeansOfType(Class<T> beanType) {
        return getBeansOfType(null, beanType);
    }

    @Override
    public <T> T inject(T instance) {
        Collection<? extends BeanDefinition<?>> candidates = findBeanCandidates(instance.getClass());
        if(!candidates.isEmpty()) {
            BeanDefinition definition = candidates.iterator().next();
            injectionDefinitionIfPossible(definition, instance);
        }
        return instance;
    }

    @Override
    public <T> T createBean(Class<T> beanType) {
        Collection<BeanDefinition<T>> candidates = findBeanCandidates(beanType);

        int size = candidates.size();
        if (size > 0) {
            if (size == 1) {
                BeanDefinition<T> definition = candidates.iterator().next();
                return doCreateBean(null, definition);
            } else {
                throw new NonUniqueBeanException(beanType, candidates.iterator());
            }
        }
        throw new NoSuchBeanException("No bean of type [" + beanType.getName() + "] exists");
    }

    <T> Iterable<T> getBeansOfType(BeanResolutionContext resolutionContext, Class<T> beanType) {
        return getBeansOfTypeInternal(resolutionContext, beanType, null);
    }

    <T> Iterable<T> getBeansOfType(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        return getBeansOfTypeInternal(resolutionContext, beanType, qualifier);
    }


    <T> Provider<T> getBeanProvider(BeanResolutionContext resolutionContext, Class<T> beanType) {
        return getBeanProvider(resolutionContext, beanType, null);
    }

    <T> Provider<T> getBeanProvider(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        T bean = (T) singletonObjects.get(new BeanKey(beanType,qualifier));
        if (bean != null) {
            return new ResolvedProvider<>(bean);
        }

        BeanDefinition<T> definition = findConcreteCandidate(beanType, qualifier);
        if(definition != null) {
            return new UnresolvedProvider<>(definition.getType(), this);
        }
        else {
            throw new NoSuchBeanException("No bean of type [" + beanType.getName() + "] exists");
        }
    }

    <T> T getBean(BeanResolutionContext resolutionContext, Class<T> beanType) {
        return getBean(resolutionContext, beanType, null);
    }

    <T> T getBean(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        T bean = (T) singletonObjects.get(new BeanKey(beanType, qualifier));
        if (bean != null) {
            return bean;
        }

        BeanDefinition<T> definition = findConcreteCandidate(beanType, qualifier);

        if( definition != null ) {

            if(definition.isSingleton()) {

                synchronized (singletonObjects) {
                    T createdBean = doCreateBean(resolutionContext, definition);
                    registerSingletonBean(definition, beanType, createdBean, qualifier);
                    return createdBean;
                }
            }
            else {
                return doCreateBean(resolutionContext, definition);
            }
        }
        else {
            throw new NoSuchBeanException("No bean of type [" + beanType.getName() + "] exists");
        }
    }
    protected Iterable<BeanDefinitionClass> resolveComponentDefinitionClasses() {
        return ServiceLoader.load(BeanDefinitionClass.class, classLoader);
    }


    private <T> T doCreateBean(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition) {
        T bean;
        if(resolutionContext == null) {
            resolutionContext = new DefaultBeanResolutionContext(this, beanDefinition);
        }

        if (beanDefinition instanceof BeanFactory) {
            BeanFactory<T> beanFactory = (BeanFactory<T>) beanDefinition;
            try {
                bean = beanFactory.build(resolutionContext,this, beanDefinition);
            } catch (Throwable e) {
                if(e instanceof DependencyInjectionException) {
                    throw e;
                }
                else {
                    throw new BeanInstantiationException("Error instantiating bean of type [" + beanDefinition.getName() + "]: " + e.getMessage(), e);
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
                    constructorArgs[i] = getBean(argument);
                }
                bean = constructor.invoke(constructorArgs);
            }

            injectionDefinitionIfPossible(beanDefinition, bean);
        }

        return bean;
    }

    private <T> BeanDefinition<T> findConcreteCandidate(Class<T> beanType, Qualifier<T> qualifier) {
        Collection<BeanDefinition<T>> candidates = findBeanCandidates(beanType);

        int size = candidates.size();
        BeanDefinition<T> definition = null;
        if (size > 0) {
            if (size == 1) {
                definition = candidates.iterator().next();
            } else {
                if(qualifier != null) {
                    definition = qualifier.qualify(beanType, candidates.stream());
                    if(definition == null) {
                        throw new NonUniqueBeanException(beanType, candidates.iterator());
                    }
                }
                else {
                    Collection<BeanDefinition<T>> exactMatches = filterExactMatch(beanType, candidates);
                    if(exactMatches.size() == 1) {
                        definition = exactMatches.iterator().next();
                    }
                    else {
                        throw new NonUniqueBeanException(beanType, candidates.iterator());
                    }
                }
            }
        }
        return definition;
    }

    private <T> Collection<BeanDefinition<T>> filterExactMatch(final Class<T> beanType, Collection<BeanDefinition<T>> candidates) {
        Stream<BeanDefinition<T>> filteredResults = candidates
                                                            .stream()
                                                            .filter((BeanDefinition<T> candidate) ->
                                                                    candidate.getType().equals(beanType)
                                                            );
        return filteredResults.collect(Collectors.toList());
    }

    private <T> void registerSingletonBean(BeanDefinition beanDefinition, Class<T> beanType, T createdBean, Qualifier<T> qualifier) {
        // for only one candidate create link to bean type as singleton
        singletonObjects.put(new BeanKey(beanType, qualifier), createdBean);
        singletonObjects.put(new BeanKey(createdBean.getClass(),qualifier), createdBean);
    }

    private void consumeAllComponentDefinitionClasses() {
        while (componentDefinitionClassIterator.hasNext()) {
            BeanDefinitionClass beanDefinitionClass = componentDefinitionClassIterator.next();
            componentDefinitionsClasses.put(beanDefinitionClass.getComponentType(), beanDefinitionClass);
        }
    }

    private <T> Collection<BeanDefinition<T>> findBeanCandidates(Class<T> beanType) {
        Collection<BeanDefinition<T>> candidates = new HashSet<>();
        // first traverse component definition classes and load candidates
        for (Map.Entry<Class, BeanDefinitionClass> componentDefinitionClassEntry : componentDefinitionsClasses.entrySet()) {
            Class componentType = componentDefinitionClassEntry.getKey();
            if (beanType.isAssignableFrom(componentType)) {
                // load it
                BeanDefinitionClass beanDefinitionClass = componentDefinitionClassEntry.getValue();
                BeanDefinition<T> beanDefinition = beanDefinitionClass.load();
                if(beanDefinition != null) {
                    componentDefinitions.put(componentType, beanDefinition);
                    candidates.add(beanDefinition);
                }
            }
        }
        for (Map.Entry<Class, BeanDefinition> componentDefinitionEntry : componentDefinitions.entrySet()) {
            BeanDefinition beanDefinition = componentDefinitionEntry.getValue();
            if (!candidates.contains(beanDefinition) && beanType.isAssignableFrom(beanDefinition.getType())) {
                candidates.add(beanDefinition);
            }
        }
        for (BeanDefinition<T> candidate : candidates) {
            componentDefinitionsClasses.remove(candidate.getType());
        }
        return candidates;
    }

    private <T> void injectionDefinitionIfPossible(BeanDefinition definition, T instance) {
        if(definition instanceof InjectableBeanDefinition) {
            ((InjectableBeanDefinition)definition).inject(this, instance);
        }
    }

    private <T> Collection<T> getBeansOfTypeInternal(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        BeanKey key = new BeanKey(beanType, qualifier);
        Collection<T> existing = (Collection<T>) initializedObjectsByType.getIfPresent(key);
        if (existing != null) {
            return Collections.unmodifiableCollection(existing);
        }

        Collection<T> beansOfTypeList = new ConcurrentLinkedQueue<>();

        Object intializedBean = singletonObjects.get(key);
        boolean allCandidatesAreSingleton = false;
        Collection<T> beans;
        if(intializedBean != null) {
            allCandidatesAreSingleton = true;
            // unique bean found
            beansOfTypeList.add((T) intializedBean);
            beans = Collections.unmodifiableCollection(beansOfTypeList);
        }
        else {
            Collection<BeanDefinition<T>> candidates = findBeanCandidates(beanType);
            if(qualifier != null) {
                BeanDefinition<T> qualified = qualifier.qualify(beanType, candidates.stream());
                if(qualified != null) {
                    if(qualified.isSingleton()) {
                        allCandidatesAreSingleton = true;
                    }
                    addCandidateToList(resolutionContext, beanType, qualified, beansOfTypeList);
                    beans = Collections.unmodifiableCollection(beansOfTypeList);
                }
                else {
                    beans = Collections.emptyList();
                }
            }
            else if (!candidates.isEmpty()) {
                boolean hasNonSingletonCandidate = false;
                for (BeanDefinition<T> candidate : candidates) {
                    if(!hasNonSingletonCandidate && !candidate.isSingleton()) {
                        hasNonSingletonCandidate = true;
                    }
                    addCandidateToList(resolutionContext, beanType, candidate, beansOfTypeList);
                }
                if(!hasNonSingletonCandidate) {
                    allCandidatesAreSingleton = true;
                }
                beans = Collections.unmodifiableCollection(beansOfTypeList);
            } else {
                allCandidatesAreSingleton = true;
                beans = Collections.emptyList();
            }
        }
        if(allCandidatesAreSingleton) {
            initializedObjectsByType.put(key, (Collection<Object>) beans);
        }
        return beans;
    }

    private <T> void addCandidateToList(BeanResolutionContext resolutionContext, Class<T> beanType, BeanDefinition<T> candidate, Collection<T> beansOfTypeList) {
        if(candidate.isSingleton()) {
            synchronized (singletonObjects) {
                T createdBean = doCreateBean(resolutionContext, candidate);
                registerSingletonBean(candidate, beanType, createdBean, null);
                beansOfTypeList.add(createdBean);
            }
        }
        else {
            T createdBean = doCreateBean(resolutionContext, candidate);
            beansOfTypeList.add(createdBean);
        }
    }

    <T> DefaultBeanDefinition<T> getComponentDefinition(Class<T> beanType) {
        return (DefaultBeanDefinition<T>)findConcreteCandidate(beanType, null);
    }

    private static final class BeanKey {
        private final Class beanType;
        private final Qualifier qualifier;

        public BeanKey(Class beanType, Qualifier qualifier) {
            this.beanType = beanType;
            this.qualifier = qualifier;
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

}
