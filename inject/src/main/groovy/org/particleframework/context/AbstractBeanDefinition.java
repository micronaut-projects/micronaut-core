package org.particleframework.context;

import org.particleframework.config.ConfigurationProperties;
import org.particleframework.config.PropertyResolver;
import org.particleframework.context.annotation.Provided;
import org.particleframework.context.annotation.Value;
import org.particleframework.context.event.BeanInitializedEventListener;
import org.particleframework.context.event.BeanInitializingEvent;
import org.particleframework.context.exceptions.BeanContextException;
import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.context.exceptions.DependencyInjectionException;
import org.particleframework.context.exceptions.NoSuchBeanException;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.inject.*;
import org.particleframework.inject.qualifiers.Qualifiers;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * <p>Default implementation of the {@link BeanDefinition} interface. This class is generally not used directly in user code.
 * Instead a build time tool does analysis of source code and dynamically produces subclasses of this class containing
 * information about the available injection points for a given class.</p>
 * <p>
 * <p>For technical reasons the class has to be marked as public, but is regarded as internal and should be used by compiler tools and plugins (such as AST transformation frameworks)</p>
 * <p>
 * <p>The {@link org.particleframework.inject.writer.BeanDefinitionWriter} class can be used to produce bean definitions at compile or runtime</p>
 *
 * @author Graeme Rocher
 * @see org.particleframework.inject.writer.BeanDefinitionWriter
 * @since 1.0
 */
@Internal
public abstract class AbstractBeanDefinition<T> implements InjectableBeanDefinition<T> {

    private static final LinkedHashMap<String, Class> EMPTY_MAP = new LinkedHashMap<>(0);
    private final Annotation scope;
    private final boolean singleton;
    private final Class<T> type;
    private final boolean provided;
    private boolean hasPreDestroyMethods = false;
    private boolean hasPostConstructMethods = false;
    private final ConstructorInjectionPoint<T> constructor;
    private final boolean isConfigurationProperties;
    private final Collection<Class> requiredComponents = new HashSet<>(3);
    protected final List<MethodInjectionPoint> methodInjectionPoints = new ArrayList<>(3);
    protected final List<FieldInjectionPoint> fieldInjectionPoints = new ArrayList<>(3);
    protected final List<MethodInjectionPoint> postConstructMethods = new ArrayList<>(1);
    protected final List<MethodInjectionPoint> preDestroyMethods = new ArrayList<>(1);
    protected final Map<MethodKey, ExecutableMethod<T, ?>> invocableMethodMap = new LinkedHashMap<>(3);

    /**
     * Constructs a bean definition that is produced from a method call on another type
     *
     * @param method     The method to call
     * @param arguments  The arguments
     * @param qualifiers The qualifiers
     */
    @Internal
    protected AbstractBeanDefinition(Method method,
                                     Map<String, Class> arguments,
                                     Map<String, Class> qualifiers,
                                     Map<String, List<Class>> genericTypes) {
        this.scope = AnnotationUtil.findAnnotationWithStereoType(Scope.class, method.getAnnotations());
        this.singleton = AnnotationUtil.findAnnotationWithStereoType(Singleton.class, method.getAnnotations()) != null;
        this.type = (Class<T>) method.getReturnType();
        this.provided = method.getAnnotation(Provided.class) != null;
        this.isConfigurationProperties = false;
        LinkedHashMap<String, Annotation> qualifierMap = null;
        if (qualifiers != null) {
            qualifierMap = new LinkedHashMap<>();
            populateQualifiersFromParameterAnnotations(arguments, qualifiers, qualifierMap, method.getParameterAnnotations());
        }
        this.constructor = new MethodConstructorInjectionPoint(
                this,
                method,
                Modifier.isPrivate(method.getModifiers()),
                arguments,
                qualifierMap,
                genericTypes);
    }

