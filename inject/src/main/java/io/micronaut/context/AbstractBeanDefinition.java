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
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.context.exceptions.*;
import io.micronaut.core.annotation.*;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.*;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Scope;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * <p>Default implementation of the {@link BeanDefinition} interface. This class is generally not used directly in user
 * code.
 * Instead a build time tool does analysis of source code and dynamically produces subclasses of this class containing
 * information about the available injection points for a given class.</p>
 * <p>
 * <p>For technical reasons the class has to be marked as public, but is regarded as internal and should be used by
 * compiler tools and plugins (such as AST transformation frameworks)</p>
 * <p>
 * <p>The {@link io.micronaut.inject.writer.BeanDefinitionWriter} class can be used to produce bean definitions at
 * compile or runtime</p>
 *
 * @param <T> The Bean definition type
 * @author Graeme Rocher
 * @see io.micronaut.inject.writer.BeanDefinitionWriter
 * @since 1.0
 */
@Internal
public class AbstractBeanDefinition<T> extends AbstractBeanContextConditional implements BeanDefinition<T>, EnvironmentConfigurable {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractBeanDefinition.class);
    private static final String NAMED_ATTRIBUTE = Named.class.getName();

    @SuppressWarnings("WeakerAccess")
    protected final List<MethodInjectionPoint<T, ?>> methodInjectionPoints = new ArrayList<>(3);
    @SuppressWarnings("WeakerAccess")
    protected final List<FieldInjectionPoint<T, ?>> fieldInjectionPoints = new ArrayList<>(3);
    @SuppressWarnings("WeakerAccess")
    protected List<MethodInjectionPoint<T, ?>> postConstructMethods;
    @SuppressWarnings("WeakerAccess")
    protected List<MethodInjectionPoint<T, ?>> preDestroyMethods;
    @SuppressWarnings("WeakerAccess")
    protected Map<MethodKey, ExecutableMethod<T, ?>> executableMethodMap;

    private final Class<T> type;
    private final boolean isAbstract;
    private final boolean isConfigurationProperties;
    private final boolean singleton;
    private final Class<?> declaringType;
    private final ConstructorInjectionPoint<T> constructor;
    private final Collection<Class<?>> requiredComponents = new HashSet<>(3);
    private AnnotationMetadata beanAnnotationMetadata;
    private Environment environment;
    private Set<Class<?>> exposedTypes;

    /**
     * Constructs a bean definition that is produced from a method call on another type (factory bean).
     *
     * @param producedType       The produced type
     * @param declaringType      The declaring type of the method
     * @param fieldName         The method name
     * @param fieldMetadata     The metadata for the method
     * @since 3.0
     */
    @SuppressWarnings({"WeakerAccess"})
    @Internal
    @UsedByGeneratedCode
    protected AbstractBeanDefinition(Class<T> producedType,
                                     Class<?> declaringType,
                                     String fieldName,
                                     AnnotationMetadata fieldMetadata,
                                     boolean isFinal) {
        this.type = producedType;
        this.isAbstract = false; // factory beans are never abstract
        this.declaringType = declaringType;

        this.constructor = new DefaultFieldConstructorInjectionPoint<>(
                this,
                declaringType,
                producedType,
                fieldName,
                fieldMetadata
        );
        this.isConfigurationProperties = hasStereotype(ConfigurationReader.class) || isIterable();
        this.singleton = isFinal;
    }

    /**
     * Constructs a bean definition that is produced from a method call on another type (factory bean).
     *
     * @param producedType       The produced type
     * @param declaringType      The declaring type of the method
     * @param methodName         The method name
     * @param methodMetadata     The metadata for the method
     * @param requiresReflection Whether reflection is required to invoke the method
     * @param arguments          The method arguments
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    @Internal
    @UsedByGeneratedCode
    protected AbstractBeanDefinition(Class<T> producedType,
                                     Class<?> declaringType,
                                     String methodName,
                                     AnnotationMetadata methodMetadata,
                                     boolean requiresReflection,
                                     Argument<?>... arguments) {
        this.type = producedType;
        this.isAbstract = false; // factory beans are never abstract
        this.declaringType = declaringType;

        if (requiresReflection) {
            this.constructor = new ReflectionMethodConstructorInjectionPoint(
                    this,
                    declaringType,
                    methodName,
                    arguments,
                    methodMetadata
            );
        } else {
            this.constructor = new DefaultMethodConstructorInjectionPoint(
                    this,
                    declaringType,
                    methodName,
                    arguments,
                    methodMetadata
            );
        }
        this.isConfigurationProperties = hasStereotype(ConfigurationReader.class) || isIterable();
        this.addRequiredComponents(arguments);
        this.singleton = getAnnotationMetadata().hasDeclaredStereotype(Singleton.class);
    }

    /**
     * Constructs a bean for the given type.
     *
     * @param type                          The type
     * @param constructorAnnotationMetadata The annotation metadata for the constructor
     * @param requiresReflection            Whether reflection is required
     * @param arguments                     The constructor arguments used to build the bean
     */
    @Internal
    @UsedByGeneratedCode
    protected AbstractBeanDefinition(Class<T> type,
                                     AnnotationMetadata constructorAnnotationMetadata,
                                     boolean requiresReflection,
                                     Argument... arguments) {

        this.type = type;
        this.isAbstract = Modifier.isAbstract(this.type.getModifiers());
        this.declaringType = type;
        if (requiresReflection) {
            this.constructor = new ReflectionConstructorInjectionPoint<>(
                    this,
                    type,
                    constructorAnnotationMetadata,
                    arguments);
        } else {
            this.constructor = new DefaultConstructorInjectionPoint<>(
                    this,
                    type,
                    constructorAnnotationMetadata,
                    arguments
            );
        }
        this.isConfigurationProperties = hasStereotype(ConfigurationReader.class) || isIterable();
        this.addRequiredComponents(arguments);
        this.singleton = getAnnotationMetadata().hasDeclaredStereotype(Singleton.class);
    }

    @Override
    public final boolean hasPropertyExpressions() {
        return getAnnotationMetadata().hasPropertyExpressions();
    }

    @Override
    public @NonNull List<Argument<?>> getTypeArguments(String type) {
        if (type == null) {
            return Collections.emptyList();
        }

        Map<String, Argument<?>[]> typeArguments = getTypeArgumentsMap();
        Argument<?>[] arguments = typeArguments.get(type);
        if (arguments != null) {
            return Arrays.asList(arguments);
        }
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public AnnotationMetadata getAnnotationMetadata() {
        if (this.beanAnnotationMetadata == null) {
            this.beanAnnotationMetadata = initializeAnnotationMetadata();
        }
        return this.beanAnnotationMetadata;
    }

    @Override
    public boolean isAbstract() {
        return this.isAbstract;
    }

    @Override
    public boolean isIterable() {
        return hasDeclaredStereotype(EachProperty.class) || hasDeclaredStereotype(EachBean.class);
    }

    @Override
    public boolean isPrimary() {
        return hasDeclaredStereotype(Primary.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>... argumentTypes) {
        if (executableMethodMap != null) {
            MethodKey methodKey = new MethodKey(name, argumentTypes);
            ExecutableMethod<T, R> invocableMethod = (ExecutableMethod<T, R>) executableMethodMap.get(methodKey);
            if (invocableMethod != null) {
                return Optional.of(invocableMethod);
            }
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Stream<ExecutableMethod<T, ?>> findPossibleMethods(String name) {
        if (executableMethodMap != null && executableMethodMap.keySet().stream().anyMatch(methodKey -> methodKey.name.equals(name))) {
            return executableMethodMap
                    .values()
                    .stream()
                    .filter(method -> method.getMethodName().equals(name));
        }
        return Stream.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Definition: " + declaringType.getName();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isProvided() {
        return getAnnotationMetadata().hasDeclaredStereotype(Provided.class);
    }

    @Override
    public boolean isSingleton() {
        return singleton;
    }

    @Override
    public Optional<Class<? extends Annotation>> getScope() {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(Scope.class);
    }

    @Override
    public final Class<T> getBeanType() {
        return type;
    }

    @Override
    @NonNull
    public final Set<Class<?>> getExposedTypes() {
        if (this.exposedTypes == null) {
            this.exposedTypes = BeanDefinition.super.getExposedTypes();
        }
        return this.exposedTypes;
    }

    @Override
    public final Optional<Class<?>> getDeclaringType() {
        return Optional.ofNullable(declaringType);
    }

    @Override
    public final ConstructorInjectionPoint<T> getConstructor() {
        return this.constructor;
    }

    @Override
    public Collection<Class<?>> getRequiredComponents() {
        return Collections.unmodifiableCollection(requiredComponents);
    }

    @Override
    public final Collection<MethodInjectionPoint<T, ?>> getInjectedMethods() {
        return Collections.unmodifiableCollection(methodInjectionPoints);
    }

    @Override
    public final Collection<FieldInjectionPoint<T, ?>> getInjectedFields() {
        return Collections.unmodifiableCollection(fieldInjectionPoints);
    }

    @Override
    public final Collection<MethodInjectionPoint<T, ?>> getPostConstructMethods() {
        if (postConstructMethods != null) {
            return Collections.unmodifiableCollection(postConstructMethods);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public final Collection<MethodInjectionPoint<T, ?>> getPreDestroyMethods() {
        if (preDestroyMethods != null) {
            return Collections.unmodifiableCollection(preDestroyMethods);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    @NonNull
    public String getName() {
        return getBeanType().getName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T inject(BeanContext context, T bean) {
        return (T) injectBean(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return (T) injectBean(resolutionContext, context, bean);
    }

    @Override
    public Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
        if (executableMethodMap != null) {
            return Collections.unmodifiableCollection(this.executableMethodMap.values());
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Configures the bean for the given {@link BeanContext}. If the context features an
     * {@link io.micronaut.context.env.Environment} this method configures the annotation metadata such that
     * environment aware values are returned.
     *
     * @param environment The environment
     */
    @Internal
    @Override
    public final void configure(Environment environment) {
        if (environment != null) {
            this.environment = environment;
            if (constructor instanceof EnvironmentConfigurable) {
                ((EnvironmentConfigurable) constructor).configure(environment);
            }

            for (MethodInjectionPoint<T, ?> methodInjectionPoint : methodInjectionPoints) {
                if (methodInjectionPoint instanceof EnvironmentConfigurable) {
                    ((EnvironmentConfigurable) methodInjectionPoint).configure(environment);
                }
            }

            if (executableMethodMap != null) {
                for (ExecutableMethod<T, ?> executableMethod : executableMethodMap.values()) {
                    if (executableMethod instanceof EnvironmentConfigurable) {
                        ((EnvironmentConfigurable) executableMethod).configure(environment);
                    }
                }
            }
        }
    }

    /**
     * Allows printing warning messages produced by the compiler.
     *
     * @param message The message
     */
    @Internal
    protected final void warn(String message) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(message);
        }
    }

    /**
     * Allows printing warning messages produced by the compiler.
     *
     * @param type     The type
     * @param method   The method
     * @param property The property
     */
    @SuppressWarnings("unused")
    @Internal
    protected final void warnMissingProperty(Class type, String method, String property) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Configuration property [{}] could not be set as the underlying method [{}] does not exist on builder [{}]. This usually indicates the configuration option was deprecated and has been removed by the builder implementation (potentially a third-party library).", property, method, type);
        }
    }

    /**
     * Resolves the proxied bean instance for this bean.
     *
     * @param beanContext The {@link BeanContext}
     * @return The proxied bean
     */
    @SuppressWarnings({"unchecked", "unused"})
    @Internal
    protected final Object getProxiedBean(BeanContext beanContext) {
        DefaultBeanContext defaultBeanContext = (DefaultBeanContext) beanContext;
        Optional<String> qualifier = getAnnotationMetadata().getAnnotationNameByStereotype(javax.inject.Qualifier.class);
        return defaultBeanContext.getProxyTargetBean(
                getBeanType(),
                (Qualifier<T>) qualifier.map(q -> Qualifiers.byAnnotation(getAnnotationMetadata(), q)).orElse(null)
        );
    }

    /**
     * Adds a new {@link ExecutableMethod}.
     *
     * @param executableMethod The method
     * @return The bean definition
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final AbstractBeanDefinition<T> addExecutableMethod(ExecutableMethod<T, ?> executableMethod) {
        MethodKey key = new MethodKey(executableMethod.getMethodName(), executableMethod.getArgumentTypes());
        if (executableMethodMap == null) {
            executableMethodMap = new LinkedHashMap<>(3);
        }
        executableMethodMap.put(key, executableMethod);
        return this;
    }

    /**
     * Adds an injection point for a field. Typically called by a dynamically generated subclass.
     *
     * @param declaringType      The declaring type
     * @param fieldType          The field type
     * @param field              The name of the field
     * @param annotationMetadata The annotation metadata for the field
     * @param typeArguments      The arguments
     * @param requiresReflection Whether reflection is required
     * @return this component definition
     */
    @SuppressWarnings({"unused", "unchecked"})
    @Internal
    @UsedByGeneratedCode
    protected final AbstractBeanDefinition addInjectionPoint(
            Class declaringType,
            Class fieldType,
            String field,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Argument[] typeArguments,
            boolean requiresReflection) {
        if (annotationMetadata != null && annotationMetadata.hasDeclaredAnnotation(Inject.class)) {
            requiredComponents.add(fieldType);
        }
        if (requiresReflection) {
            fieldInjectionPoints.add(new ReflectionFieldInjectionPoint(
                    this,
                    declaringType,
                    fieldType,
                    field,
                    annotationMetadata,
                    typeArguments
            ));
        } else {
            fieldInjectionPoints.add(new DefaultFieldInjectionPoint(
                    this,
                    declaringType,
                    fieldType,
                    field,
                    annotationMetadata,
                    typeArguments
            ));
        }
        return this;
    }

    /**
     * Adds an injection point for a method that cannot be resolved at runtime, but a compile time produced injection
     * point exists. This allows the framework to recover and relay better error messages to the user instead of just
     * NoSuchMethodError.
     *
     * @param declaringType      The declaring type
     * @param method             The method
     * @param arguments          The argument types
     * @param annotationMetadata The annotation metadata
     * @param requiresReflection Whether the method requires reflection to invoke
     * @return this component definition
     */
    @SuppressWarnings({"unused"})
    @Internal
    @UsedByGeneratedCode
    protected final AbstractBeanDefinition addInjectionPoint(
            Class declaringType,
            String method,
            @Nullable Argument[] arguments,
            @Nullable AnnotationMetadata annotationMetadata,
            boolean requiresReflection) {

        return addInjectionPointInternal(
                declaringType,
                method,
                arguments,
                annotationMetadata,
                requiresReflection,
                this.methodInjectionPoints
        );
    }

    /**
     * Adds a post construct method definition.
     *
     * @param declaringType      The declaring type
     * @param method             The method
     * @param arguments          The arguments
     * @param annotationMetadata The annotation metadata
     * @param requiresReflection Whether the method requires reflection
     * @return This bean definition
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final AbstractBeanDefinition addPostConstruct(Class declaringType,
                                                            String method,
                                                            @Nullable Argument[] arguments,
                                                            @Nullable AnnotationMetadata annotationMetadata,
                                                            boolean requiresReflection) {
        if (postConstructMethods == null) {
            postConstructMethods = new ArrayList<>(1);
        }
        return addInjectionPointInternal(declaringType, method, arguments, annotationMetadata, requiresReflection, this.postConstructMethods);
    }

    /**
     * Adds a pre destroy method definition.
     *
     * @param declaringType      The declaring type
     * @param method             The method
     * @param arguments          The arguments
     * @param annotationMetadata The annotation metadata
     * @param requiresReflection Whether the method requires reflection
     * @return This bean definition
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final AbstractBeanDefinition addPreDestroy(Class declaringType,
                                                         String method,
                                                         Argument[] arguments,
                                                         AnnotationMetadata annotationMetadata,
                                                         boolean requiresReflection) {
        if (preDestroyMethods == null) {
            preDestroyMethods = new ArrayList<>(1);
        }
        return addInjectionPointInternal(declaringType, method, arguments, annotationMetadata, requiresReflection, this.preDestroyMethods);
    }

    /**
     * The default implementation which provides no injection. To be overridden by compile time tooling.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @return The injected bean
     */
    @Internal
    @SuppressWarnings({"WeakerAccess", "unused"})
    @UsedByGeneratedCode
    protected Object injectBean(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        return bean;
    }

    /**
     * Inject another bean, for example one created via factory.
     *
     * @param resolutionContext The reslution context
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    @Internal
    @SuppressWarnings({"unused"})
    @UsedByGeneratedCode
    protected Object injectAnother(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        if (bean == null) {
            throw new BeanInstantiationException(resolutionContext, "Bean factory returned null");
        }
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        return defaultContext.inject(resolutionContext, this, bean);
    }

    /**
     * Default postConstruct hook that only invokes methods that require reflection. Generated subclasses should
     * override to call methods that don't require reflection.
     *
     * @param resolutionContext The resolution hook
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    @SuppressWarnings({"unused", "unchecked"})
    @Internal
    @UsedByGeneratedCode
    protected Object postConstruct(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        boolean addInCreationHandling = isSingleton() && !CollectionUtils.isNotEmpty(postConstructMethods);
        DefaultBeanContext.BeanKey key = null;
        if (addInCreationHandling) {
            // ensure registration as an inflight bean if a post construct is present
            // this is to ensure that if the post construct method does anything funky to
            // cause recreation of this bean then we don't have a circular problem
            key = new DefaultBeanContext.BeanKey(this, resolutionContext.getCurrentQualifier());
            resolutionContext.addInFlightBean(key, bean);
        }

        final Set<Map.Entry<Class, List<BeanInitializedEventListener>>> beanInitializedEventListeners
                = ((DefaultBeanContext) context).beanInitializedEventListeners;
        if (CollectionUtils.isNotEmpty(beanInitializedEventListeners)) {
            for (Map.Entry<Class, List<BeanInitializedEventListener>> entry : beanInitializedEventListeners) {
                if (entry.getKey().isAssignableFrom(getBeanType())) {
                    for (BeanInitializedEventListener listener : entry.getValue()) {
                        bean = listener.onInitialized(new BeanInitializingEvent(context, this, bean));
                        if (bean == null) {
                            throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onInitialized event");
                        }
                    }
                }
            }
        }

        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        for (int i = 0; i < methodInjectionPoints.size(); i++) {
            MethodInjectionPoint methodInjectionPoint = methodInjectionPoints.get(i);
            if (methodInjectionPoint.isPostConstructMethod() && methodInjectionPoint.requiresReflection()) {
                injectBeanMethod(resolutionContext, defaultContext, i, bean);
            }
        }
        if (bean instanceof LifeCycle) {
            bean = ((LifeCycle) bean).start();
        }
        try {
            return bean;
        } finally {
            if (addInCreationHandling) {
                // ensure registration as an inflight bean if a post construct is present
                // this is to ensure that if the post construct method does anything funky to
                // cause recreation of this bean then we don't have a circular problem
                resolutionContext.removeInFlightBean(key);
            }
        }
    }

    /**
     * Default preDestroy hook that only invokes methods that require reflection. Generated subclasses should override
     * to call methods that don't require reflection.
     *
     * @param resolutionContext The resolution hook
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    @UsedByGeneratedCode
    protected Object preDestroy(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        for (int i = 0; i < methodInjectionPoints.size(); i++) {
            MethodInjectionPoint methodInjectionPoint = methodInjectionPoints.get(i);
            if (methodInjectionPoint.isPreDestroyMethod() && methodInjectionPoint.requiresReflection()) {
                injectBeanMethod(resolutionContext, defaultContext, i, bean);
            }
        }

        if (bean instanceof LifeCycle) {
            bean = ((LifeCycle) bean).stop();
        }
        return bean;
    }

    /**
     * Inject a bean method that requires reflection.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param bean              The bean
     */
    @Internal
    @SuppressWarnings("WeakerAccess")
    protected void injectBeanMethod(BeanResolutionContext resolutionContext, DefaultBeanContext context, int methodIndex, Object bean) {
        MethodInjectionPoint methodInjectionPoint = methodInjectionPoints.get(methodIndex);
        Argument[] methodArgumentTypes = methodInjectionPoint.getArguments();
        Object[] methodArgs = new Object[methodArgumentTypes.length];
        for (int i = 0; i < methodArgumentTypes.length; i++) {
            methodArgs[i] = getBeanForMethodArgument(resolutionContext, context, methodIndex, i);
        }
        try {
            methodInjectionPoint.invoke(bean, methodArgs);
        } catch (Throwable e) {
            throw new BeanInstantiationException(this, e);
        }
    }

    /**
     * Injects the value of a field of a bean that requires reflection.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param index             The index of the field
     * @param bean              The bean being injected
     */
    @SuppressWarnings("unused")
    @Internal
    protected final void injectBeanField(BeanResolutionContext resolutionContext, DefaultBeanContext context, int index, Object bean) {
        FieldInjectionPoint fieldInjectionPoint = fieldInjectionPoints.get(index);
        boolean isInject = fieldInjectionPoint.getAnnotationMetadata().hasDeclaredAnnotation(Inject.class);
        try {
            Object value;
            if (isInject) {
                instrumentAnnotationMetadata(context, fieldInjectionPoint);
                value = getBeanForField(resolutionContext, context, fieldInjectionPoint);
            } else {
                value = getValueForField(resolutionContext, context, index);
            }
            if (value != null) {
                //noinspection unchecked
                fieldInjectionPoint.set(bean, value);
            }
        } catch (Throwable e) {
            if (e instanceof BeanContextException) {
                throw (BeanContextException) e;
            } else {
                throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, "Error setting field value: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Obtains a value for the given method argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @return The value
     */
    @SuppressWarnings({"unused", "unchecked"})
    @Internal
    protected final Object getValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        if (context instanceof ApplicationContext) {
            // can't use orElseThrow here due to compiler bug
            try {
                String valueAnnStr = argument.getAnnotationMetadata().stringValue(Value.class).orElse(null);

                Argument<?> argumentType;
                boolean isCollection = false;
                if (Collection.class.isAssignableFrom(argument.getType())) {
                    argumentType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                    isCollection = true;
                } else {
                    argumentType = argument;
                }

                if (isInnerConfiguration(argumentType, context)) {
                    Qualifier qualifier = resolveQualifier(resolutionContext, argument, true);
                    if (isCollection) {
                        Collection beans = ((DefaultBeanContext) context).getBeansOfType(resolutionContext, argumentType, qualifier);
                        return coerceCollectionToCorrectType(argument.getType(), beans);
                    } else {
                        return ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                    }
                } else {
                    String valString = resolvePropertyValueName(resolutionContext, injectionPoint.getAnnotationMetadata(), argument, valueAnnStr);

                    ApplicationContext applicationContext = (ApplicationContext) context;
                    ArgumentConversionContext conversionContext = ConversionContext.of(argument);
                    Optional value = resolveValue(applicationContext, conversionContext, valueAnnStr != null, valString);
                    if (argumentType.isOptional()) {
                        return resolveOptionalObject(value);
                    } else {
                        if (value.isPresent()) {
                            return value.get();
                        } else {
                            if (argument.isDeclaredNullable()) {
                                return null;
                            }
                            throw new DependencyInjectionException(resolutionContext, injectionPoint, conversionContext, valString);
                        }
                    }
                }
            } finally {
                path.pop();
            }
        } else {
            path.pop();
            throw new DependencyInjectionException(resolutionContext, argument, "BeanContext must support property resolution");
        }
    }


    /**
     * Obtains a value for the given method argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @return The value
     */
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        if (context instanceof ApplicationContext) {
            MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
            Argument argument = injectionPoint.getArguments()[argIndex];
            String valueAnnStr = argument.getAnnotationMetadata().stringValue(Value.class).orElse(null);
            String valString = resolvePropertyValueName(resolutionContext, injectionPoint.getAnnotationMetadata(), argument, valueAnnStr);
            ApplicationContext applicationContext = (ApplicationContext) context;
            Class type = argument.getType();
            boolean isConfigProps = type.isAnnotationPresent(ConfigurationProperties.class);
            boolean result = isConfigProps || Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type) ? applicationContext.containsProperties(valString) : applicationContext.containsProperty(valString);
            if (!result && isConfigurationProperties()) {
                String cliOption = resolveCliOption(argument.getName());
                if (cliOption != null) {
                    result = applicationContext.containsProperty(cliOption);
                }
            }
            if (result && injectionPoint instanceof MissingMethodInjectionPoint) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Bean definition for type [{}] is compiled against an older version and value [{}] can no longer be set for missing method: {}",
                            getBeanType(),
                            valString,
                            injectionPoint.getName());
                }
                result = false;
            }
            return result;
        }
        return false;
    }

    /**
     * Obtains a bean definition for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @return The resolved bean
     */
    @Internal
    @SuppressWarnings("WeakerAccess")
    @UsedByGeneratedCode
    protected final Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = resolveArgument(context, argIndex, injectionPoint.getArguments());
        return getBeanForMethodArgument(resolutionContext, context, injectionPoint, argument);
    }

    /**
     * Obtains all bean definitions for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param injectionPoint    The method injection point
     * @param argument          The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Collection getBeansOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, injectionPoint, argument, (beanType, qualifier) -> {
                    boolean hasNoGenerics = !argument.getType().isArray() && argument.getTypeVariables().isEmpty();
                    if (hasNoGenerics) {
                        return ((DefaultBeanContext) context).getBean(
                                resolutionContext,
                                beanType,
                                qualifier
                        );
                    } else {
                        return ((DefaultBeanContext) context).getBeansOfType(
                                resolutionContext,
                                beanType,
                                qualifier
                        );
                    }

                }
        );
    }

    /**
     * Obtains an optional bean for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param injectionPoint    The method injection point
     * @param argument          The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Optional findBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, injectionPoint, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).findBean(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param injectionPoint    The method injection point
     * @param argument          The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Stream streamOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, injectionPoint, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains a bean definition for a constructor at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argIndex          The argument index
     * @return The resolved bean
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex) {
        ConstructorInjectionPoint<T> constructorInjectionPoint = getConstructor();
        Argument<?> argument = getArgument(context, constructorInjectionPoint.getArguments(), argIndex);
        final Class<?> beanType = argument.getType();
        if (beanType == BeanResolutionContext.class) {
            return resolutionContext;
        } else if (argument.isArray()) {
            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
            return beansOfType.toArray((Object[]) Array.newInstance(beanType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(beanType)) {
            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
            return coerceCollectionToCorrectType(beanType, beansOfType);
        } else if (Stream.class.isAssignableFrom(beanType)) {
            return streamOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
        } else if (argument.isOptional()) {
            return findBeanForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
        } else {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            BeanResolutionContext.Segment current = path.peek();
            boolean isNullable = argument.isDeclaredNullable();
            if (isNullable && current != null && current.getArgument().equals(argument)) {
                return null;
            } else {
                path.pushConstructorResolve(this, argument);
                try {
                    Object bean;
                    Qualifier qualifier = resolveQualifier(resolutionContext, argument, isInnerConfiguration(argument, context));
                    if (Qualifier.class.isAssignableFrom(beanType)) {
                        bean = qualifier;
                    } else {
                        //noinspection unchecked
                        bean = ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
                    }
                    path.pop();
                    return bean;
                } catch (DisabledBeanException e) {
                    if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                        AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", argument.getTypeName(), e.getMessage());
                    }
                    if (isIterable() && getAnnotationMetadata().hasDeclaredAnnotation(EachBean.class)) {
                        throw new DisabledBeanException("Bean [" + getBeanType().getSimpleName() + "] disabled by parent: " + e.getMessage());
                    } else {
                        if (isNullable) {
                            path.pop();
                            return null;
                        }
                        throw new DependencyInjectionException(resolutionContext, argument, e);
                    }
                } catch (NoSuchBeanException e) {
                    if (isNullable) {
                        path.pop();
                        return null;
                    }
                    throw new DependencyInjectionException(resolutionContext, argument, e);
                }
            }
        }
    }

    private Argument<?> getArgument(BeanContext context, Argument[] arguments, int argIndex) {
        Argument<?> argument = resolveArgument(context, argIndex, arguments);
        return argument;
    }

    /**
     * Obtains a value for a bean definition for a constructor at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argIndex          The argument index
     * @return The resolved bean
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final Object getValueForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex) {
        ConstructorInjectionPoint<T> constructorInjectionPoint = getConstructor();
        BeanResolutionContext.Path path = resolutionContext.getPath();
        Argument<?> argument = constructorInjectionPoint.getArguments()[argIndex];
        path.pushConstructorResolve(this, argument);
        try {
            Object result;
            if (context instanceof ApplicationContext) {
                ApplicationContext propertyResolver = (ApplicationContext) context;
                AnnotationMetadata argMetadata = argument.getAnnotationMetadata();
                Optional<String> valAnn = argMetadata.stringValue(Value.class);
                String prop = resolvePropertyValueName(resolutionContext, argMetadata, argument, valAnn.orElse(null));
                ArgumentConversionContext<?> conversionContext = ConversionContext.of(argument);
                Optional<?> value = resolveValue(propertyResolver, conversionContext, valAnn.isPresent(), prop);
                if (argument.getType() == Optional.class) {
                    return resolveOptionalObject(value);
                } else {
                    // can't use orElseThrow here due to compiler bug
                    if (value.isPresent()) {
                        result = value.get();
                    } else {
                        if (argument.isDeclaredNullable()) {
                            result = null;
                        } else {
                            result = argMetadata.getValue(Bindable.class, "defaultValue", argument)
                                    .orElseThrow(() -> new DependencyInjectionException(resolutionContext, conversionContext, prop));
                        }
                    }
                }
            } else {
                throw new DependencyInjectionException(resolutionContext, argument, "BeanContext must support property resolution");
            }

            if (this instanceof ValidatedBeanDefinition) {
                ((ValidatedBeanDefinition) this).validateBeanArgument(
                        resolutionContext,
                        constructorInjectionPoint,
                        argument,
                        argIndex,
                        result
                );
            }

            return result;
        } catch (NoSuchBeanException | BeanInstantiationException e) {
            throw new DependencyInjectionException(resolutionContext, argument, e);
        } finally {
            path.pop();
        }
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index.
     * <p>
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param constructorInjectionPoint The constructor injection point
     * @param argument                  The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Collection getBeansOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, @SuppressWarnings("unused") ConstructorInjectionPoint<T> constructorInjectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, argument, (beanType, qualifier) -> {
                    boolean hasNoGenerics = !argument.getType().isArray() && argument.getTypeVariables().isEmpty();
                    if (hasNoGenerics) {
                        return ((DefaultBeanContext) context).getBean(resolutionContext, beanType, qualifier);
                    } else {
                        return ((DefaultBeanContext) context).getBeansOfType(resolutionContext, beanType, qualifier);
                    }
                }
        );
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index.
     * <p>
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argumentIndex             The argument index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeansOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argumentIndex) {
        final ConstructorInjectionPoint<T> constructorInjectionPoint = getConstructor();
        final Argument<?> argument = getArgument(context, constructorInjectionPoint.getArguments(), argumentIndex);
        final Class<?> argumentType = argument.getType();
        Argument<?> genericType = resolveGenericType(argument, () ->
                new DependencyInjectionException(resolutionContext, argument, "Type " + argumentType + " has no generic argument")
        );
        final Qualifier qualifier = resolveQualifier(resolutionContext, argument);
        final BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this, argument);
        return doGetBeansOfType(resolutionContext, (DefaultBeanContext) context, argumentType, genericType, qualifier, path);
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index.
     * <p>
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param methodIndex               The method index
     * @param argumentIndex             The argument index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeansOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argumentIndex) {
        final MethodInjectionPoint<?, ?> methodInjectionPoint = methodInjectionPoints.get(methodIndex);
        final Argument<?> argument = getArgument(context, methodInjectionPoint.getArguments(), argumentIndex);
        final Class<?> argumentType = argument.getType();
        Argument<?> genericType = resolveGenericType(argument, () ->
                new DependencyInjectionException(resolutionContext, methodInjectionPoint, argument, "Type " + argumentType + " has no generic argument")
        );
        final Qualifier qualifier = resolveQualifier(resolutionContext, argument);
        final BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, methodInjectionPoint, argument);
        return doGetBeansOfType(resolutionContext, (DefaultBeanContext) context, argumentType, genericType, qualifier, path);
    }

    /**
     * Obtains all bean definitions for the field at the given index.
     * <p>
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param fieldIndex                The field index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeansOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        final FieldInjectionPoint<?, ?> fieldInjectionPoint = fieldInjectionPoints.get(fieldIndex);
        final Argument<?> argument = fieldInjectionPoint.asArgument();
        final Class<?> argumentType = argument.getType();
        Argument<?> genericType = resolveGenericType(argument, () ->
                new DependencyInjectionException(resolutionContext, fieldInjectionPoint, "Type " + argumentType + " has no generic argument"));
        final Qualifier qualifier = resolveQualifier(resolutionContext, argument);
        final BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, fieldInjectionPoint);
        return doGetBeansOfType(resolutionContext, (DefaultBeanContext) context, argumentType, genericType, qualifier, path);
    }

    private Object doGetBeansOfType(BeanResolutionContext resolutionContext, DefaultBeanContext context, Class<?> argumentType, Argument<?> genericType, Qualifier qualifier, BeanResolutionContext.Path path) {
        try {
            final Collection<?> beansOfType = context.getBeansOfType(resolutionContext, genericType, qualifier);
            if (argumentType.isArray()) {
                return beansOfType.toArray((Object[]) Array.newInstance(genericType.getType(), beansOfType.size()));
            } else {
                return coerceCollectionToCorrectType(argumentType, beansOfType);
            }
        } finally {
            path.pop();
        }
    }

    private Argument<?> resolveGenericType(Argument<?> argument, Supplier<DependencyInjectionException> exceptionSupplier) {
        Argument<?> genericType;
        if (argument.isArray()) {
            genericType = Argument.of(argument.getType().getComponentType());
        } else {

            genericType = argument.getFirstTypeVariable()
                    .orElseThrow(exceptionSupplier);
        }
        return genericType;
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argumentIndex             The argument index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanRegistrationsForConstructorArgument(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int argumentIndex) {
        Argument<?> argument = getArgument(context, getConstructor().getArguments(), argumentIndex);
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this, argument);
        return doResolveBeanRegistrations(resolutionContext, (DefaultBeanContext) context, argument, path);
    }

    /**
     * Obtains a bean registration for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argIndex                  The arg index
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForConstructorArgument(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int argIndex) {
        Argument<?> argument = getArgument(context, getConstructor().getArguments(), argIndex);
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this, argument);
        return resolveBeanRegistrationWithGenericsFromArgument(resolutionContext, argument, path, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeanRegistration(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for a field injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param fieldIndex                The field index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanRegistrationsForField(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int fieldIndex) {
        FieldInjectionPoint<?, ?> field = fieldInjectionPoints.get(fieldIndex);
        instrumentAnnotationMetadata(context, field);
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, field);
        return doResolveBeanRegistrations(resolutionContext, (DefaultBeanContext) context, field.asArgument(), path);
    }

    /**
     * Obtains a bean registration for a field injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param fieldIndex                The field index
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForField(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int fieldIndex) {
        FieldInjectionPoint<?, ?> field = fieldInjectionPoints.get(fieldIndex);
        instrumentAnnotationMetadata(context, field);
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, field);
        return resolveBeanRegistrationWithGenericsFromArgument(resolutionContext, field.asArgument(), path, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeanRegistration(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param methodIndex               The method index
     * @param argIndex                  The arg index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanRegistrationsForMethodArgument(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int methodIndex,
            int argIndex) {
        MethodInjectionPoint<?, ?> methodInjectionPoint = methodInjectionPoints.get(methodIndex);
        Argument<?> argument = resolveArgument(context, argIndex, methodInjectionPoint.getArguments());
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, methodInjectionPoint, argument);
        return doResolveBeanRegistrations(resolutionContext, (DefaultBeanContext) context, argument, path);
    }

    /**
     * Obtains a bean registration for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param methodIndex               The method index
     * @param argIndex                  The arg index
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForMethodArgument(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int methodIndex,
            int argIndex) {
        MethodInjectionPoint<?, ?> methodInjectionPoint = methodInjectionPoints.get(methodIndex);
        Argument<?> argument = resolveArgument(context, argIndex, methodInjectionPoint.getArguments());
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, methodInjectionPoint, argument);
        return resolveBeanRegistrationWithGenericsFromArgument(resolutionContext, argument, path, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeanRegistration(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param constructorInjectionPoint The constructor injection point
     * @param argument                  The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Stream streamOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, @SuppressWarnings("unused") ConstructorInjectionPoint<T> constructorInjectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param constructorInjectionPoint The constructor injection point
     * @param argument                  The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Optional findBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, @SuppressWarnings("unused") ConstructorInjectionPoint<T> constructorInjectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).findBean(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The field index
     * @return The resolved bean
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
        instrumentAnnotationMetadata(context, injectionPoint);
        return getBeanForField(resolutionContext, context, injectionPoint);
    }

    /**
     * Obtains a value for the given field from the bean context
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The index of the field
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, injectionPoint);
        try {
            if (context instanceof PropertyResolver) {
                final AnnotationMetadata annotationMetadata = injectionPoint.getAnnotationMetadata();
                String valueAnnVal = annotationMetadata.stringValue(Value.class).orElse(null);
                Argument<?> fieldArgument = injectionPoint.asArgument();

                Argument<?> argumentType;
                boolean isCollection = false;
                if (Collection.class.isAssignableFrom(injectionPoint.getType())) {
                    argumentType = fieldArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                    isCollection = true;
                } else {
                    argumentType = fieldArgument;
                }
                if (isInnerConfiguration(argumentType, context)) {
                    Qualifier qualifier = resolveQualifier(resolutionContext, fieldArgument, true);
                    if (isCollection) {
                        Collection beans = ((DefaultBeanContext) context).getBeansOfType(resolutionContext, argumentType, qualifier);
                        return coerceCollectionToCorrectType(fieldArgument.getType(), beans);
                    } else {
                        return ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                    }
                } else {
                    String valString = resolvePropertyValueName(resolutionContext, injectionPoint, valueAnnVal, annotationMetadata);
                    ArgumentConversionContext conversionContext = ConversionContext.of(fieldArgument);
                    Optional value = resolveValue((ApplicationContext) context, conversionContext, valueAnnVal != null, valString);
                    if (argumentType.isOptional()) {
                        return resolveOptionalObject(value);
                    } else {
                        if (value.isPresent()) {
                            return value.get();
                        } else {
                            if (fieldArgument.isDeclaredNullable()) {
                                return null;
                            }
                            throw new DependencyInjectionException(resolutionContext, injectionPoint, "Error resolving field value [" + valString + "]. Property doesn't exist or cannot be converted");
                        }
                    }
                }
            } else {
                throw new DependencyInjectionException(resolutionContext, injectionPoint, "@Value requires a BeanContext that implements PropertyResolver");
            }
        } finally {
            path.pop();
        }
    }

    /**
     * Resolve a value for the given field of the given type and path. Only
     * used by applications compiled with versions of Micronaut prior to 1.2.0.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param propertyType      The required property type
     * @param propertyPath      The property path
     * @param <T1>              The generic type
     * @return An optional value
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final <T1> Optional<T1> getValueForPath(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            Argument<T1> propertyType,
            String... propertyPath) {
        if (context instanceof PropertyResolver) {
            PropertyResolver propertyResolver = (PropertyResolver) context;
            String pathString = propertyPath.length > 1 ? String.join(".", propertyPath) : propertyPath[0];
            String valString = resolvePropertyPath(resolutionContext, pathString);

            return propertyResolver.getProperty(valString, ConversionContext.of(propertyType));
        }
        return Optional.empty();
    }

    /**
     * Resolve a value for the given field of the given type and path.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param propertyType      The required property type
     * @param propertyPath      The property path
     * @param <T1>              The generic type
     * @return An optional value
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final <T1> Optional<T1> getValueForPath(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            Argument<T1> propertyType,
            String propertyPath) {
        if (context instanceof PropertyResolver) {
            PropertyResolver propertyResolver = (PropertyResolver) context;
            String valString = substituteWildCards(resolutionContext, propertyPath);

            return propertyResolver.getProperty(valString, ConversionContext.of(propertyType));
        }
        return Optional.empty();
    }

    /**
     * Obtains a value for the given field argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param fieldIndex        The field index
     * @return True if it does
     */
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        if (context instanceof ApplicationContext) {
            FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
            final AnnotationMetadata annotationMetadata = injectionPoint.getAnnotationMetadata();
            String valueAnnVal = annotationMetadata.stringValue(Value.class).orElse(null);
            String valString = resolvePropertyValueName(resolutionContext, injectionPoint, valueAnnVal, annotationMetadata);
            ApplicationContext applicationContext = (ApplicationContext) context;
            Class fieldType = injectionPoint.getType();
            boolean isConfigProps = fieldType.isAnnotationPresent(ConfigurationProperties.class);
            boolean result = isConfigProps || Map.class.isAssignableFrom(fieldType) || Collection.class.isAssignableFrom(fieldType) ? applicationContext.containsProperties(valString) : applicationContext.containsProperty(valString);
            if (!result && isConfigurationProperties()) {
                String cliOption = resolveCliOption(injectionPoint.getName());
                if (cliOption != null) {
                    return applicationContext.containsProperty(cliOption);
                }
            }
            return result;
        }
        return false;
    }

    /**
     * If this bean is a {@link ConfigurationProperties} bean return whether any properties for it are configured
     * within the context.
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @return True if it does
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsProperties(BeanResolutionContext resolutionContext, BeanContext context) {
        return containsProperties(resolutionContext, context, null);
    }

    /**
     * If this bean is a {@link ConfigurationProperties} bean return whether any properties for it are configured
     * within the context.
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @param subProperty       The subproperty to check
     * @return True if it does
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsProperties(@SuppressWarnings("unused") BeanResolutionContext resolutionContext, BeanContext context, String subProperty) {
        boolean isSubProperty = StringUtils.isNotEmpty(subProperty);
        if (!isSubProperty && !requiredComponents.isEmpty()) {
            // if the bean requires dependency injection we disable this optimization
            return true;
        }
        if (isConfigurationProperties && context instanceof ApplicationContext) {
            AnnotationMetadata annotationMetadata = getAnnotationMetadata();
            ApplicationContext appCtx = (ApplicationContext) context;
            if (annotationMetadata.getValue(ConfigurationProperties.class, "cliPrefix").isPresent()) {
                return true;
            } else {
                String path = getConfigurationPropertiesPath(resolutionContext);
                return appCtx.containsProperties(path);
            }

        }
        return false;
    }

    /**
     * Resolves a bean for the given {@link FieldInjectionPoint}.
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param context           The {@link BeanContext}
     * @param injectionPoint    The {@link FieldInjectionPoint}
     * @return The resolved bean
     * @throws DependencyInjectionException If the bean cannot be resolved
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
        final Class beanClass = injectionPoint.getType();
        if (beanClass.isArray()) {
            Collection beansOfType = getBeansOfTypeForField(resolutionContext, context, injectionPoint);
            return beansOfType.toArray((Object[]) Array.newInstance(beanClass.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(beanClass)) {
            Collection beansOfType = getBeansOfTypeForField(resolutionContext, context, injectionPoint);
            if (beanClass.isInstance(beansOfType)) {
                return beansOfType;
            } else {
                //noinspection unchecked
                return CollectionUtils.convertCollection(beanClass, beansOfType).orElse(null);
            }
        } else if (Stream.class.isAssignableFrom(beanClass)) {
            return getStreamOfTypeForField(resolutionContext, context, injectionPoint);
        } else if (Optional.class.isAssignableFrom(beanClass)) {
            return findBeanForField(resolutionContext, context, injectionPoint);
        } else {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            path.pushFieldResolve(this, injectionPoint);

            final Argument argument = injectionPoint.asArgument();
            try {
                Qualifier qualifier = resolveQualifier(resolutionContext, argument);
                @SuppressWarnings("unchecked") Object bean = ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
                path.pop();
                return bean;
            } catch (DisabledBeanException e) {
                if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                    AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", argument.getTypeName(), e.getMessage());
                }
                if (isIterable() && getAnnotationMetadata().hasDeclaredAnnotation(EachBean.class)) {
                    throw new DisabledBeanException("Bean [" + getBeanType().getSimpleName() + "] disabled by parent: " + e.getMessage());
                } else {
                    if (injectionPoint.isDeclaredNullable()) {
                        path.pop();
                        return null;
                    }
                    throw new DependencyInjectionException(resolutionContext, injectionPoint, e);
                }
            } catch (NoSuchBeanException e) {
                if (injectionPoint.isDeclaredNullable()) {
                    path.pop();
                    return null;
                }
                throw new DependencyInjectionException(resolutionContext, injectionPoint, e);
            }
        }
    }

    /**
     * Obtains a an optional for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param injectionPoint    The field injection point
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Optional findBeanForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
        return resolveBeanWithGenericsForField(resolutionContext, injectionPoint, (beanType, qualifier) ->
                ((DefaultBeanContext) context).findBean(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param injectionPoint    The field injection point
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Collection getBeansOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
        return resolveBeanWithGenericsForField(resolutionContext, injectionPoint, (beanType, qualifier) -> {
                    boolean hasNoGenerics = !injectionPoint.getType().isArray() && injectionPoint.asArgument().getTypeVariables().isEmpty();
                    if (hasNoGenerics) {
                        return ((DefaultBeanContext) context).getBean(resolutionContext, beanType, qualifier);
                    } else {
                        return ((DefaultBeanContext) context).getBeansOfType(resolutionContext, beanType, qualifier);
                    }

                }
        );
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param injectionPoint    The field injection point
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Stream getStreamOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
        return resolveBeanWithGenericsForField(resolutionContext, injectionPoint, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * A method that subclasses can override to provide information on type arguments.
     *
     * @return The type arguments
     */
    @Internal
    @UsedByGeneratedCode
    protected Map<String, Argument<?>[]> getTypeArgumentsMap() {
        return Collections.emptyMap();
    }

    /**
     * Resolves the annotation metadata for this bean. Subclasses
     *
     * @return The {@link AnnotationMetadata}
     */
    protected AnnotationMetadata resolveAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    private AnnotationMetadata initializeAnnotationMetadata() {
        AnnotationMetadata annotationMetadata = resolveAnnotationMetadata();
        if (annotationMetadata != AnnotationMetadata.EMPTY_METADATA) {
            if (annotationMetadata.hasPropertyExpressions()) {
                // we make a copy of the result of annotation metadata which is normally a reference
                // to the class metadata
                return new BeanAnnotationMetadata(annotationMetadata);
            } else {
                return annotationMetadata;
            }
        } else {
            return AnnotationMetadata.EMPTY_METADATA;
        }
    }

    private AbstractBeanDefinition addInjectionPointInternal(
            Class declaringType,
            String method,
            @Nullable Argument[] arguments,
            @Nullable AnnotationMetadata annotationMetadata,
            boolean requiresReflection,
            List<MethodInjectionPoint<T, ?>> targetInjectionPoints) {
        boolean isPreDestroy = targetInjectionPoints == this.preDestroyMethods;
        boolean isPostConstruct = targetInjectionPoints == this.postConstructMethods;

        MethodInjectionPoint injectionPoint;
        if (requiresReflection) {
            injectionPoint = new ReflectionMethodInjectionPoint(
                    this,
                    declaringType,
                    method,
                    arguments,
                    annotationMetadata
            );
        } else {
            injectionPoint = new DefaultMethodInjectionPoint(
                    this,
                    declaringType,
                    method,
                    arguments,
                    annotationMetadata
            );
        }
        targetInjectionPoints.add(injectionPoint);
        if (isPostConstruct || isPreDestroy) {
            this.methodInjectionPoints.add(injectionPoint);
        }
        addRequiredComponents(arguments);
        return this;
    }

    private Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        Class argumentType = argument.getType();
        if (argumentType.isArray()) {
            Collection beansOfType = getBeansOfTypeForMethodArgument(resolutionContext, context, injectionPoint, argument);
            return beansOfType.toArray((Object[]) Array.newInstance(argumentType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(argumentType)) {
            Collection beansOfType = getBeansOfTypeForMethodArgument(resolutionContext, context, injectionPoint, argument);
            return coerceCollectionToCorrectType(argumentType, beansOfType);
        } else if (Stream.class.isAssignableFrom(argumentType)) {
            return streamOfTypeForMethodArgument(resolutionContext, context, injectionPoint, argument);
        } else if (Optional.class.isAssignableFrom(argumentType)) {
            return findBeanForMethodArgument(resolutionContext, context, injectionPoint, argument);
        } else {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            path.pushMethodArgumentResolve(this, injectionPoint, argument);
            try {
                Qualifier qualifier = resolveQualifier(resolutionContext, argument);
                @SuppressWarnings("unchecked")
                Object bean = ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
                path.pop();
                return bean;
            } catch (DisabledBeanException e) {
                if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                    AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", argumentType.getSimpleName(), e.getMessage());
                }
                if (isIterable() && getAnnotationMetadata().hasDeclaredAnnotation(EachBean.class)) {
                    throw new DisabledBeanException("Bean [" + getBeanType().getSimpleName() + "] disabled by parent: " + e.getMessage());
                } else {
                    if (argument.isDeclaredNullable()) {
                        path.pop();
                        return null;
                    }
                    throw new DependencyInjectionException(resolutionContext, argument, e);
                }
            } catch (NoSuchBeanException e) {
                if (argument.isDeclaredNullable()) {
                    path.pop();
                    return null;
                }
                throw new DependencyInjectionException(resolutionContext, argument, e);
            }
        }
    }

    private Optional resolveValue(
            ApplicationContext context,
            ArgumentConversionContext<?> argument,
            boolean hasValueAnnotation,
            String valString) {

        if (hasValueAnnotation) {

            return context.resolvePlaceholders(valString).flatMap(v ->
                    context.getConversionService().convert(v, argument)
            );
        } else {
            Optional<?> value = context.getProperty(valString, argument);
            if (!value.isPresent() && isConfigurationProperties()) {
                String cliOption = resolveCliOption(argument.getArgument().getName());
                if (cliOption != null) {
                    return context.getProperty(cliOption, argument);
                }
            }
            return value;
        }
    }

    private String resolvePropertyValueName(
            BeanResolutionContext resolutionContext,
            AnnotationMetadata annotationMetadata,
            Argument argument,
            String valueAnnStr) {
        String valString;
        if (valueAnnStr != null) {
            valString = valueAnnStr;
        } else {
            valString = annotationMetadata.stringValue(Property.class, "name")
                    .orElseGet(() ->
                            argument.getAnnotationMetadata().stringValue(Property.class, "name")
                                    .orElseThrow(() ->
                                            new DependencyInjectionException(
                                                    resolutionContext,
                                                    argument,
                                                    "Value resolution attempted but @Value annotation is missing"
                                            )
                                    )
                    );

            valString = substituteWildCards(resolutionContext, valString);
        }
        return valString;
    }

    private String resolvePropertyValueName(
            BeanResolutionContext resolutionContext,
            FieldInjectionPoint injectionPoint,
            String valueAnn,
            AnnotationMetadata annotationMetadata) {
        String valString;
        if (valueAnn != null) {
            valString = valueAnn;
        } else {
            valString = annotationMetadata.stringValue(Property.class, "name")
                    .orElseThrow(() -> new DependencyInjectionException(resolutionContext, injectionPoint, "Value resolution attempted but @Value annotation is missing"));

            valString = substituteWildCards(resolutionContext, valString);
        }
        return valString;
    }

    private String resolvePropertyPath(
            BeanResolutionContext resolutionContext,
            String path) {

        String valString = getConfigurationPropertiesPath(resolutionContext);
        return valString + "." + path;
    }

    private String getConfigurationPropertiesPath(BeanResolutionContext resolutionContext) {
        String valString = getAnnotationMetadata()
                .stringValue(ConfigurationReader.class, "prefix")
                .orElseThrow(() -> new IllegalStateException("Resolve property path called for non @ConfigurationProperties bean"));
        valString = substituteWildCards(
                resolutionContext,
                valString
        );
        return valString;
    }

    private String substituteWildCards(BeanResolutionContext resolutionContext, String valString) {
        if (valString.indexOf('*') > -1) {
            Optional<String> namedBean = resolutionContext.get(Named.class.getName(), ConversionContext.STRING);
            if (namedBean.isPresent()) {
                valString = valString.replace("*", namedBean.get());
            }
        }
        return valString;
    }

    private String resolveCliOption(String name) {
        String attr = "cliPrefix";
        AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        if (annotationMetadata.isPresent(ConfigurationProperties.class, attr)) {
            return annotationMetadata.stringValue(ConfigurationProperties.class, attr).map(val -> val + name).orElse(null);
        }
        return null;
    }

    private boolean isInnerConfiguration(Argument<?> argumentType, BeanContext beanContext) {
        final Class<?> type = argumentType.getType();
        return isConfigurationProperties &&
                type.getName().indexOf('$') > -1 &&
                !type.isEnum() &&
                !type.isPrimitive() &&
                Modifier.isPublic(type.getModifiers()) && Modifier.isStatic(type.getModifiers()) &&
                isInnerOfAnySuperclass(type) &&
                beanContext.findBeanDefinition(argumentType).map(bd -> bd.hasStereotype(ConfigurationReader.class) || bd.isIterable()).isPresent();
    }

    private boolean isInnerOfAnySuperclass(Class argumentType) {
        Class beanType = getBeanType();
        while (beanType != null) {
            if ((beanType.getName() + "$" + argumentType.getSimpleName()).equals(argumentType.getName())) {
                return true;
            }
            beanType = beanType.getSuperclass();
        }
        return false;
    }

    private <B, X extends RuntimeException> B resolveBeanWithGenericsFromMethodArgument(BeanResolutionContext resolutionContext, MethodInjectionPoint injectionPoint, Argument argument, BeanResolver<B> beanResolver) throws X {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        try {
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            Class argumentType = argument.getType();
            Argument genericType = resolveGenericType(argument, argumentType);
            @SuppressWarnings("unchecked") B bean = (B) beanResolver.resolveBean(genericType != null ? genericType : argument, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
        }
    }

    private Argument resolveGenericType(Argument argument, Class argumentType) {
        Argument genericType;
        if (argument.isArray()) {
            genericType = Argument.of(argumentType.getComponentType());
        } else {
            return argument.getFirstTypeVariable()
                           .orElse(null);
        }
        return genericType;
    }

    private <B> B resolveBeanWithGenericsFromConstructorArgument(BeanResolutionContext resolutionContext, Argument argument, BeanResolver<B> beanResolver) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this, argument);
        try {
            Class argumentType = argument.getType();
            Argument genericType = resolveGenericType(argument, argumentType);
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            @SuppressWarnings("unchecked") B bean = (B) beanResolver.resolveBean(genericType != null ? genericType : argument, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                path.pop();
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private <B> Collection<BeanRegistration<B>> resolveBeanRegistrationsWithGenericsFromArgument(
            BeanResolutionContext resolutionContext,
            Argument<?> argument,
            BeanResolutionContext.Path path,
            BiFunction<Argument<B>, Qualifier<B>, Collection<BeanRegistration<B>>> beanResolver) {
        try {
            final Supplier<DependencyInjectionException> errorSupplier = () ->
                    new DependencyInjectionException(resolutionContext, argument, "Cannot resolve bean registrations. Argument [" + argument + "] missing generic type information.");
            Argument<?> genericType = argument.getFirstTypeVariable().orElseThrow(errorSupplier);
            Argument beanType = argument.isArray() ? genericType : genericType.getFirstTypeVariable().orElseThrow(errorSupplier);
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            final Collection result = beanResolver.apply(beanType, qualifier);
            path.pop();
            return result;
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                path.pop();
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private Argument<?> resolveArgument(BeanContext context, int argIndex, Argument<?>[] arguments) {
        Argument<?> argument = arguments[argIndex];
        if (argument instanceof DefaultArgument) {
            if (argument.getAnnotationMetadata().hasPropertyExpressions()) {
                argument = new EnvironmentAwareArgument<>((DefaultArgument) argument);
                instrumentAnnotationMetadata(context, argument);
            }
        }
        return argument;
    }

    private <B> BeanRegistration<B> resolveBeanRegistrationWithGenericsFromArgument(
            BeanResolutionContext resolutionContext,
            Argument<?> argument,
            BeanResolutionContext.Path path,
            BiFunction<Argument<B>, Qualifier<B>, BeanRegistration<B>> beanResolver) {
        try {
            final Supplier<DependencyInjectionException> errorSupplier = () ->
                    new DependencyInjectionException(resolutionContext, argument, "Cannot resolve bean registration. Argument [" + argument + "] missing generic type information.");
            Argument genericType = argument.getFirstTypeVariable().orElseThrow(errorSupplier);
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            final BeanRegistration result = beanResolver.apply(genericType, qualifier);
            path.pop();
            return result;
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                path.pop();
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private Object doResolveBeanRegistrations(BeanResolutionContext resolutionContext, DefaultBeanContext context, Argument<?> argument, BeanResolutionContext.Path path) {
        final Collection<BeanRegistration<Object>> beanRegistrations = resolveBeanRegistrationsWithGenericsFromArgument(resolutionContext, argument, path,
                (beanType, qualifier) -> context.getBeanRegistrations(resolutionContext, beanType, qualifier)
        );
        if (argument.isArray()) {
            return beanRegistrations.toArray(new BeanRegistration[beanRegistrations.size()]);
        } else {
            return coerceCollectionToCorrectType(argument.getType(), beanRegistrations);
        }
    }

    private <B> B resolveBeanWithGenericsForField(BeanResolutionContext resolutionContext, FieldInjectionPoint injectionPoint, BeanResolver<B> beanResolver) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, injectionPoint);
        Argument argument = injectionPoint.asArgument();
        try {
            Argument genericType = argument.isArray() ? Argument.of(argument.getType().getComponentType()) : argument.getFirstTypeVariable().orElse(argument);
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            @SuppressWarnings("unchecked") B bean = (B) beanResolver.resolveBean(genericType, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                path.pop();
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, injectionPoint, e);
        }
    }

    private boolean isConfigurationProperties() {
        return isConfigurationProperties;
    }

    private Qualifier resolveQualifier(BeanResolutionContext resolutionContext, Argument argument) {
        return resolveQualifier(resolutionContext, argument, false);
    }

    private Qualifier resolveQualifier(
            BeanResolutionContext resolutionContext,
            Argument argument,
            boolean innerConfiguration) {
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        boolean hasMetadata = annotationMetadata != AnnotationMetadata.EMPTY_METADATA;
        Class<? extends Annotation> qualifierType = hasMetadata ? annotationMetadata.getAnnotationTypeByStereotype(javax.inject.Qualifier.class).orElse(null) : null;
        if (qualifierType != null) {
            return Qualifiers.byAnnotation(
                    annotationMetadata,
                    qualifierType
            );
        } else {
            if (hasMetadata && annotationMetadata.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)) {
                return Qualifiers.byInterceptorBinding(annotationMetadata);
            }
            Class<?>[] byType = hasMetadata ? annotationMetadata.hasDeclaredAnnotation(Type.class) ? annotationMetadata.classValues(Type.class) : null : null;
            if (byType != null) {
                return Qualifiers.byType(byType);
            } else {
                Qualifier qualifier = null;
                boolean isIterable = isIterable() || resolutionContext.get(EachProperty.class.getName(), Class.class).map(getBeanType()::equals).orElse(false);
                if (isIterable) {
                    Optional<Qualifier> optional = resolutionContext.get(javax.inject.Qualifier.class.getName(), Map.class)
                            .map(map -> (Qualifier) map.get(argument));
                    qualifier = optional.orElse(null);
                }
                if (qualifier == null) {
                    if ((hasMetadata && argument.isAnnotationPresent(Parameter.class)) ||
                            (innerConfiguration && isIterable) ||
                            Qualifier.class == argument.getType()) {
                        final Optional<String> n = resolutionContext.get(NAMED_ATTRIBUTE, ConversionContext.STRING);
                        qualifier = n.map(Qualifiers::byName).orElse(null);
                    }
                }
                return qualifier;
            }
        }

    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Object resolveOptionalObject(Optional value) {
        if (!value.isPresent()) {
            return value;
        } else {
            Object convertedOptional = value.get();
            if (convertedOptional instanceof Optional) {
                return convertedOptional;
            } else {
                return value;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object coerceCollectionToCorrectType(Class collectionType, Collection beansOfType) {
        if (collectionType.isInstance(beansOfType)) {
            return beansOfType;
        } else {
            return CollectionUtils.convertCollection(collectionType, beansOfType).orElse(null);
        }
    }

    private void addRequiredComponents(Argument... arguments) {
        if (arguments != null) {
            for (Argument argument : arguments) {
                requiredComponents.add(argument.getType());
            }
        }
    }

    private void instrumentAnnotationMetadata(BeanContext context, Object object) {
        if (object instanceof EnvironmentConfigurable && context instanceof ApplicationContext) {
            final EnvironmentConfigurable ec = (EnvironmentConfigurable) object;
            if (ec.hasPropertyExpressions()) {
                ec.configure(((ApplicationContext) context).getEnvironment());
            }
        }
    }

    /**
     * Internal environment aware annotation metadata delegate.
     */
    private final class BeanAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {
        BeanAnnotationMetadata(AnnotationMetadata targetMetadata) {
            super(targetMetadata);
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }

    /**
     * Class used as a method key.
     */
    private final class MethodKey {
        final String name;
        final Class[] argumentTypes;

        MethodKey(String name, Class[] argumentTypes) {
            this.name = name;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            @SuppressWarnings("unchecked") MethodKey methodKey = (MethodKey) o;

            if (!name.equals(methodKey.name)) {
                return false;
            }
            return Arrays.equals(argumentTypes, methodKey.argumentTypes);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(argumentTypes);
            return result;
        }
    }

    /**
     * Bean resolver.
     *
     * @param <T> The type
     */
    private interface BeanResolver<T> {
        T resolveBean(Argument<T> beanType, Qualifier<T> qualifier);
    }
}
