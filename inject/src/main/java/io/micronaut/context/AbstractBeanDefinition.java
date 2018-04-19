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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Provided;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
 * @author Graeme Rocher
 * @see io.micronaut.inject.writer.BeanDefinitionWriter
 * @since 1.0
 */
@Internal
public class AbstractBeanDefinition<T> extends AbstractBeanContextConditional implements BeanDefinition<T> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractBeanDefinition.class);

    protected final List<MethodInjectionPoint> methodInjectionPoints = new ArrayList<>(3);
    protected final List<FieldInjectionPoint> fieldInjectionPoints = new ArrayList<>(3);
    protected final List<MethodInjectionPoint> postConstructMethods = new ArrayList<>(1);
    protected final List<MethodInjectionPoint> preDestroyMethods = new ArrayList<>(1);
    protected final Map<MethodKey, ExecutableMethod<T, ?>> executableMethodMap = new LinkedHashMap<>(3);

    private final Class<T> type;
    private final boolean isAbstract;
    private final boolean singleton;
    private final boolean isProvided;
    private final boolean isConfigurationProperties;
    private final Class<?> declaringType;
    private final ConstructorInjectionPoint<T> constructor;
    private final Collection<Class> requiredComponents = new HashSet<>(3);
    private Map<Class, String> valuePrefixes;

    /**
     * Constructs a bean definition that is produced from a method call on another type ( factory bean )
     *
     * @param method    The method to call
     * @param arguments The arguments
     */
    @SuppressWarnings("unchecked")
    @Internal
    protected AbstractBeanDefinition(Method method,
                                     Argument... arguments) {

        AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        this.singleton = annotationMetadata.hasDeclaredStereotype(Singleton.class);
        this.isProvided = annotationMetadata.hasDeclaredStereotype(Provided.class);
        this.type = (Class<T>) method.getReturnType();
        this.isAbstract = false; // factory beans are never abstract
        this.declaringType = method.getDeclaringClass();
        this.constructor = new MethodConstructorInjectionPoint(this, method, Modifier.isPrivate(method.getModifiers()), arguments);
        this.isConfigurationProperties = hasStereotype(ConfigurationReader.class) || isIterable();
        this.valuePrefixes = isConfigurationProperties ? new HashMap<>(2) : null;
        this.addRequiredComponents(arguments);
    }

    /**
     * Constructs a bean for the given type
     * @param type The type
     * @param constructor
     * @param arguments The constructor arguments used to build the bean
     */
    @Internal
    protected AbstractBeanDefinition(Class<T> type,
                                     Constructor<T> constructor,
                                     Argument... arguments) {

        AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        this.type = type;
        this.isAbstract = Modifier.isAbstract(this.type.getModifiers());
        this.isProvided = annotationMetadata.hasDeclaredStereotype(Provided.class);
        this.singleton = annotationMetadata.hasDeclaredStereotype(Singleton.class);
        this.declaringType = type;
        this.constructor = new ReflectionConstructorInjectionPoint<>(this, constructor, arguments);
        this.isConfigurationProperties = hasStereotype(ConfigurationReader.class) || isIterable();
        this.valuePrefixes = isConfigurationProperties ? new HashMap<>(2) : null;
        this.addRequiredComponents(arguments);
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
            Optional<Method> method = ReflectionUtils.findMethod(type, name, argumentTypes);
            return method.map(theMethod -> {
                        ReflectionExecutableMethod<T, R> reflectionMethod = new ReflectionExecutableMethod<>(this, theMethod);
                        executableMethodMap.put(methodKey, reflectionMethod);
                        return reflectionMethod;
                    }
            );
        }
    }

    @Override
    public Stream<ExecutableMethod<T, ?>> findPossibleMethods(String name) {
        if (executableMethodMap.keySet().stream().anyMatch(methodKey -> methodKey.name.equals(name))) {
            return executableMethodMap
                    .values()
                    .stream()
                    .filter((method) -> method.getMethodName().equals(name));
        } else {
            return ReflectionUtils
                    .findMethodsByName(type, name)
                    .map((method) -> new ReflectionExecutableMethod<>(this, method));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

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

    @Override
    public T inject(BeanContext context, T bean) {
        return (T) injectBean(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    @Override
    public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return (T) injectBean(resolutionContext, context, bean);
    }

    @Override
    public Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
        return Collections.unmodifiableCollection(this.executableMethodMap.values());
    }

    /**
     * Configures the bean for the given {@link BeanContext}. If the context features an {@link Environment} this
     * method configures the annotation metadata such that environment aware values are returned
     *
     * @param context The bean context
     */
    void configure(BeanContext context) {
        AnnotationMetadata am = getAnnotationMetadata();
        if (am instanceof DefaultAnnotationMetadata) {
            ((DefaultAnnotationMetadata) am).configure(context);
        }
        for (MethodInjectionPoint methodInjectionPoint : methodInjectionPoints) {
            AnnotationMetadata annotationMetadata = methodInjectionPoint.getAnnotationMetadata();
            if (annotationMetadata instanceof DefaultAnnotationMetadata) {
                ((DefaultAnnotationMetadata) annotationMetadata).configure(context);
            }
        }

        executableMethodMap.values().parallelStream().forEach(method -> {
            AnnotationMetadata annotationMetadata = method.getAnnotationMetadata();
            if (annotationMetadata instanceof DefaultAnnotationMetadata) {
                ((DefaultAnnotationMetadata) annotationMetadata).configure(context);
            }
        });
    }

    /**
     * Allows printing warning messages produced by the compiler
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
     * Allows printing warning messages produced by the compiler
     *
     * @param type     The type
     * @param property The property
     */
    @Internal
    protected final void warnMissingProperty(Class type, String method, String property) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Configuration property [{}] could not be set as the underlying method [{}] does not exist on builder [{}]. This usually indicates the configuration option was deprecated and has been removed by the builder implementation (potentially a third-party library).", property, method, type);
        }
    }

    /**
     * Resolves the proxied bean instance for this bean
     *
     * @param beanContext The {@link BeanContext}
     * @return The proxied bean
     */
    @SuppressWarnings("unchecked")
    @Internal
    protected final Object getProxiedBean(BeanContext beanContext) {
        DefaultBeanContext defaultBeanContext = (DefaultBeanContext) beanContext;
        Optional<String> qualifier = getAnnotationMetadata().getAnnotationNameByStereotype(javax.inject.Qualifier.class);
        return defaultBeanContext.getProxyTargetBean(getBeanType(), (Qualifier<T>) qualifier.map(q -> Qualifiers.byAnnotation(getAnnotationMetadata(), q)).orElse(null));
    }

    /**
     * Adds a new {@link ExecutableMethod}
     *
     * @param executableMethod The method
     * @return The bean definition
     */
    @Internal
    protected final AbstractBeanDefinition<T> addExecutableMethod(ExecutableMethod<T, ?> executableMethod) {
        MethodKey key = new MethodKey(executableMethod.getMethodName(), executableMethod.getArgumentTypes());
        executableMethodMap.put(key, executableMethod);
        return this;
    }

    /**
     * Adds an injection point for a field. Typically called by a dynamically generated subclass.
     *
     * @param field     The field
     * @param qualifier The qualifier, can be null
     * @return this component definition
     */
    @Internal
    protected final AbstractBeanDefinition addInjectionPoint(Field field, Annotation qualifier, boolean requiresReflection) {
        if (field.getAnnotation(Inject.class) != null) {
            requiredComponents.add(field.getType());
        }
        fieldInjectionPoints.add(new DefaultFieldInjectionPoint(this, field, qualifier, requiresReflection));
        return this;
    }

    /**
     * Adds an injection point for a field. Typically called by a dynamically generated subclass.
     *
     * @param field The field
     * @return this component definition
     */
    @Internal
    protected final AbstractBeanDefinition addInjectionPoint(Field field, boolean requiresReflection) {
        if (field.getAnnotation(Inject.class) != null) {
            requiredComponents.add(field.getType());
        }
        fieldInjectionPoints.add(new DefaultFieldInjectionPoint(this, field, null, requiresReflection));
        return this;
    }


    /**
     * Adds an injection point for a method that cannot be resolved at runtime, but a compile time produced injection point
     * exists. This allows the framework to recover and relay better error messages to the user instead of just NoSuchMethodError
     *
     * @param declaringType      The declaring type
     * @param method             The method
     * @param arguments          The argument types
     * @param annotationMetadata The annotation metadata
     * @param requiresReflection Whether the method requires reflection to invoke
     * @return this component definition
     */
    @SuppressWarnings("unchecked")
    @Internal
    protected final AbstractBeanDefinition addInjectionPoint(
            Class declaringType,
            String method,
            Argument[] arguments,
            AnnotationMetadata annotationMetadata,
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
     * Adds an injection point for a setter and field to be set. Typically called by a dynamically generated subclass.
     *
     * @param setter The method
     * @return this component definition
     */
    @Internal
    protected final AbstractBeanDefinition addInjectionPoint(
            Field field,
            Method setter,
            Argument argument,
            boolean requiresReflection) {

        ReflectionMethodInjectionPoint methodInjectionPoint = new ReflectionMethodInjectionPoint(
                this,
                field,
                setter,
                requiresReflection,
                argument
        );
        if (field.getAnnotation(Inject.class) != null || setter.getAnnotation(Inject.class) != null) {
            requiredComponents.add(field.getType());
        }
        methodInjectionPoints.add(methodInjectionPoint);
        return this;
    }



    /**
     * Adds a post construct method definition
     *
     * @param declaringType The declaring type
     * @param method             The method
     * @param arguments          The arguments
     * @param requiresReflection Whether the method requires reflection
     * @return This bean definition
     */
    @Internal
    protected final AbstractBeanDefinition addPostConstruct(Class declaringType,
                                                            String method,
                                                            Argument[] arguments,
                                                            AnnotationMetadata annotationMetadata,
                                                            boolean requiresReflection) {
        return addInjectionPointInternal(declaringType, method, arguments, annotationMetadata, requiresReflection, this.postConstructMethods);
    }

    /**
     * Adds a pre destroy method definition
     *
     * @param declaringType The declaring type
     * @param method             The method
     * @param arguments          The arguments
     * @param requiresReflection Whether the method requires reflection
     * @return This bean definition
     */
    @Internal
    protected final AbstractBeanDefinition addPreDestroy(Class declaringType,
                                                         String method,
                                                         Argument[] arguments,
                                                         AnnotationMetadata annotationMetadata,
                                                         boolean requiresReflection) {
        return addInjectionPointInternal(declaringType, method, arguments, annotationMetadata, requiresReflection, this.preDestroyMethods);
    }

    /**
     * The default implementation which provides no injection. To be overridden by compile time tooling
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @return The injected bean
     */
    @Internal
    protected Object injectBean(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        try {
            return bean;
        } finally {
            if (valuePrefixes != null && isSingleton()) {
                // free up memory by clearing the value prefixes after injection completes
                valuePrefixes.clear();
            }
        }
    }

    /**
     * Inject another bean, for example one created via factory
     *
     * @param resolutionContext The reslution context
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    protected Object injectAnother(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        if (bean == null) {
            throw new BeanInstantiationException(resolutionContext, "Bean factory returned null");
        }
        return defaultContext.inject(resolutionContext, this, bean);
    }

    /**
     * Default postConstruct hook that only invokes methods that require reflection. Generated subclasses should override to call methods that don't require reflection
     *
     * @param resolutionContext The resolution hook
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    protected Object postConstruct(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        Collection<BeanInitializedEventListener> initializedEventListeners = defaultContext.getBeansOfType(resolutionContext, BeanInitializedEventListener.class, null);
        for (BeanInitializedEventListener listener : initializedEventListeners) {
            Optional<Class> targetType = GenericTypeUtils.resolveInterfaceTypeArgument(listener.getClass(), BeanInitializedEventListener.class);
            if (!targetType.isPresent() || targetType.get().isInstance(bean)) {
                bean = listener.onInitialized(new BeanInitializingEvent(context, this, bean));
                if (bean == null) {
                    throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onCreated event");
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
        return bean;
    }

    /**
     * Default preDestroy hook that only invokes methods that require reflection. Generated subclasses should override to call methods that don't require reflection
     *
     * @param resolutionContext The resolution hook
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    protected Object preDestroy(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        for (int i = 0; i < methodInjectionPoints.size(); i++) {
            MethodInjectionPoint methodInjectionPoint = methodInjectionPoints.get(i);
            if (methodInjectionPoint.isPostConstructMethod() && methodInjectionPoint.requiresReflection()) {
                injectBeanMethod(resolutionContext, defaultContext, i, bean);
            }
        }

        if (bean instanceof LifeCycle) {
            bean = ((LifeCycle) bean).stop();
        }
        return bean;
    }

    /**
     * Inject a bean method that requires reflection
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param bean              The bean
     */
    @Internal
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
     * Injects the value of a field of a bean that requires reflection
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param index             The index of the field
     * @param bean              The bean being injected
     */
    @Internal
    protected void injectBeanField(BeanResolutionContext resolutionContext, DefaultBeanContext context, int index, Object bean) {
        FieldInjectionPoint fieldInjectionPoint = fieldInjectionPoints.get(index);
        boolean isInject = fieldInjectionPoint.getField().getAnnotation(Inject.class) != null;
        try {
            Object value;
            if (isInject) {
                value = getBeanForField(resolutionContext, context, fieldInjectionPoint);
            } else {
                value = getValueForField(resolutionContext, context, index);
            }
            if (value != null) {
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
     * Obtains a value for the given method argument
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @return The value
     */
    @Internal
    protected Object getValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) throws Throwable {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        if (context instanceof ApplicationContext) {
            // can't use orElseThrow here due to compiler bug
            try {
                Value valAnn = argument.getAnnotation(Value.class);
                Class argumentType = argument.getType();

                if (isInnerConfiguration(argumentType)) {
                    return ((DefaultBeanContext) context).createBean(resolutionContext, argumentType, null);
                } else {
                    String argumentName = argument.getName();
                    Class<?> declaringClass = injectionPoint.getMethod().getDeclaringClass();
                    String valString = resolveValueString(resolutionContext, context, declaringClass, injectionPoint.getDeclaringBean().getBeanType(), argumentName, valAnn);
                    ApplicationContext applicationContext = (ApplicationContext) context;
                    ArgumentConversionContext conversionContext = ConversionContext.of(argument);
                    Optional value = resolveValue(applicationContext, conversionContext, valAnn, valString);
                    if (argumentType == Optional.class) {
                        return resolveOptionalObject(value);
                    } else {
                        if (value.isPresent()) {
                            return value.get();
                        } else if (!Iterable.class.isAssignableFrom(argumentType) && !Map.class.isAssignableFrom(argumentType)) {
                            throw new DependencyInjectionException(resolutionContext, injectionPoint, conversionContext, valString);
                        } else {
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

    /**
     * Obtains a value for the given method argument
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @return The value
     */
    @Internal
    protected boolean containsValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) throws Throwable {
        if (context instanceof ApplicationContext) {
            MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
            Argument argument = injectionPoint.getArguments()[argIndex];
            String argumentName = argument.getName();
            Class<?> declaringClass = injectionPoint.getMethod().getDeclaringClass();
            Class beanType = injectionPoint.getDeclaringBean().getBeanType();
            Value valAnn = argument.getAnnotation(Value.class);
            String valString = resolveValueString(resolutionContext, context, declaringClass, beanType, argumentName, valAnn);
            ApplicationContext applicationContext = (ApplicationContext) context;
            Class type = argument.getType();
            boolean isConfigProps = type.getAnnotation(ConfigurationProperties.class) != null;
            boolean result = isConfigProps || Map.class.isAssignableFrom(type) ? applicationContext.containsProperties(valString) : applicationContext.containsProperty(valString);
            if (!result && isConfigurationProperties()) {
                String cliOption = resolveCliOption(argument.getName());
                if (cliOption != null) {
                    result = applicationContext.containsProperty(cliOption);
                }
            }
            if(result && injectionPoint instanceof MissingMethodInjectionPoint) {
                if(LOG.isWarnEnabled()) {
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
    protected Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        return getBeanForMethodArgument(resolutionContext, context, injectionPoint, argument);
    }

    protected Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
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
                Object bean = ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                path.pop();
                return bean;
            } catch (NoSuchBeanException e) {
                if (argument.getDeclaredAnnotation(Nullable.class) != null) {
                    path.pop();
                    return null;
                }
                throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
            }
        }
    }

    /**
     * Obtains all bean definitions for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @return The resolved bean
     */
    @Internal
    protected Collection getBeansOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, context, injectionPoint, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeansOfType(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains a bean provider for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @return The resolved bean
     */
    @Internal
    protected Provider getBeanProviderForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, context, injectionPoint, argument, (beanType, qualifier) ->
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
     * @return The resolved bean
     */
    @Internal
    protected Optional findBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, context, injectionPoint, argument, (beanType, qualifier) ->
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
     * @return The resolved bean
     */
    @Internal
    protected Stream streamOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, context, injectionPoint, argument, (beanType, qualifier) ->
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
    @Internal
    protected Object getBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex) {
        ConstructorInjectionPoint<T> constructorInjectionPoint = getConstructor();
        Argument<?> argument = constructorInjectionPoint.getArguments()[argIndex];
        Class argumentType = argument.getType();
        if (argumentType.isArray()) {
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
            boolean isNullable = argument.getDeclaredAnnotation(Nullable.class) != null;
            if (isNullable && current != null && current.getArgument().equals(argument)) {
                return null;
            } else {
                path.pushConstructorResolve(this, argument);
                try {
                    Object bean;
                    Qualifier qualifier = resolveQualifier(resolutionContext, argument);
                    bean = ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                    path.pop();
                    return bean;
                } catch (NoSuchBeanException | BeanInstantiationException e) {
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
    @Internal
    protected Object getValueForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex) {
        ConstructorInjectionPoint<T> constructorInjectionPoint = getConstructor();
        BeanResolutionContext.Path path = resolutionContext.getPath();
        Argument<?> argument = constructorInjectionPoint.getArguments()[argIndex];
        path.pushConstructorResolve(this, argument);
        try {
            Object result;
            if (context instanceof ApplicationContext) {
                ApplicationContext propertyResolver = (ApplicationContext) context;
                Value valAnn = argument.findAnnotation(Value.class)
                        .orElseThrow(() -> new IllegalStateException("Compiled getValueForMethodArgument(..) call present but @Value annotation missing."));

                String prop = valAnn.value();
                ArgumentConversionContext<?> conversionContext = ConversionContext.of(argument);
                Optional<?> value = resolveValue(propertyResolver, conversionContext, valAnn, prop);
                if (argument.getType() == Optional.class) {
                    return resolveOptionalObject(value);
                } else {
                    // can't use orElseThrow here due to compiler bug
                    result = value.orElseThrow(() -> new DependencyInjectionException(resolutionContext, conversionContext, prop));
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
     * @param resolutionContext The resolution context
     * @param context           The context
     * @return The resolved bean
     */
    @Internal
    protected Provider getBeanProviderForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, ConstructorInjectionPoint constructorInjectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeanProvider(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @return The resolved bean
     */
    @Internal
    protected Collection getBeansOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, ConstructorInjectionPoint<T> constructorInjectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeansOfType(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @return The resolved bean
     */
    @Internal
    protected Stream streamOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, ConstructorInjectionPoint<T> constructorInjectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @return The resolved bean
     */
    @Internal
    protected Optional findBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, ConstructorInjectionPoint<T> constructorInjectionPoint, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument, (beanType, qualifier) ->
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
     * @return The resolved bean
     */
    @Internal
    protected Object getBeanForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
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
    @Internal
    protected Object getValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) throws Throwable {
        FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, injectionPoint);
        try {
            if (context instanceof PropertyResolver) {
                Field field = injectionPoint.getField();
                Value valueAnn = field.getAnnotation(Value.class);
                Class<?> fieldType = field.getType();
                if (isInnerConfiguration(fieldType)) {
                    return context.createBean(fieldType);
                } else {
                    Class<?> beanType = injectionPoint.getDeclaringBean().getBeanType();
                    Class<?> declaringClass = field.getDeclaringClass();

                    String valString = resolveValueString(
                            resolutionContext,
                            context,
                            declaringClass,
                            beanType,
                            injectionPoint.getName(),
                            valueAnn
                    );
                    Argument fieldArgument = injectionPoint.asArgument();
                    ArgumentConversionContext conversionContext = ConversionContext.of(fieldArgument);
                    Optional value = resolveValue((ApplicationContext) context, conversionContext, valueAnn, valString);
                    if (fieldType == Optional.class) {
                        return resolveOptionalObject(value);
                    } else {
                        return value.orElseThrow(() -> new DependencyInjectionException(resolutionContext, injectionPoint, "Error resolving field value [" + valString + "]. Property doesn't exist or cannot be converted"));
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
     * Resolve a value for the given field of the given type and path
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param propertyType      The required property type
     * @param propertyPath      The property path
     * @param <T1>              The generic type
     * @return An optional value
     */
    @Internal
    protected <T1> Optional<T1> getValueForPath(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            Argument<T1> propertyType,
            String... propertyPath) {
        if (context instanceof PropertyResolver) {
            PropertyResolver propertyResolver = (PropertyResolver) context;
            Class<?> beanType = getBeanType();
            String valString = resolveValueString(
                    resolutionContext,
                    context,
                    beanType,
                    beanType,
                    Arrays.stream(propertyPath).collect(Collectors.joining(".")),
                    null
            );

            return propertyResolver.getProperty(valString, ConversionContext.of(propertyType));
        }
        return Optional.empty();
    }

    /**
     * Obtains a value for the given field argument
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param fieldIndex        The field index
     * @return True if it does
     */
    @Internal
    protected boolean containsValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) throws Throwable {
        if (context instanceof ApplicationContext) {
            FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
            Value valueAnn = injectionPoint.getAnnotation(Value.class);
            Class<?> beanType = injectionPoint.getDeclaringBean().getBeanType();
            Class<?> declaringClass = injectionPoint.getField().getDeclaringClass();

            String valString = resolveValueString(
                    resolutionContext,
                    context,
                    declaringClass,
                    beanType,
                    injectionPoint.getName(),
                    valueAnn
            );
            ApplicationContext applicationContext = (ApplicationContext) context;
            Class fieldType = injectionPoint.getType();
            boolean isConfigProps = fieldType.getAnnotation(ConfigurationProperties.class) != null;
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
     * If this bean is a {@link ConfigurationProperties} bean return whether any properties for it are configured within the context
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @return True if it does
     */
    @Internal
    protected boolean containsProperties(BeanResolutionContext resolutionContext, BeanContext context) {
        return containsProperties(resolutionContext, context, null);
    }

    /**
     * If this bean is a {@link ConfigurationProperties} bean return whether any properties for it are configured within the context
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @param subProperty       The subproperty to check
     * @return True if it does
     */
    @Internal
    protected boolean containsProperties(BeanResolutionContext resolutionContext, BeanContext context, String subProperty) {
        boolean isSubProperty = StringUtils.isNotEmpty(subProperty);
        if (!isSubProperty && !requiredComponents.isEmpty()) {
            // if the bean requires dependency injection we disable this optimization
            return true;
        }
        if (isConfigurationProperties && context instanceof ApplicationContext) {
            ApplicationContext appCtx = (ApplicationContext) context;
            Class<?> beanType = getBeanType();
            ConfigurationProperties annotation = beanType.getAnnotation(ConfigurationProperties.class);
            while (annotation != null) {
                if (ArrayUtils.isNotEmpty(annotation.cliPrefix())) {
                    // little bit of a hack this, would be nice if we had a better way to acknowledge CLI properties
                    return true;
                }

                String prefix = resolvePrefix(resolutionContext, context, beanType, beanType);

                if (isSubProperty) {
                    prefix += '.' + subProperty;
                }
                if (appCtx.containsProperties(prefix)) {
                    return true;
                }
                beanType = beanType.getSuperclass();
                if (beanType == null) {
                    break;
                } else {
                    annotation = beanType.getAnnotation(ConfigurationProperties.class);
                }
            }
        }
        return false;
    }

    /**
     * Resolves a bean for the given {@link FieldInjectionPoint}
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param context           The {@link BeanContext}
     * @param injectionPoint    The {@link FieldInjectionPoint}
     * @return The resolved bean
     * @throws DependencyInjectionException If the bean cannot be resolved
     */
    @Internal
    protected Object getBeanForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
        Class beanType = injectionPoint.getType();
        if (beanType.isArray()) {
            Collection beansOfType = getBeansOfTypeForField(resolutionContext, context, injectionPoint);
            return beansOfType.toArray((Object[]) Array.newInstance(beanType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(beanType)) {
            Collection beansOfType = getBeansOfTypeForField(resolutionContext, context, injectionPoint);
            if (beanType.isInstance(beansOfType)) {
                return beansOfType;
            } else {
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
                Qualifier qualifier = resolveQualifier(injectionPoint);
                Object bean = ((DefaultBeanContext) context).getBean(resolutionContext, beanType, qualifier);
                path.pop();
                return bean;
            } catch (NoSuchBeanException e) {
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
     * @return The resolved bean
     */
    @Internal
    protected Provider getBeanProviderForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
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
     * @return The resolved bean
     */
    @Internal
    protected Optional findBeanForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
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
     * @return The resolved bean
     */
    @Internal
    protected Collection getBeansOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
        return resolveBeanWithGenericsForField(resolutionContext, injectionPoint, (beanType, qualifier) ->
                ((DefaultBeanContext) context).getBeansOfType(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @return The resolved bean
     */
    @Internal
    protected Stream getStreamOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint) {
        return resolveBeanWithGenericsForField(resolutionContext, injectionPoint, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
    }

    private AbstractBeanDefinition addInjectionPointInternal(Class declaringType, String method, Argument[] arguments, AnnotationMetadata annotationMetadata, boolean requiresReflection, List<MethodInjectionPoint> targetInjectionPoints) {
        boolean isPreDestroy = targetInjectionPoints == this.preDestroyMethods;
        boolean isPostConstruct = targetInjectionPoints == this.postConstructMethods;

        MethodInjectionPoint injectionPoint;
        if(requiresReflection) {

            Optional<Method> beanMethod = ReflectionUtils.getMethod(declaringType, method, Argument.toClassArray(arguments));
            if (beanMethod.isPresent()) {
                Method javaMethod = beanMethod.get();
                // transform arguments to include annotations
                Argument[] newArguments;
                if (ArrayUtils.isNotEmpty(arguments)) {

                    newArguments = new Argument[arguments.length];
                    for (int i = 0; i < arguments.length; i++) {
                        Argument existing = arguments[0];
                        Annotation qualifier = existing.getQualifier();
                        newArguments[i] = Argument.of(
                                javaMethod,
                                existing.getName(),
                                i,
                                qualifier != null ? qualifier.annotationType() : null,
                                existing.getTypeParameters());
                    }
                } else {
                    newArguments = Argument.ZERO_ARGUMENTS;
                }
                injectionPoint = new ReflectionMethodInjectionPoint(this, javaMethod, true, newArguments);
            } else {
                injectionPoint = new MissingMethodInjectionPoint(
                        this,
                        declaringType,
                        method,
                        arguments
                );
            }
        }
        else {
            injectionPoint = new DefaultMethodInjectionPoint(
                    this,
                    declaringType,
                    method,
                    arguments,
                    annotationMetadata,
                    false
            );
        }
        targetInjectionPoints.add(injectionPoint);
        if(isPostConstruct || isPreDestroy) {
            this.methodInjectionPoints.add(injectionPoint);
        }
        addRequiredComponents(arguments);
        return this;
    }

    private Optional resolveValue(
            ApplicationContext context,
            ArgumentConversionContext<?> argument,
            Value val,
            String valString) {

        if (val != null) {

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

    private String resolveValueString(BeanResolutionContext resolutionContext, BeanContext beanContext, Class<?> declaringClass, Class<?> beanType, String name, Value val) {
        String valString;
        if (val == null) {
            if (isConfigurationProperties()) {
                if (Modifier.isAbstract(declaringClass.getModifiers())) {
                    declaringClass = getBeanType();
                }
                String prefix = resolvePrefix(resolutionContext, beanContext, declaringClass, beanType);
                valString = prefix + "." + name;
            } else {
                throw new IllegalStateException("Compiled getValue*(..) call present but @Value annotation missing for bean: " + declaringClass);
            }
        } else {
            valString = val.value();
        }
        return valString;
    }

    private String resolveCliOption(String name) {
        String attr = "cliPrefix";
        AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        if (annotationMetadata.isPresent(ConfigurationProperties.class, attr)) {
            return annotationMetadata.getValue(ConfigurationProperties.class, attr, String.class).map(val -> val + name).orElse(null);
        }
        return null;
    }

    private boolean isInnerConfiguration(Class argumentType) {
        return !argumentType.isEnum() && isConfigurationProperties() &&
                argumentType.getName().indexOf('$') > -1 &&
                Arrays.asList(getBeanType().getClasses()).contains(argumentType) &&
                Modifier.isPublic(argumentType.getModifiers()) && Modifier.isStatic(argumentType.getModifiers());
    }

    private String resolvePrefix(BeanResolutionContext resolutionContext, BeanContext beanContext, Class<?> declaringClass, Class<?> beanType) {
        return valuePrefixes.computeIfAbsent(declaringClass, aClass -> {
            String configurationPropertiesString = resolveConfigPropertiesValue(declaringClass, beanContext);
            StringBuilder prefix = new StringBuilder();
            boolean isInner = declaringClass.getDeclaringClass() != null;
            if (isInner) {
                // must be an inner class
                String name;

                if (configurationPropertiesString != null) {
                    prefix.append(configurationPropertiesString);
                } else {
                    String beanTypeString = resolveConfigPropertiesValue(beanType, beanContext);
                    if (beanTypeString != null) {
                        prefix.append(beanTypeString);
                    } else {

                        name = beanType.getName();
                        int i = name.lastIndexOf('$');
                        if (i > -1) {
                            name = name.substring(i + 1, name.length());
                        }
                        prefix.append(NameUtils.decapitalize(name));
                    }
                }
                Optional<String> named = resolutionContext.get(Named.class.getName(), String.class);
                named.ifPresent(val -> prefix.insert(0, '.').insert(0, val));

                Class<?> nestedType = declaringClass.getDeclaringClass();
                while (nestedType != null) {
                    configurationPropertiesString = resolveConfigPropertiesValue(nestedType, beanContext);
                    if (configurationPropertiesString != null) {
                        prefix.insert(0, '.')
                                .insert(0, configurationPropertiesString);
                        prependSuperClasses(prefix, nestedType, beanContext);
                    } else {
                        break;
                    }
                    nestedType = nestedType.getDeclaringClass();

                }
            } else if (configurationPropertiesString != null) {
                prefix.append(configurationPropertiesString);
            }

            prependSuperClasses(prefix, declaringClass, beanContext);
            if (!isInner) {
                boolean isForEach = isForEachBean(resolutionContext, beanType);
                if (isForEach) {
                    Optional<String> named = resolutionContext.get(Named.class.getName(), String.class);
                    named.ifPresent(val -> prefix.append('.').append(val));
                }
            }
            return prefix.toString();
        });
    }

    private Boolean isForEachBean(BeanResolutionContext resolutionContext, Class<?> beanType) {
        return resolutionContext.get(EachProperty.class.getName(), Class.class).map(type -> type.equals(beanType)).orElse(false);
    }

    private void prependSuperClasses(StringBuilder prefix, Class<?> nestedType, BeanContext beanContext) {
        String configurationPropertiesPrefix;
        String nestedConfigurationPropertiesPrefix = resolveConfigPropertiesValue(nestedType, beanContext);
        Class<?> supertype = nestedType.getSuperclass();
        while (supertype != null && supertype != Object.class && !(Modifier.isAbstract(supertype.getModifiers()))) {
            configurationPropertiesPrefix = resolveConfigPropertiesValue(supertype, beanContext);
            if (configurationPropertiesPrefix != null) {
                if (nestedConfigurationPropertiesPrefix == null || !nestedConfigurationPropertiesPrefix.equals(configurationPropertiesPrefix)) {
                    prefix.insert(0, configurationPropertiesPrefix + '.');
                }
            }
            supertype = supertype.getSuperclass();
        }
    }

    private String resolveConfigPropertiesValue(Class<?> supertype, BeanContext beanContext) {
        BeanDefinition<?> definition;
        if (supertype.equals(getBeanType()) || Modifier.isAbstract(supertype.getModifiers())) {
            definition = this;
        } else {
            definition = beanContext.findBeanDefinition(supertype).orElse(null);
        }
        if (definition != null) {
            Optional<String> opt = definition.getAnnotationNameByStereotype(ConfigurationReader.class);
            if (opt.isPresent()) {
                String annotationName = opt.get();
                Optional<String> val = definition.getValue(ConfigurationReader.class, String.class);
                return val.map(v -> {
                    Optional<String> prefix = definition.getValue(annotationName, "prefix", String.class);
                    if (prefix.isPresent()) {
                        String p = prefix.get();
                        if (StringUtils.isNotEmpty(p)) {
                            return p + '.' + v;
                        } else {
                            return v;
                        }
                    } else {
                        return v;
                    }
                }).orElse(null);
            }
        }
        return null;
    }



    private <B, X extends RuntimeException> B resolveBeanWithGenericsFromMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument, BeanResolver<B> beanResolver) throws X {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        try {
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            Class genericType;
            Class argumentType = argument.getType();
            if (argumentType.isArray()) {
                genericType = argumentType.getComponentType();
            } else {
                Map<String, Argument<?>> genericTypes = argument.getTypeVariables();
                if (genericTypes.size() != 1) {
                    throw new DependencyInjectionException(resolutionContext, argument, "Expected exactly 1 generic type for argument [" + argument + "] of method [" + injectionPoint.getName() + "]");
                } else {
                    genericType = genericTypes.values().iterator().next().getType();
                }
            }
            B bean = (B) beanResolver.resolveBean(genericType, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
        }
    }

    private <B> B resolveBeanWithGenericsFromConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, ConstructorInjectionPoint injectionPoint, Argument argument, BeanResolver<B> beanResolver) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this, argument);
        try {
            Class argumentType = argument.getType();
            Class genericType;
            if (argumentType.isArray()) {
                genericType = argumentType.getComponentType();
            } else {
                Map<String, Argument<?>> genericTypes = argument.getTypeVariables();
                if (genericTypes.size() != 1) {
                    throw new DependencyInjectionException(resolutionContext, argument, "Expected exactly 1 generic type argument to constructor");
                } else {
                    genericType = genericTypes.values().iterator().next().getType();
                }
            }
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            B bean = (B) beanResolver.resolveBean(genericType, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private <B> B resolveBeanWithGenericsForField(BeanResolutionContext resolutionContext, FieldInjectionPoint injectionPoint, BeanResolver<B> beanResolver) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, injectionPoint);

        try {
            Field field = injectionPoint.getField();
            Class<?> fieldType = field.getType();
            Optional<Class> genericType = fieldType.isArray() ? Optional.of(fieldType.getComponentType()) : GenericTypeUtils.resolveGenericTypeArgument(field);
            if (!genericType.isPresent()) {
                throw new DependencyInjectionException(resolutionContext, injectionPoint, "Expected exactly 1 generic type for field");
            }
            Qualifier qualifier = resolveQualifier(injectionPoint);
            B bean = (B) beanResolver.resolveBean(genericType.get(), qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, e);
        }
    }

    private boolean isConfigurationProperties() {
        return isConfigurationProperties;
    }

    private Qualifier resolveQualifier(FieldInjectionPoint injectionPoint) {
        Qualifier qualifier = null;
        Annotation ann = injectionPoint.getQualifier();
        if (ann == null) {
            ann = injectionPoint.getAnnotation(io.micronaut.context.annotation.Type.class);
        }
        if (ann != null) {
            qualifier = Qualifiers.byAnnotation(ann);
        }
        return qualifier;
    }

    private Qualifier resolveQualifier(BeanResolutionContext resolutionContext, Argument argument) {
        Qualifier qualifier = null;
        Annotation ann = argument.getQualifier();
        if (ann != null) {
            qualifier = Qualifiers.byAnnotation(ann);
        }

        if (qualifier == null) {
            io.micronaut.context.annotation.Type typeAnn = argument.getAnnotation(io.micronaut.context.annotation.Type.class);
            if (typeAnn != null) {
                qualifier = Qualifiers.byAnnotation(typeAnn);
            } else {
                Optional<Qualifier> optional = resolutionContext.get(javax.inject.Qualifier.class.getName(), Map.class)
                        .map(map -> (Qualifier) map.get(argument));
                qualifier = optional.orElse(null);
            }
        }
        return qualifier;
    }

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

    private class MethodKey {
        final String name;
        final Class[] argumentTypes;

        MethodKey(String name, Class[] argumentTypes) {
            this.name = name;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodKey methodKey = (MethodKey) o;

            if (!name.equals(methodKey.name)) return false;
            return Arrays.equals(argumentTypes, methodKey.argumentTypes);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(argumentTypes);
            return result;
        }
    }

    private interface BeanResolver<T> {
        T resolveBean(Class<T> beanType, Qualifier<T> qualifier);
    }
}