    /**
     * Constructs a bean definition that is produced from a method call on another type
     *
     * @param method The method to call
     */
    @Internal
    protected AbstractBeanDefinition(Method method) {
        this(method, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    @Internal
    protected AbstractBeanDefinition(Annotation scope,
                                     boolean singleton,
                                     Class<T> type,
                                     Constructor<T> constructor,
                                     Map<String, Class> arguments,
                                     Map<String, Class> qualifiers,
                                     Map<String, List<Class>> genericTypes) {
        this.scope = scope;
        this.singleton = singleton;
        this.type = type;
        this.provided = type.getAnnotation(Provided.class) != null;
        this.isConfigurationProperties = isConfigurationProperties(type);
        LinkedHashMap<String, Annotation> qualifierMap = null;
        if (qualifiers != null) {
            qualifierMap = new LinkedHashMap<>();
            populateQualifiersFromParameterAnnotations(arguments, qualifiers, qualifierMap, constructor.getParameterAnnotations());
        }
        this.constructor = new DefaultConstructorInjectionPoint<>(this, constructor, arguments, qualifierMap, genericTypes);
    }

    @Internal
    protected AbstractBeanDefinition(Annotation scope,
                                     boolean singleton,
                                     Class<T> type,
                                     Constructor<T> constructor) {
        this(scope, singleton, type, constructor, EMPTY_MAP, null, null);
    }

    protected AbstractBeanDefinition(Annotation scope,
                                     boolean singleton,
                                     Class<T> type,
                                     Constructor<T> constructor,
                                     Map<String, Class> arguments) {
        this(scope, singleton, type, constructor, arguments, null, null);
    }

    @Override
    public Optional<ExecutableMethod<T, ?>> findMethod(String name, Class... argumentTypes) {
        MethodKey methodKey = new MethodKey(name, argumentTypes);
        ExecutableMethod<T, ?> invocableMethod = invocableMethodMap.get(methodKey);
        if (invocableMethod != null) {
            return Optional.of(invocableMethod);
        } else {
            Optional<Method> method = ReflectionUtils.findMethod(type, name, argumentTypes);
            return method.map(theMethod -> {
                    ReflectionExecutableMethod<T, Object> reflectionMethod = new ReflectionExecutableMethod<>(this, theMethod);
                    invocableMethodMap.put(methodKey, reflectionMethod);
                    return reflectionMethod;
                }
            );
        }
    }

    @Override
    public Stream<ExecutableMethod<T, ?>> findPossibleMethods(String name) {
        if(invocableMethodMap.keySet().stream().anyMatch(methodKey -> methodKey.name.equals(name))) {
            return invocableMethodMap
                    .values()
                    .stream()
                    .filter((method) -> method.getMethodName().equals(name));
        }
        else {
            return ReflectionUtils.findMethodsByName(type, name)
                                    .map((method)-> new ReflectionExecutableMethod<>(this, method));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractBeanDefinition<?> that = (AbstractBeanDefinition<?>) o;

        return type != null ? type.equals(that.type) : that.type == null;
    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }

    @Override
    public boolean isProvided() {
        return provided;
    }

    @Override
    public boolean isSingleton() {
        return singleton;
    }

    @Override
    public Annotation getScope() {
        return this.scope;
    }

    @Override
    public Class<T> getType() {
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
        return getType().getName();
    }

    @Override
    public T inject(BeanContext context, T bean) {
        return (T) injectBean(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    @Override
    public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return (T) injectBean(resolutionContext, context, bean);
    }

    /**
     * Adds a new {@link ExecutableMethod}
     *
     * @param  executableMethod The method
     * @return The bean definition
     */
    protected AbstractBeanDefinition<T> addExecutableMethod(ExecutableMethod<T,?> executableMethod) {
        MethodKey key = new MethodKey(executableMethod.getMethodName(), executableMethod.getArgumentTypes());
        invocableMethodMap.put(key, executableMethod);
        return this;
    }

    /**
     * Adds an injection point for a field. Typically called by a dynamically generated subclass.
     *
     * @param field     The field
     * @param qualifier The qualifier, can be null
     * @return this component definition
     */
    protected AbstractBeanDefinition addInjectionPoint(Field field, Annotation qualifier, boolean requiresReflection) {
        requiredComponents.add(field.getType());
        fieldInjectionPoints.add(new DefaultFieldInjectionPoint(this, field, qualifier, requiresReflection));
        return this;
    }


    /**
     * Adds an injection point for a field. Typically called by a dynamically generated subclass.
     *
     * @param field The field
     * @return this component definition
     */
    protected AbstractBeanDefinition addInjectionPoint(Field field, boolean requiresReflection) {
        requiredComponents.add(field.getType());
        fieldInjectionPoints.add(new DefaultFieldInjectionPoint(this, field, null, requiresReflection));
        return this;
    }

    /**
     * Adds an injection point for a method. Typically called by a dynamically generated subclass.
     *
     * @param method    The method
     * @param arguments The arguments to the method
     * @return this component definition
     */
    protected AbstractBeanDefinition addInjectionPoint(
            Method method,
            Map<String, Class> arguments,
            Map<String, Class> qualifiers,
            Map<String, List<Class>> genericTypes,
            boolean requiresReflection) {
        Collection<MethodInjectionPoint> methodInjectionPoints = this.methodInjectionPoints;
        return addMethodInjectionPointInternal(null, method, arguments, qualifiers, genericTypes, requiresReflection, methodInjectionPoints);
    }

    /**
     * Adds an injection point for a setter and field to be set. Typically called by a dynamically generated subclass.
     *
     * @param setter The method
     * @return this component definition
     */
    protected AbstractBeanDefinition addInjectionPoint(
            Field field,
            Method setter,
            Annotation qualifier,
            List<Class> genericTypes,
            boolean requiresReflection) {

        Map<String, Annotation> qualifiers = null;
        Map<String, List<Class>> genericTypeMap = null;
        String fieldName = field.getName();
        if (qualifier != null) {
            qualifiers = Collections.singletonMap(fieldName, qualifier);
        }
        if (genericTypes != null) {
            genericTypeMap = Collections.singletonMap(fieldName, genericTypes);
        }
        DefaultMethodInjectionPoint methodInjectionPoint = new DefaultMethodInjectionPoint(
                this,
                field,
                setter,
                requiresReflection,
                Collections.singletonMap(fieldName, field.getType()),
                qualifiers,
                genericTypeMap);
        requiredComponents.add(field.getType());
        methodInjectionPoints.add(methodInjectionPoint);
        return this;
    }


    /**
     * Adds a post construct method definition
     *
     * @param method             The method
     * @param arguments          The arguments
     * @param qualifiers         The qualifiers
     * @param genericTypes       The generic types
     * @param requiresReflection Whether the method requires reflection
     * @return This bean definition
     */
    @Internal
    protected AbstractBeanDefinition addPostConstruct(Method method,
                                                      LinkedHashMap<String, Class> arguments,
                                                      Map<String, Class> qualifiers,
                                                      Map<String, List<Class>> genericTypes,
                                                      boolean requiresReflection) {
        return addMethodInjectionPointInternal(null, method, arguments, qualifiers, genericTypes, requiresReflection, postConstructMethods);
    }

    /**
     * Adds a pre destroy method definition
     *
     * @param method             The method
     * @param arguments          The arguments
     * @param qualifiers         The qualifiers
     * @param genericTypes       The generic types
     * @param requiresReflection Whether the method requires reflection
     * @return This bean definition
     */
    @Internal
    protected AbstractBeanDefinition addPreDestroy(Method method,
                                                   LinkedHashMap<String, Class> arguments,
                                                   Map<String, Class> qualifiers,
                                                   Map<String, List<Class>> genericTypes,
                                                   boolean requiresReflection) {
        return addMethodInjectionPointInternal(null, method, arguments, qualifiers, genericTypes, requiresReflection, preDestroyMethods);
    }

    protected Object injectBean(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;


        // Inject fields that require reflection
        injectBeanFields(resolutionContext, defaultContext, bean);

        // Inject methods that require reflection
        injectBeanMethods(resolutionContext, defaultContext, bean);

        return bean;
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
        for (MethodInjectionPoint methodInjectionPoint : methodInjectionPoints) {
            if (methodInjectionPoint.isPostConstructMethod() && methodInjectionPoint.requiresReflection()) {
                injectBeanMethod(resolutionContext, defaultContext, bean, resolutionContext.getPath(), methodInjectionPoint);
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
        for (MethodInjectionPoint methodInjectionPoint : methodInjectionPoints) {
            if (methodInjectionPoint.isPreDestroyMethod() && methodInjectionPoint.requiresReflection()) {
                injectBeanMethod(resolutionContext, defaultContext, bean, resolutionContext.getPath(), methodInjectionPoint);
            }
        }
        if (bean instanceof LifeCycle) {
            bean = ((LifeCycle) bean).stop();
        }
        return bean;
    }

    /**
     * Injects the methods of the bean that require reflection
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean being injected
     */
    protected void injectBeanMethods(BeanResolutionContext resolutionContext, DefaultBeanContext context, Object bean) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        for (MethodInjectionPoint methodInjectionPoint : methodInjectionPoints) {
            if (methodInjectionPoint.requiresReflection() && !methodInjectionPoint.isPostConstructMethod() && !methodInjectionPoint.isPreDestroyMethod()) {
                injectBeanMethod(resolutionContext, context, bean, path, methodInjectionPoint);
            }
        }
    }


    /**
     * Injects the fields of the bean that require reflection
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean being injected
     */
    protected void injectBeanFields(BeanResolutionContext resolutionContext, DefaultBeanContext context, Object bean) {
        for (FieldInjectionPoint fieldInjectionPoint : fieldInjectionPoints) {
            if (fieldInjectionPoint.requiresReflection()) {
                boolean isInject = AnnotationUtil.findAnnotationWithStereoType(Inject.class, fieldInjectionPoint.getField().getAnnotations()) != null;
                try {
                    Object value;
                    if(isInject) {
                        value = getBeanForField(resolutionContext, context, fieldInjectionPoint);
                    }
                    else {
                        value = getValueForField(resolutionContext, context, fieldInjectionPoint, null);
                    }
                    if(value != null) {
                        fieldInjectionPoint.set(bean, value);
                    }
                } catch (Throwable e) {
                    if(e instanceof BeanContextException) {
                        throw (BeanContextException)e;
                    }
                    else {
                        throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, "Error setting field value: " + e.getMessage());
                    }
                }
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
        return getValueForMethodArgument(resolutionContext, context, injectionPoint, argument, null);
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
    protected Object getValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Object defaultValue) throws Throwable {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        return getValueForMethodArgument(resolutionContext, context, injectionPoint, argument, defaultValue);
    }

    private Object getValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument, Object defaultValue) {
        if (context instanceof ApplicationContext) {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            path.pushMethodArgumentResolve(this, injectionPoint, argument);
            // can't use orElseThrow here due to compiler bug
            try {
                Value valAnn = argument.getAnnotation(Value.class);
                Class argumentType = argument.getType();

                if (isInnerConfiguration(argumentType)) {
                    return context.createBean(argumentType);
                } else {
                    String argumentName = argument.getName();
                    Class[] genericTypes = argument.getGenericTypes();
                    String valString = resolveValueString(argumentName, valAnn);
                    ApplicationContext applicationContext = (ApplicationContext) context;
                    Optional value = resolveValue(applicationContext, argumentType, valString, genericTypes);
                    if (!value.isPresent() && argumentType == Optional.class) {
                        return value;
                    } else {
                        return value.orElseGet(() -> {
                            if (valAnn == null && isConfigurationProperties) {
                                String cliOption = resolveCliOption(argumentName);
                                if(cliOption != null) {
                                    return resolveValue(applicationContext,
                                            argumentType,
                                            cliOption,
                                            genericTypes)
                                            .orElse(defaultValue);
                                }
                                else {
                                    return defaultValue;
                                }
                            } else if (!Iterable.class.isAssignableFrom(argumentType) && !Map.class.isAssignableFrom(argumentType)) {
                                throw new DependencyInjectionException(resolutionContext, argument, "Error resolving property value [" + valString + "]. Property doesn't exist");
                            } else {
                                return null;
                            }
                        });
                    }
                }
            } finally {
                path.pop();
            }
        } else {
            throw new DependencyInjectionException(resolutionContext, argument, "BeanContext must support property resolution");
        }
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
        if (argument.getAnnotation(Value.class) != null) {
            return getValueForMethodArgument(resolutionContext, context, injectionPoint, argument, null);
        } else {
            return getBeanForMethodArgument(resolutionContext, context, injectionPoint, argument);
        }
    }

    protected Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument) {
        Class argumentType = argument.getType();
        if (argumentType.isArray()) {
            Collection beansOfType = getBeansOfTypeForMethodArgument(resolutionContext, context, injectionPoint, argument);
            return beansOfType.toArray((Object[]) Array.newInstance(argumentType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(argumentType)) {
            Collection beansOfType = getBeansOfTypeForMethodArgument(resolutionContext, context, injectionPoint, argument);
            return coerceCollectionToCorrectType(resolutionContext, argument, argumentType, beansOfType);
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
                Qualifier qualifier = resolveQualifier(argument);
                Object bean = ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                path.pop();
                return bean;
            } catch (NoSuchBeanException e) {
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
        Argument argument = constructorInjectionPoint.getArguments()[argIndex];
        Class argumentType = argument.getType();
        if (argumentType.isArray()) {
            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
            return beansOfType.toArray((Object[]) Array.newInstance(argumentType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(argumentType)) {
            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
            return coerceCollectionToCorrectType(resolutionContext, argument, argumentType, beansOfType);
        } else if (Stream.class.isAssignableFrom(argumentType)) {
            return streamOfTypeForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
        } else if (Provider.class.isAssignableFrom(argumentType)) {
            return getBeanProviderForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
        } else if (Optional.class.isAssignableFrom(argumentType)) {
            return findBeanForConstructorArgument(resolutionContext, context, constructorInjectionPoint, argument);
        } else {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            path.pushConstructorResolve(this, argument);
            try {
                Object bean;
                if (argument.getAnnotation(Value.class) != null) {
                    if (context instanceof PropertyResolver) {
                        PropertyResolver propertyResolver = (PropertyResolver) context;
                        Value valAnn = (Value) argument.getAnnotation(Value.class);
                        if (valAnn == null) {
                            throw new IllegalStateException("Compiled getValueForMethodArgument(..) call present but @Value annotation missing.");
                        }
                        Optional value = propertyResolver.getProperty(valAnn.value(), argumentType);
                        // can't use orElseThrow here due to compiler bug
                        bean = value.orElseGet(() -> new DependencyInjectionException(resolutionContext, argument, "Error resolving property value [" + value + "]. Property doesn't exist"));
                    } else {
                        throw new DependencyInjectionException(resolutionContext, argument, "BeanContext must support property resolution");
                    }
                } else {
                    Qualifier qualifier = resolveQualifier(argument);
                    bean = ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                }
                path.pop();
                return bean;
            } catch (NoSuchBeanException | BeanInstantiationException e) {
                throw new DependencyInjectionException(resolutionContext, argument, e);
            }
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
        return getValueForField(resolutionContext, context, fieldIndex, null);
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
    protected Object getValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Object defaultValue) throws Throwable {
        FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
        return getValueForField(resolutionContext, context, injectionPoint, defaultValue);
    }

    @Internal
    protected Object getValueForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint, Object defaultValue) throws Throwable {
        if (context instanceof PropertyResolver) {
            Field field = injectionPoint.getField();
            Value valueAnn = field.getAnnotation(Value.class);
            Class<?> fieldType = field.getType();
            String valString = resolveValueString(injectionPoint.getName(), valueAnn);
            Optional value = resolveValue((ApplicationContext) context, fieldType, valString, GenericTypeUtils.resolveGenericTypeArguments(field));
            if (!value.isPresent() && fieldType == Optional.class) {
                return value;
            } else {
                if (isConfigurationProperties && valueAnn == null) {
                    return value.orElseGet(()->{
                        String cliOption = resolveCliOption(field.getName());
                        if(cliOption != null) {
                            return resolveValue((ApplicationContext) context,
                                    fieldType,
                                    cliOption,
                                    GenericTypeUtils.resolveGenericTypeArguments(field))
                                    .orElse(defaultValue);
                        }
                        else {
                            return defaultValue;
                        }
                    });
                } else {
                    return value.orElseThrow(() -> new DependencyInjectionException(resolutionContext, injectionPoint, "Error resolving field value [" + valString + "]. Property doesn't exist"));
                }
            }

        } else {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, "@Value requires a BeanContext that implements PropertyResolver");
        }
    }

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
        return resolveBeanWithGenericsForField(resolutionContext, context, injectionPoint, (beanType, qualifier) ->
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
        return resolveBeanWithGenericsForField(resolutionContext, context, injectionPoint, (beanType, qualifier) ->
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
        return resolveBeanWithGenericsForField(resolutionContext, context, injectionPoint, (beanType, qualifier) ->
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
        return resolveBeanWithGenericsForField(resolutionContext, context, injectionPoint, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
    }

    private Optional resolveValue(ApplicationContext context, Class type, String valString, Class... genericTypes) {

        int i = valString.indexOf(':');
        Object defaultValue = null;
        if (i > -1) {
            Optional converted = context.getConversionService().convert(valString.substring(i + 1, valString.length()), type);
            valString = valString.substring(0, i);
            defaultValue = converted.orElse(null);
        }
        Optional value;

        TypeVariable[] typeParameters = type.getTypeParameters();
        Map<String, Class> typeParameterMap = new HashMap<>();
        if (typeParameters.length == genericTypes.length) {
            for (int j = 0; j < typeParameters.length; j++) {
                TypeVariable typeParameter = typeParameters[j];
                typeParameterMap.put(typeParameter.getName(), genericTypes[j]);
            }
        }
        value = context.getProperty(valString, (Class<?>) type, typeParameterMap);

        if (defaultValue != null && !value.isPresent()) {
            value = Optional.of(defaultValue);
        }
        return value;
    }

    private String resolveValueString(String name, Value val) {
        String valString;
        if (val == null) {
            if (isConfigurationProperties) {
                String prefix = resolvePrefix();
                valString = prefix + "." + name;
            } else {
                throw new IllegalStateException("Compiled getValue*(..) call present but @Value annotation missing.");
            }
        } else {
            valString = val.value();
        }
        return valString;
    }

    private String resolveCliOption(String name) {
        Class type = getType();
        ConfigurationProperties configurationProperties = (ConfigurationProperties) type.getAnnotation(ConfigurationProperties.class);
        if(configurationProperties != null ) {
            String[] prefix = configurationProperties.cliPrefix();
            if(prefix.length == 1) {
                return prefix[0] + name;
            }
        }
        return null;
    }

    private boolean isInnerConfiguration(Class argumentType) {
        return isConfigurationProperties &&
                argumentType.getName().indexOf('$') > -1 &&
                Arrays.asList(getType().getClasses()).contains(argumentType) &&
                Modifier.isPublic(argumentType.getModifiers()) && Modifier.isStatic(argumentType.getModifiers());
    }

    private String resolvePrefix() {
        Class type = getType();
        ConfigurationProperties configurationProperties = (ConfigurationProperties) type.getAnnotation(ConfigurationProperties.class);
        String prefix = null;
        if (configurationProperties != null) {
            prefix = configurationProperties.value();
        } else {
            StringBuilder path = new StringBuilder();
            while (type != null) {
                String name = type.getName();
                int i = name.indexOf('$');
                if (i > -1) {
                    name = Introspector.decapitalize(name.substring(i, name.length()));
                    path.append('.').append(name);
                }

                type = type.getDeclaringClass();
                configurationProperties = (ConfigurationProperties) type.getAnnotation(ConfigurationProperties.class);
                if (configurationProperties != null) {
                    prefix = configurationProperties.value() + path;
                    break;
                }
            }
        }
        if (prefix == null) {
            throw new IllegalStateException("Unable to resolve configuration prefix. No @ConfigurationProperties root found");
        }
        return prefix;
    }

    private AbstractBeanDefinition addMethodInjectionPointInternal(
            Field field,
            Method method,
            Map<String, Class> arguments,
            Map<String, Class> qualifierTypes,
            Map<String, List<Class>> genericTypes,
            boolean requiresReflection,
            Collection<MethodInjectionPoint> methodInjectionPoints) {
        if (!hasPreDestroyMethods && method.getAnnotation(PreDestroy.class) != null) {
            hasPreDestroyMethods = true;
        }
        if (!hasPostConstructMethods && method.getAnnotation(PostConstruct.class) != null) {
            hasPostConstructMethods = true;
        }

        LinkedHashMap<String, Annotation> qualifiers = null;
        if (qualifierTypes != null && !qualifierTypes.isEmpty()) {
            qualifiers = new LinkedHashMap<>();
            if (field != null) {
                Map.Entry<String, Class> entry = qualifierTypes.entrySet().iterator().next();
                Annotation matchingAnnotation = findMatchingAnnotation(field.getAnnotations(), entry.getValue());
                if (matchingAnnotation != null) {
                    qualifiers.put(entry.getKey(), matchingAnnotation);
                } else {
                    qualifiers.put(entry.getKey(), null);
                }
            } else {
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                populateQualifiersFromParameterAnnotations(arguments, qualifierTypes, qualifiers, parameterAnnotations);
            }

        }
        DefaultMethodInjectionPoint methodInjectionPoint = new DefaultMethodInjectionPoint(this, method, requiresReflection, arguments, qualifiers, genericTypes);
        for (Argument argument : methodInjectionPoint.getArguments()) {
            requiredComponents.add(argument.getType());
        }
        methodInjectionPoints.add(methodInjectionPoint);
        return this;
    }

    private void populateQualifiersFromParameterAnnotations(Map<String, Class> argumentTypes, Map<String, Class> qualifierTypes, LinkedHashMap<String, Annotation> qualifiers, Annotation[][] parameterAnnotations) {
        int i = 0;
        for (Map.Entry<String, Class> entry : argumentTypes.entrySet()) {
            Annotation[] annotations = parameterAnnotations[i++];
            Annotation matchingAnnotation = null;
            if (annotations.length > 0) {
                Class annotationType = qualifierTypes.get(entry.getKey());
                if (annotationType != null) {
                    matchingAnnotation = findMatchingAnnotation(annotations, annotationType);
                }
            }

            if (matchingAnnotation != null) {
                qualifiers.put(entry.getKey(), matchingAnnotation);
            } else {
                qualifiers.put(entry.getKey(), null);
            }
        }
    }

    private Annotation findMatchingAnnotation(Annotation[] annotations, Class annotationType) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == annotationType) {
                return annotation;
            }
        }
        return null;
    }

    private <B, X extends RuntimeException> B resolveBeanWithGenericsFromMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodInjectionPoint injectionPoint, Argument argument, BeanResolver<B> beanResolver) throws X {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        try {
            Qualifier qualifier = resolveQualifier(argument);
            Class<B> genericType;
            Class argumentType = argument.getType();
            if (argumentType.isArray()) {
                genericType = argumentType.getComponentType();
            } else {
                Class[] genericTypes = argument.getGenericTypes();
                if (genericTypes.length != 1) {
                    throw new DependencyInjectionException(resolutionContext, argument, "Expected exactly 1 generic type for argument [" + argument + "] of method [" + injectionPoint.getName() + "]");
                } else {
                    genericType = genericTypes[0];
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
            Class[] genericTypes = argument.getGenericTypes();
            Class genericType;
            if (genericTypes.length != 1) {
                throw new DependencyInjectionException(resolutionContext, argument, "Expected exactly 1 generic type argument to constructor");
            } else {
                genericType = genericTypes[0];
            }
            Qualifier qualifier = resolveQualifier(argument);
            B bean = (B) beanResolver.resolveBean(genericType, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private <B> B resolveBeanWithGenericsForField(BeanResolutionContext resolutionContext, BeanContext context, FieldInjectionPoint injectionPoint, BeanResolver<B> beanResolver) {
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


    protected static Map createMap(Object[] values) {
        return CollectionUtils.createMap(values);
    }

    private boolean isConfigurationProperties(Class type) {
        while (type != null) {
            if (type.getAnnotation(ConfigurationProperties.class) != null) {
                return true;
            }
            type = type.getDeclaringClass();
        }
        return false;
    }

    private Qualifier resolveQualifier(FieldInjectionPoint injectionPoint) {
        Qualifier qualifier = null;
        Annotation ann = injectionPoint.getQualifier();
        if (ann != null) {
            qualifier = Qualifiers.qualify(ann);
        }
        return qualifier;
    }

    private Qualifier resolveQualifier(Argument argument) {
        Qualifier qualifier = null;
        Annotation ann = argument.getQualifier();
        if (ann != null) {
            qualifier = Qualifiers.qualify(ann);
        }
        return qualifier;
    }


    private Object coerceCollectionToCorrectType(BeanResolutionContext resolutionContext, Argument argument, Class collectionType, Collection beansOfType) {
        if (collectionType.isInstance(beansOfType)) {
            return beansOfType;
        } else {
            return CollectionUtils.convertCollection(collectionType, beansOfType).orElse(null);
        }
    }


    private void injectBeanMethod(BeanResolutionContext resolutionContext, DefaultBeanContext context, Object bean, BeanResolutionContext.Path path, MethodInjectionPoint methodInjectionPoint) {
        Argument[] methodArgumentTypes = methodInjectionPoint.getArguments();
        Object[] methodArgs = new Object[methodArgumentTypes.length];
        for (int i = 0; i < methodArgumentTypes.length; i++) {
            Argument argument = methodArgumentTypes[i];

            methodArgs[i] = getBeanForMethodArgument(resolutionContext, context, methodInjectionPoint, argument);
        }
        try {
            methodInjectionPoint.invoke(bean, methodArgs);
        } catch (Throwable e) {
            throw new BeanInstantiationException(this, e);
        }
    }

    private class MethodKey {
        final String name;
        final Class[] argumentTypes;

        public MethodKey(String name, Class[] argumentTypes) {
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

    protected interface MethodExecutor {
        Object invoke(Object target, Object...arguments);
    }
}
