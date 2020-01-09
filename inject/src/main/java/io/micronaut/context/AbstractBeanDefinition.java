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
/*
 * Copyright 2017 original authors
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
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
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
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.*;
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
    protected final List<MethodInjectionPoint> methodInjectionPoints = new ArrayList<>(3);
    @SuppressWarnings("WeakerAccess")
    protected final List<FieldInjectionPoint> fieldInjectionPoints = new ArrayList<>(3);
    @SuppressWarnings("WeakerAccess")
    protected final List<MethodInjectionPoint> postConstructMethods = new ArrayList<>(1);
    @SuppressWarnings("WeakerAccess")
    protected final List<MethodInjectionPoint> preDestroyMethods = new ArrayList<>(1);
    @SuppressWarnings("WeakerAccess")
    protected final Map<MethodKey, ExecutableMethod<T, ?>> executableMethodMap = new LinkedHashMap<>(3);

    private final Class<T> type;
    private final boolean isAbstract;
    private final boolean singleton;
    private final boolean isProvided;
    private final boolean isConfigurationProperties;
    private final Class<?> declaringType;
    private final ConstructorInjectionPoint<T> constructor;
    private final Collection<Class> requiredComponents = new HashSet<>(3);
    private AnnotationMetadata beanAnnotationMetadata;
    private Environment environment;

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
                                     Argument... arguments) {

        AnnotationMetadata beanAnnotationMetadata = getAnnotationMetadata();
        this.singleton = beanAnnotationMetadata.hasDeclaredStereotype(Singleton.class);
        this.isProvided = beanAnnotationMetadata.hasDeclaredStereotype(Provided.class);
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
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    @UsedByGeneratedCode
    protected AbstractBeanDefinition(Class<T> type,
                                     AnnotationMetadata constructorAnnotationMetadata,
                                     boolean requiresReflection,
                                     Argument... arguments) {

        AnnotationMetadata beanAnnotationMetadata = getAnnotationMetadata();
        this.type = type;
        this.isAbstract = Modifier.isAbstract(this.type.getModifiers());
        this.isProvided = beanAnnotationMetadata.hasDeclaredStereotype(Provided.class);
        this.singleton = beanAnnotationMetadata.hasDeclaredStereotype(Singleton.class);
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
    }

    @Override
    public @Nonnull List<Argument<?>> getTypeArguments(String type) {
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
    public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class... argumentTypes) {
        MethodKey methodKey = new MethodKey(name, argumentTypes);
        ExecutableMethod<T, R> invocableMethod = (ExecutableMethod<T, R>) executableMethodMap.get(methodKey);
        if (invocableMethod != null) {
            return Optional.of(invocableMethod);
        } else {
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Stream<ExecutableMethod<T, ?>> findPossibleMethods(String name) {
        if (executableMethodMap.keySet().stream().anyMatch(methodKey -> methodKey.name.equals(name))) {
            return executableMethodMap
                    .values()
                    .stream()
                    .filter((method) -> method.getMethodName().equals(name));
        } else {
            return Stream.empty();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractBeanDefinition<?> that = (AbstractBeanDefinition<?>) o;

        return getClass().equals(that.getClass());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Definition: " + declaringType.getName();
    }

    @Override
    public boolean isProvided() {
        return isProvided;
    }

    @Override
    public boolean isSingleton() {
        return singleton;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Class<? extends Annotation>> getScope() {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(Scope.class);
    }

    @Override
    public Class<T> getBeanType() {
        return type;
    }

    @Override
    public Optional<Class<?>> getDeclaringType() {
        return Optional.ofNullable(declaringType);
    }

    @Override
    public ConstructorInjectionPoint<T> getConstructor() {
        return constructor;
    }

    @Override
    public Collection<Class> getRequiredComponents() {
        return Collections.unmodifiableCollection(requiredComponents);
    }

    @Override
    public Collection<MethodInjectionPoint> getInjectedMethods() {
        return Collections.unmodifiableCollection(methodInjectionPoints);
    }

    @Override
    public Collection<FieldInjectionPoint> getInjectedFields() {
        return Collections.unmodifiableCollection(fieldInjectionPoints);
    }

    @Override
    public Collection<MethodInjectionPoint> getPostConstructMethods() {
        return Collections.unmodifiableCollection(postConstructMethods);
    }

    @Override
    public Collection<MethodInjectionPoint> getPreDestroyMethods() {
        return Collections.unmodifiableCollection(preDestroyMethods);
    }

    @Override
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
        return Collections.unmodifiableCollection(this.executableMethodMap.values());
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

            for (MethodInjectionPoint methodInjectionPoint : methodInjectionPoints) {
                if (methodInjectionPoint instanceof EnvironmentConfigurable) {
                    ((EnvironmentConfigurable) methodInjectionPoint).configure(environment);
                }
            }

            for (ExecutableMethod<T, ?> executableMethod : executableMethodMap.values()) {
                if (executableMethod instanceof EnvironmentConfigurable) {
                    ((EnvironmentConfigurable) executableMethod).configure(environment);
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
    @SuppressWarnings({"unchecked", "unused"})
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
    @SuppressWarnings({"WeakerAccess", "unused"})
    @UsedByGeneratedCode
    protected Object injectAnother(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        if (bean == null) {
            throw new BeanInstantiationException(resolutionContext, "Bean factory returned null");
        }
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
    @SuppressWarnings({"WeakerAccess", "unused", "unchecked"})
    @Internal
    @UsedByGeneratedCode
    protected Object postConstruct(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        boolean addInCreationHandling = singleton && !postConstructMethods.isEmpty();
        DefaultBeanContext.BeanKey key = null;
        if (addInCreationHandling) {
            // ensure registration as an inflight bean if a post construct is present
            // this is to ensure that if the post construct method does anything funky to
            // cause recreation of this bean then we don't have a circular problem
            key = new DefaultBeanContext.BeanKey(this, resolutionContext.getCurrentQualifier());
            resolutionContext.addInFlightBean(key, bean);
        }
        Collection<BeanRegistration<BeanInitializedEventListener>> beanInitializedEventListeners = ((DefaultBeanContext) context).beanInitializedEventListeners;
        if (CollectionUtils.isNotEmpty(beanInitializedEventListeners)) {
            for (BeanRegistration<BeanInitializedEventListener> registration : beanInitializedEventListeners) {
                BeanDefinition<BeanInitializedEventListener> definition = registration.getBeanDefinition();
                List<Argument<?>> typeArguments = definition.getTypeArguments(BeanInitializedEventListener.class);
                if (CollectionUtils.isEmpty(typeArguments) || typeArguments.get(0).getType().isAssignableFrom(getBeanType())) {
                    BeanInitializedEventListener listener = registration.getBean();
                    bean = listener.onInitialized(new BeanInitializingEvent(context, this, bean));
                    if (bean == null) {
                        throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onInitialized event");
                    }
                }
            }
        }

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
        instrumentAnnotationMetadata(context, fieldInjectionPoint);
        boolean isInject = fieldInjectionPoint.getAnnotationMetadata().hasDeclaredAnnotation(Inject.class);
        try {
            Object value;
            if (isInject) {
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
                if (fieldInjectionPoint.isDeclaredNullable()) {
                    //noinspection unchecked
                    fieldInjectionPoint.set(bean, null);
                } else {
                    throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, "Error setting field value: " + e.getMessage(), e);
                }
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
                Class argumentType = argument.getType();

                if (isInnerConfiguration(argumentType)) {
                    Qualifier qualifier = resolveQualifier(resolutionContext, argument, true);
                    return ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                } else {
                    String argumentName = argument.getName();
                    String valString = resolvePropertyValueName(resolutionContext, injectionPoint.getAnnotationMetadata(), argument, valueAnnStr);


                    ApplicationContext applicationContext = (ApplicationContext) context;
                    ArgumentConversionContext conversionContext = ConversionContext.of(argument);
                    Optional value = resolveValue(applicationContext, conversionContext, valueAnnStr != null, valString);
                    if (argumentType == Optional.class) {
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
            boolean result = isConfigProps || Map.class.isAssignableFrom(type) ? applicationContext.containsProperties(valString) : applicationContext.containsProperty(valString);
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
        Argument argument = injectionPoint.getArguments()[argIndex];
        if (argument instanceof DefaultArgument) {
            argument = new EnvironmentAwareArgument((DefaultArgument) argument);
            instrumentAnnotationMetadata(context, argument);
        }
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
                        return ((DefaultBeanContext) context).getBean(resolutionContext, beanType, qualifier);
                    } else {
                        return ((DefaultBeanContext) context).getBeansOfType(resolutionContext, beanType, qualifier);
                    }

                }
        );
    }

    /**
     * Obtains a bean provider for the method at the given index and the argument at the given index
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
    protected final Provider getBeanProviderForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, injectionPoint, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeanProvider(resolutionContext, beanType, qualifier)
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
        Argument<?> argument = constructorInjectionPoint.getArguments()[argIndex];
        if (argument instanceof DefaultArgument) {
            argument = new EnvironmentAwareArgument((DefaultArgument) argument);
            instrumentAnnotationMetadata(context, argument);
        }
        Class argumentType = argument.getType();
        if (argumentType == BeanResolutionContext.class) {
            return resolutionContext;
        } else if (argumentType.isArray()) {
            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
            return beansOfType.toArray((Object[]) Array.newInstance(argumentType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(argumentType)) {
            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
            return coerceCollectionToCorrectType(argumentType, beansOfType);
        } else if (Stream.class.isAssignableFrom(argumentType)) {
            return streamOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
        } else if (Provider.class.isAssignableFrom(argumentType)) {
            return getBeanProviderForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
        } else if (Optional.class.isAssignableFrom(argumentType)) {
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
                    Qualifier qualifier = resolveQualifier(resolutionContext, argument, isInnerConfiguration(argumentType));
                    if (Qualifier.class.isAssignableFrom(argumentType)) {
                        bean = qualifier;
                    } else {
                        //noinspection unchecked
                        bean = ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                    }
                    path.pop();
                    return bean;
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
                String prop = valAnn.orElseGet(() ->
                        argMetadata.stringValue(Property.class, "name")
                                .orElseThrow(() -> new IllegalStateException("Compiled getValueForMethodArgument(..) call present but @Value annotation missing."))
                );
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
                            throw new DependencyInjectionException(resolutionContext, conversionContext, prop);
                        }
                    }
                }
            } else {
                throw new DependencyInjectionException(resolutionContext, argument, "BeanContext must support property resolution");
            }
            path.pop();
            return result;
        } catch (NoSuchBeanException | BeanInstantiationException e) {
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    /**
     * Obtains a bean provider for a constructor at the given index
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
    protected final Provider getBeanProviderForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, @SuppressWarnings("unused") ConstructorInjectionPoint constructorInjectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeanProvider(resolutionContext, beanType, qualifier)
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
                Class<?> fieldType = injectionPoint.getType();
                if (isInnerConfiguration(fieldType)) {
                    Qualifier qualifier = resolveQualifier(resolutionContext, injectionPoint.asArgument(), true);
                    return ((DefaultBeanContext) context).getBean(resolutionContext, fieldType, qualifier);
                } else {
                    String valString = resolvePropertyValueName(resolutionContext, injectionPoint, valueAnnVal, annotationMetadata);
                    Argument fieldArgument = injectionPoint.asArgument();
                    ArgumentConversionContext conversionContext = ConversionContext.of(fieldArgument);
                    Optional value = resolveValue((ApplicationContext) context, conversionContext, valueAnnVal != null, valString);
                    if (fieldType == Optional.class) {
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
            Class<?> beanType = getBeanType();
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
            Class<?> beanType = getBeanType();
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
            boolean result = isConfigProps || Map.class.isAssignableFrom(fieldType) ? applicationContext.containsProperties(valString) : applicationContext.containsProperty(valString);
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
        Class beanType = injectionPoint.getType();
        if (beanType.isArray()) {
            Collection beansOfType = getBeansOfTypeForField(resolutionContext, context, injectionPoint);
            return beansOfType.toArray((Object[]) Array.newInstance(beanType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(beanType)) {
            Collection beansOfType = getBeansOfTypeForField(resolutionContext, context, injectionPoint);
            if (beanType.isInstance(beansOfType)) {
                return beansOfType;
            } else {
                //noinspection unchecked
                return CollectionUtils.convertCollection(beanType, beansOfType).orElse(null);
            }
        } else if (Stream.class.isAssignableFrom(beanType)) {
            return getStreamOfTypeForField(resolutionContext, context, injectionPoint);
        } else if (Provider.class.isAssignableFrom(beanType)) {
            return getBeanProviderForField(resolutionContext, context, injectionPoint);
        } else if (Optional.class.isAssignableFrom(beanType)) {
            return findBeanForField(resolutionContext, context, injectionPoint);
        } else {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            path.pushFieldResolve(this, injectionPoint);

            try {
                Qualifier qualifier = resolveQualifier(resolutionContext, injectionPoint.asArgument());
                @SuppressWarnings("unchecked") Object bean = ((DefaultBeanContext) context).getBean(resolutionContext, beanType, qualifier);
                path.pop();
                return bean;
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
    protected final Provider getBeanProviderForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
        return resolveBeanWithGenericsForField(resolutionContext, injectionPoint, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeanProvider(resolutionContext, beanType, qualifier)
        );
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
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            // we make a copy of the result of annotation metadata which is normally a reference
            // to the class metadata
            return new BeanAnnotationMetadata((DefaultAnnotationMetadata) annotationMetadata);
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
            List<MethodInjectionPoint> targetInjectionPoints) {
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
        } else if (Provider.class.isAssignableFrom(argumentType)) {
            return getBeanProviderForMethodArgument(resolutionContext, context, injectionPoint, argument);
        } else if (Optional.class.isAssignableFrom(argumentType)) {
            return findBeanForMethodArgument(resolutionContext, context, injectionPoint, argument);
        } else {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            path.pushMethodArgumentResolve(this, injectionPoint, argument);
            try {
                Qualifier qualifier = resolveQualifier(resolutionContext, argument);
                @SuppressWarnings("unchecked") Object bean = ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                path.pop();
                return bean;
            } catch (NoSuchBeanException e) {
                if (argument.isDeclaredNullable()) {
                    path.pop();
                    return null;
                }
                throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
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
            Optional<String> namedBean = resolutionContext.get(Named.class.getName(), String.class);
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

    private boolean isInnerConfiguration(Class argumentType) {
        return isConfigurationProperties &&
                argumentType.getName().indexOf('$') > -1 &&
                !argumentType.isEnum() &&
                !argumentType.isPrimitive() &&
                Modifier.isPublic(argumentType.getModifiers()) && Modifier.isStatic(argumentType.getModifiers()) &&
                isInnerOfAnySuperclass(argumentType);
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
            Class genericType = resolveGenericType(argument, argumentType);
            @SuppressWarnings("unchecked") B bean = (B) beanResolver.resolveBean(genericType != null ? genericType : argumentType, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
        }
    }

    private Class resolveGenericType(Argument argument, Class argumentType) {
        Class genericType;
        if (argumentType.isArray()) {
            genericType = argumentType.getComponentType();
        } else {
            Map<String, Argument<?>> genericTypes = argument.getTypeVariables();
            if (genericTypes.size() == 1) {
                genericType = genericTypes.values().iterator().next().getType();
            } else {
                genericType = null;
            }
        }
        return genericType;
    }

    private <B> B resolveBeanWithGenericsFromConstructorArgument(BeanResolutionContext resolutionContext, Argument argument, BeanResolver<B> beanResolver) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this, argument);
        try {
            Class argumentType = argument.getType();
            Class genericType = resolveGenericType(argument, argumentType);
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            @SuppressWarnings("unchecked") B bean = (B) beanResolver.resolveBean(genericType != null ? genericType : argumentType, qualifier);
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

    private <B> B resolveBeanWithGenericsForField(BeanResolutionContext resolutionContext, FieldInjectionPoint injectionPoint, BeanResolver<B> beanResolver) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, injectionPoint);
        Argument argument = injectionPoint.asArgument();
        try {
            Optional<Class> genericType = injectionPoint.getType().isArray() ? Optional.of(injectionPoint.getType().getComponentType()) : argument.getFirstTypeVariable().map(Argument::getType);
            Qualifier qualifier = resolveQualifier(resolutionContext, injectionPoint.asArgument());
            @SuppressWarnings("unchecked") B bean = (B) beanResolver.resolveBean(genericType.orElse(injectionPoint.getType()), qualifier);
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
                        final Optional<String> n = resolutionContext.get(NAMED_ATTRIBUTE, String.class);
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
            ((EnvironmentConfigurable) object).configure(((ApplicationContext) context).getEnvironment());
        }
    }

    /**
     * Internal environment aware annotation metadata delegate.
     */
    private final class BeanAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {
        BeanAnnotationMetadata(DefaultAnnotationMetadata targetMetadata) {
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
        T resolveBean(Class<T> beanType, Qualifier<T> qualifier);
    }
}
