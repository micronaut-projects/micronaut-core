package org.particleframework.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.context.exceptions.NoSuchBeanException;
import org.particleframework.context.exceptions.NonUniqueBeanException;
import org.particleframework.inject.*;

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
public class DefaultContext implements Context {

    private final Iterator<ComponentDefinitionClass> componentDefinitionClassIterator;
    private final Map<Class, ComponentDefinitionClass> componentDefinitionsClasses = new ConcurrentHashMap<>(50);
    private final Map<Class, ComponentDefinition> componentDefinitions = new ConcurrentHashMap<>(50);
    private final Cache<Class, Collection<Object>> initializedObjectsByType = Caffeine.newBuilder()
            .maximumSize(50)
            .build();
    private final Map<Class, Object> singletonObjects = new ConcurrentHashMap<>(50);
    private final ClassLoader classLoader;

    public DefaultContext() {
        this(Context.class.getClassLoader());
    }

    public DefaultContext(ClassLoader classLoader) {
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
    public <T> T getBean(Class<T> beanType) {
        return getBean(null, beanType);
    }

    @Override
    public <T> Iterable<T> getBeansOfType(Class<T> beanType) {
        return getBeansOfType(null, beanType);
    }

    @Override
    public <T> T inject(T instance) {
        Collection<? extends ComponentDefinition<?>> candidates = findBeanCandidates(instance.getClass());
        if(!candidates.isEmpty()) {
            ComponentDefinition definition = candidates.iterator().next();
            injectionDefinitionIfPossible(definition, instance);
        }
        return instance;
    }

    @Override
    public <T> T createBean(Class<T> beanType) {
        Collection<ComponentDefinition<T>> candidates = findBeanCandidates(beanType);

        int size = candidates.size();
        if (size > 0) {
            if (size == 1) {
                ComponentDefinition<T> definition = candidates.iterator().next();
                return doCreateBean(null, definition, beanType);
            } else {
                throw new NonUniqueBeanException(beanType, candidates);
            }
        }
        throw new NoSuchBeanException("No bean of type [" + beanType.getName() + "] exists");
    }

    <T> Iterable<T> getBeansOfType(ComponentResolutionContext resolutionContext, Class<T> beanType) {
        return getBeansOfTypeInternal(resolutionContext, beanType);
    }

    <T> T getBean(ComponentResolutionContext resolutionContext, Class<T> beanType) {
        T bean = (T) singletonObjects.get(beanType);
        if (bean != null) {
            return bean;
        }

        Collection<T> existing = (Collection<T>) initializedObjectsByType.getIfPresent(beanType);
        if (existing != null && !existing.isEmpty()) {
            return existing.iterator().next();
        }

        Collection<ComponentDefinition<T>> candidates = findBeanCandidates(beanType);

        int size = candidates.size();
        if (size > 0) {
            if (size == 1) {
                ComponentDefinition<T> definition = candidates.iterator().next();
                synchronized (singletonObjects) {
                    T createdBean = doCreateBean(resolutionContext, definition, beanType);
                    registerSingletonBean(beanType, createdBean);
                    return createdBean;
                }
            } else {
                Collection<ComponentDefinition<T>> exactMatches = filterExactMatch(beanType, candidates);
                if(exactMatches.size() == 1) {
                    ComponentDefinition<T> definition = candidates.iterator().next();
                    synchronized (singletonObjects) {
                        T createdBean = doCreateBean(resolutionContext, definition, beanType);
                        registerSingletonBean(beanType, createdBean);
                        return createdBean;
                    }
                }
                else {
                    throw new NonUniqueBeanException(beanType, candidates);
                }
            }
        }
        throw new NoSuchBeanException("No bean of type [" + beanType.getName() + "] exists");
    }



    protected Iterable<ComponentDefinitionClass> resolveComponentDefinitionClasses() {
        return ServiceLoader.load(ComponentDefinitionClass.class, classLoader);
    }

    private <T> T doCreateBean(ComponentResolutionContext resolutionContext, ComponentDefinition<T> componentDefinition, Class<T> beanType) {
        T bean;
        if(resolutionContext == null) {
            resolutionContext = new DefaultComponentResolutionContext(this, componentDefinition);
        }

        if (componentDefinition instanceof ComponentFactory) {
            ComponentFactory<T> componentFactory = (ComponentFactory<T>) componentDefinition;
            bean = componentFactory.build(resolutionContext,this, componentDefinition);
        } else {
            ConstructorInjectionPoint<T> constructor = componentDefinition.getConstructor();
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

            injectionDefinitionIfPossible(componentDefinition, bean);
        }
        if (bean != null) {
            Collection<Object> initializedObjects = getOrInitializeObjectsForType(beanType);
            if (componentDefinition.getType().equals(beanType)) {
                getOrInitializeObjectsForType(beanType).add(bean);
            }

            initializedObjects.add(bean);
        }
        Deque<Object> objectsInCreation = resolutionContext.getObjectsInCreation();
        Object head = objectsInCreation.peek();
        if(head != null && head == bean) {
            objectsInCreation.pop();
        }
        return bean;
    }

    private <T> Collection<Object> getOrInitializeObjectsForType(Class<T> beanType) {
        Collection<Object> initializedObjects = initializedObjectsByType.getIfPresent(beanType);
        if (initializedObjects == null) {
            initializedObjects = new ConcurrentLinkedQueue<>();
            initializedObjectsByType.put(beanType, initializedObjects);
        }
        return initializedObjects;
    }

    private <T> Collection<ComponentDefinition<T>> filterExactMatch(final Class<T> beanType, Collection<ComponentDefinition<T>> candidates) {
        Stream<ComponentDefinition<T>> filteredResults = candidates
                                                            .stream()
                                                            .filter((ComponentDefinition<T> candidate) ->
                                                                    candidate.getType().equals(beanType)
                                                            );
        return filteredResults.collect(Collectors.toList());
    }

    private <T> void registerSingletonBean(Class<T> beanType, T createdBean) {
        // for only one candidate create link to bean type as singleton
        singletonObjects.put(beanType, createdBean);
        singletonObjects.put(createdBean.getClass(), createdBean);
    }

    private void consumeAllComponentDefinitionClasses() {
        while (componentDefinitionClassIterator.hasNext()) {
            ComponentDefinitionClass componentDefinitionClass = componentDefinitionClassIterator.next();
            componentDefinitionsClasses.put(componentDefinitionClass.getComponentType(), componentDefinitionClass);
        }
    }

    private <T> Collection<ComponentDefinition<T>> findBeanCandidates(Class<T> beanType) {
        Collection<ComponentDefinition<T>> candidates = new HashSet<>();
        // first traverse component definition classes and load candidates
        for (Map.Entry<Class, ComponentDefinitionClass> componentDefinitionClassEntry : componentDefinitionsClasses.entrySet()) {
            Class componentType = componentDefinitionClassEntry.getKey();
            if (beanType.isAssignableFrom(componentType)) {
                // load it
                ComponentDefinitionClass componentDefinitionClass = componentDefinitionClassEntry.getValue();
                ComponentDefinition<T> componentDefinition = componentDefinitionClass.load();
                if(componentDefinition != null) {
                    componentDefinitions.put(componentType, componentDefinition);
                    candidates.add(componentDefinition);
                }
            }
        }
        for (Map.Entry<Class, ComponentDefinition> componentDefinitionEntry : componentDefinitions.entrySet()) {
            ComponentDefinition componentDefinition = componentDefinitionEntry.getValue();
            if (!candidates.contains(componentDefinition) && beanType.isAssignableFrom(componentDefinition.getType())) {
                candidates.add(componentDefinition);
            }
        }
        for (ComponentDefinition<T> candidate : candidates) {
            componentDefinitionsClasses.remove(candidate.getType());
        }
        return candidates;
    }

    private <T> void injectionDefinitionIfPossible(ComponentDefinition definition, T instance) {
        if(definition instanceof InjectableComponentDefinition) {
            ((InjectableComponentDefinition)definition).inject(this, instance);
        }
    }

    private <T> Collection<T> getBeansOfTypeInternal(ComponentResolutionContext resolutionContext, Class<T> beanType) {
        Collection<T> existing = (Collection<T>) initializedObjectsByType.getIfPresent(beanType);
        if (existing != null) {
            return Collections.unmodifiableCollection(existing);
        }

        List<T> beansOfTypeList = new ArrayList<>();

        consumeAllComponentDefinitionClasses();
        Collection<ComponentDefinition<T>> candidates = findBeanCandidates(beanType);

        if (!candidates.isEmpty()) {
            for (ComponentDefinition<T> candidate : candidates) {
                synchronized (singletonObjects) {
                    T createdBean = doCreateBean(resolutionContext, candidate, beanType);
                    registerSingletonBean(beanType, createdBean);
                    beansOfTypeList.add(createdBean);
                }
            }
            return Collections.unmodifiableList(beansOfTypeList);
        } else {
            return Collections.emptyList();
        }
    }

}
