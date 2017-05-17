package org.particleframework.context;

import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.context.exceptions.DependencyInjectionException;
import org.particleframework.context.exceptions.NoSuchBeanException;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.*;
import org.particleframework.core.annotation.Internal;

import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * Default implementation of the {@link BeanDefinition} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultBeanDefinition<T> implements BeanDefinition<T> {

    private static final LinkedHashMap<String, Class> EMPTY_MAP = new LinkedHashMap<>(0);
    private final Annotation scope;
    private final boolean singleton;
    private final Class<T> type;
    private final ConstructorInjectionPoint<T> constructor;
    private final Collection<Class> requiredComponents = new HashSet<>(3);
    protected final List<MethodInjectionPoint> methodInjectionPoints = new ArrayList<>(3);
    protected final List<FieldInjectionPoint> fieldInjectionPoints = new ArrayList<>(3);
    protected final List<MethodInjectionPoint> postConstructMethods = new ArrayList<>(1);
    protected final List<MethodInjectionPoint> preDestroyMethods = new ArrayList<>(1);


    protected DefaultBeanDefinition(Annotation scope,
                                    boolean singleton,
                                    Class<T> type,
                                    Constructor<T> constructor,
                                    LinkedHashMap<String, Class> arguments,
                                    Map<String, Class> qualifiers,
                                    Map<String, List<Class>> genericTypes) {
        this.scope = scope;
        this.singleton = singleton;
        this.type = type;
        LinkedHashMap<String, Annotation> qualifierMap = null;
        if(qualifiers != null) {
            qualifierMap = new LinkedHashMap<>();
            populateQualifiersFromParameterAnnotations(arguments, qualifiers, qualifierMap, constructor.getParameterAnnotations());
        }
        this.constructor = new DefaultConstructorInjectionPoint<>(this,constructor, arguments, qualifierMap, genericTypes);
    }

    protected DefaultBeanDefinition(Annotation scope,
                                    boolean singleton,
                                    Class<T> type,
                                    Constructor<T> constructor) {
        this(scope, singleton, type, constructor, EMPTY_MAP, null, null);
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
    public Iterable<Class> getRequiredComponents() {
        return Collections.unmodifiableCollection(requiredComponents);
    }

    @Override
    public Iterable<MethodInjectionPoint> getInjectedMethods() {
        return Collections.unmodifiableCollection(methodInjectionPoints);
    }

    @Override
    public Iterable<FieldInjectionPoint> getInjectedFields() {
        return Collections.unmodifiableCollection(fieldInjectionPoints);
    }

    @Override
    public Iterable<MethodInjectionPoint> getPostConstructMethods() {
        return Collections.unmodifiableCollection(postConstructMethods);
    }

    @Override
    public Iterable<MethodInjectionPoint> getPreDestroyMethods() {
        return Collections.unmodifiableCollection(preDestroyMethods);
    }

    @Override
    public String getName() {
        return getType().getName();
    }

    public T inject(BeanContext context, T bean) {
        return (T) injectBean(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    protected Object injectBean(BeanContext context, Object bean) {
        return injectBean(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    /**
     * Adds an injection point for a field. Typically called by a dynamically generated subclass.
     *
     * @param field The field
     * @param qualifier The qualifier, can be null
     * @return this component definition
     */
    protected DefaultBeanDefinition addInjectionPoint(Field field, Annotation qualifier, boolean requiresReflection) {
        requiredComponents.add(field.getType());
        fieldInjectionPoints.add(new DefaultFieldInjectionPoint(this,field, qualifier, requiresReflection));
        return this;
    }


    /**
     * Adds an injection point for a field. Typically called by a dynamically generated subclass.
     *
     * @param field The field
     * @return this component definition
     */
    protected DefaultBeanDefinition addInjectionPoint(Field field, boolean requiresReflection) {
        requiredComponents.add(field.getType());
        fieldInjectionPoints.add(new DefaultFieldInjectionPoint(this,field, null, requiresReflection));
        return this;
    }

    /**
     * Adds an injection point for a method. Typically called by a dynamically generated subclass.
     *
     * @param method The method
     * @param  arguments The arguments to the method
     * @return this component definition
     */
    protected DefaultBeanDefinition addInjectionPoint(
                                                Method method,
                                                LinkedHashMap<String, Class> arguments,
                                                Map<String, Class> qualifiers,
                                                Map<String, List<Class>> genericTypes,
                                                boolean requiresReflection) {
        Collection<MethodInjectionPoint> methodInjectionPoints = this.methodInjectionPoints;
        return addMethodInjectionPointInternal(null, method, arguments, qualifiers, genericTypes,requiresReflection, methodInjectionPoints);
    }

    /**
     * Adds an injection point for a method. Typically called by a dynamically generated subclass.
     *
     * @param setter The method
     * @param  arguments The arguments to the method
     * @return this component definition
     */
    protected DefaultBeanDefinition addInjectionPoint(
            Field field,
            Method setter,
            LinkedHashMap<String, Class> arguments,
            Map<String, Class> qualifiers,
            Map<String, List<Class>> genericTypes,
            boolean requiresReflection) {
        Collection<MethodInjectionPoint> methodInjectionPoints = this.methodInjectionPoints;
        return addMethodInjectionPointInternal(field, setter, arguments, qualifiers, genericTypes, requiresReflection, methodInjectionPoints);
    }


    protected DefaultBeanDefinition addPostConstruct(Method method,
                                                     LinkedHashMap<String, Class> arguments,
                                                     Map<String, Class> qualifiers,
                                                     Map<String, List<Class>> genericTypes,
                                                     boolean requiresReflection) {
        return addMethodInjectionPointInternal(null, method, arguments, qualifiers, genericTypes, requiresReflection, postConstructMethods);
    }

    protected DefaultBeanDefinition addPreDestroy(Method method,
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

    protected void injectBeanMethods(BeanResolutionContext resolutionContext, DefaultBeanContext context, Object bean) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        for (MethodInjectionPoint methodInjectionPoint : methodInjectionPoints) {
            if (methodInjectionPoint.requiresReflection()) {
                Argument[] methodArgumentTypes = methodInjectionPoint.getArguments();
                Object[] methodArgs = new Object[methodArgumentTypes.length];
                for (int i = 0; i < methodArgumentTypes.length; i++) {
                    Argument argument = methodArgumentTypes[i];
                    path.pushMethodArgumentResolve(this, methodInjectionPoint, argument);
                    Class argumentType = argument.getType();
                    Qualifier qualifier = resolveQualifier(argument);

                    if(argumentType.isArray()) {
                        Class arrayType = argumentType.getComponentType();
                        methodArgs[i] = collectionToArray(arrayType, context.getBeansOfType(resolutionContext, arrayType));
                    }
                    else if (Iterable.class.isAssignableFrom(argumentType)) {
                        Class[] genericTypes = argument.getGenericTypes();
                        if(genericTypes != null && genericTypes.length == 1) {
                            Class genericType = genericTypes[0];
                            Collection beansOfType = context.getBeansOfType(resolutionContext, genericType);
                            try {
                                methodArgs[i] = coerceToType(beansOfType, argumentType);
                            } catch (Exception e) {
                                throw new DependencyInjectionException(resolutionContext, methodInjectionPoint, argument, "Cannot convert collection to target iterable type: " + argumentType.getName());
                            }
                        }
                        else {
                            throw new DependencyInjectionException(resolutionContext, methodInjectionPoint, argument, "Iterable missing generic argument types");
                        }
                    } else if(Provider.class.isAssignableFrom(argumentType)) {
                        Class[] genericTypes = argument.getGenericTypes();
                        if(genericTypes != null && genericTypes.length == 1) {
                            Class genericType = genericTypes[0];
                            if (genericType != null) {
                                methodArgs[i] = context.getBeanProvider(resolutionContext, genericType);
                                path.pop();
                            } else {
                                throw new DependencyInjectionException(resolutionContext, methodInjectionPoint, argument, "Cannot inject Iterable with missing generic type arguments for field");
                            }
                        }
                        else {
                            throw new DependencyInjectionException(resolutionContext, methodInjectionPoint, argument, "Provider missing generic argument types");
                        }
                    } else {
                        methodArgs[i] = context.getBean(resolutionContext, argumentType, qualifier);
                    }
                    path.pop();
                }
                methodInjectionPoint.invoke(bean, methodArgs);
            }
        }
    }

    protected void injectBeanFields(BeanResolutionContext resolutionContext , DefaultBeanContext defaultContext, Object bean) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        for (FieldInjectionPoint fieldInjectionPoint : fieldInjectionPoints) {
            if(fieldInjectionPoint.requiresReflection()) {
                Field field = fieldInjectionPoint.getField();
                Class beanType = fieldInjectionPoint.getType();

                Class genericType = GenericTypeUtils.resolveGenericTypeArgument(field);
                path.pushFieldResolve(this, fieldInjectionPoint);

                Object value;
                if (beanType.isArray()) {
                    Class arrayType = beanType.getComponentType();
                    Collection beans = defaultContext.getBeansOfType(resolutionContext,arrayType);
                    value = collectionToArray(arrayType, beans);
                } else if (Iterable.class.isAssignableFrom(beanType)) {
                    if (genericType != null) {
                        Collection beans = defaultContext.getBeansOfType(resolutionContext, genericType);
                        if (beanType.isInstance(beans)) {
                            value = beans;
                        } else {
                            try {
                                value = coerceToType(beans, beanType);
                            } catch (Throwable e) {
                                throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, e);
                            }
                        }
                    } else {
                        throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, "Cannot inject Iterable with missing generic type arguments for field");
                    }
                } else if(Provider.class.isAssignableFrom(beanType)) {
                    if (genericType != null) {
                        value = defaultContext.getBeanProvider(resolutionContext, genericType);
                        path.pop();
                    } else {
                        throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, "Cannot inject Iterable with missing generic type arguments for field");
                    }
                } else {
                    Object beanValue;
                    try {
                        Qualifier qualifier = resolveQualifier(fieldInjectionPoint);
                        beanValue = defaultContext.getBean(resolutionContext, beanType, qualifier);
                    } catch (NoSuchBeanException e) {
                        throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, e);
                    }
                    value = beanValue;
                }
                path.pop();
                fieldInjectionPoint.set(bean, value);
            }
        }
    }

    private Object[] collectionToArray(Class arrayType, Collection beans) {
        Object[] newArray = (Object[]) Array.newInstance(arrayType, beans.size());
        int i = 0;
        for (Object foundBean : beans) {
            newArray[i++] = foundBean;
        }
        return newArray;
    }

    /**
     * Obtains a bean definition for the method at the given index and the argument at the given index
     *
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @param methodIndex The method index
     * @param argIndex The argument index
     * @return The resolved bean
     */
    @Internal
    protected Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        try {
            Qualifier qualifier = resolveQualifier(argument);
            Object bean = ((DefaultBeanContext)context).getBean(resolutionContext, argument.getType(), qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
        }
    }


    /**
     * Obtains all bean definitions for the method at the given index and the argument at the given index
     *
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @param methodIndex The method index
     * @param argIndex The argument index
     * @return The resolved bean
     */
    @Internal
    protected Iterable getBeansOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        try {
            Qualifier qualifier = resolveQualifier(argument);
            Class[] genericTypes = argument.getGenericTypes();
            Class genericType;
            if(genericTypes.length != 1) {
                throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, "Expected exactly 1 generic type argument");
            }
            else {
                genericType = genericTypes[0];
            }
            Iterable beansOfType = ((DefaultBeanContext) context).getBeansOfType(resolutionContext, genericType, qualifier);
            path.pop();
            return beansOfType;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
        }
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     *
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @param argIndex The argument index
     * @return The resolved bean
     */
    @Internal
    protected Iterable getBeansOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex) {
        ConstructorInjectionPoint<T> constructorInjectionPoint = getConstructor();
        Argument argument = constructorInjectionPoint.getArguments()[argIndex];
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this,  argument);
        try {
            Qualifier qualifier = resolveQualifier(argument);
            Class[] genericTypes = argument.getGenericTypes();
            Class genericType;
            if(genericTypes.length != 1) {
                throw new DependencyInjectionException(resolutionContext, argument, "Expected exactly 1 generic type argument to constructor");
            }
            else {
                genericType = genericTypes[0];
            }
            Iterable beansOfType = ((DefaultBeanContext) context).getBeansOfType(resolutionContext, genericType, qualifier);
            path.pop();
            return beansOfType;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, argument , e);
        }
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     *
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @return The resolved bean
     */
    @Internal
    protected Object getBeanForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, injectionPoint);
        Class beanType = injectionPoint.getType();

        try {
            Qualifier qualifier = resolveQualifier(injectionPoint);
            Object bean = ((DefaultBeanContext)context).getBean(resolutionContext, beanType, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, e);
        }

    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     *
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @return The resolved bean
     */
    @Internal
    protected Object getBeanProviderForField(BeanResolutionContext resolutionContext, BeanContext context, Class providedType, int fieldIndex) {
        FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, injectionPoint);

        try {
            Qualifier qualifier = resolveQualifier(injectionPoint);
            Object bean = ((DefaultBeanContext)context).getBeanProvider(resolutionContext, providedType, qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, e);
        }

    }

    private Qualifier resolveQualifier(FieldInjectionPoint injectionPoint) {
        Qualifier qualifier = null;
        Annotation ann = injectionPoint.getQualifier();
        if(ann != null) {
            qualifier = Qualifiers.qualify(ann);
        }
        return qualifier;
    }

    private Qualifier resolveQualifier(Argument argument) {
        Qualifier qualifier = null;
        Annotation ann = argument.getQualifier();
        if(ann != null) {
            qualifier = Qualifiers.qualify(ann);
        }
        return qualifier;
    }



    /**
     * Obtains a bean definition for a constructor at the given index
     *
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @param argIndex The argument index
     * @return The resolved bean
     */
    @Internal
    protected Object getBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex) {
        ConstructorInjectionPoint<T> constructorInjectionPoint = getConstructor();
        Argument argument = constructorInjectionPoint.getArguments()[argIndex];
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this,  argument);
        try {
            Qualifier qualifier = resolveQualifier(argument);
            Object bean = ((DefaultBeanContext)context).getBean(resolutionContext, argument.getType(), qualifier);
            path.pop();
            return bean;
        } catch (NoSuchBeanException | BeanInstantiationException e) {
            throw new DependencyInjectionException(resolutionContext, argument , e);
        }
    }




    /**
     * Obtains a bean provider for the method at the given index and the argument at the given index
     *
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @param methodIndex The method index
     * @param argIndex The argument index
     * @return The resolved bean
     */
    @Internal
    protected Provider getBeanProviderForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, Class providedType, int methodIndex, int argIndex) {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        try {
            Qualifier qualifier = resolveQualifier(argument);
            Provider beanProvider = ((DefaultBeanContext)context).getBeanProvider(resolutionContext, providedType,qualifier);
            path.pop();
            return beanProvider;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
        }

    }

    /**
     * Obtains a bean provider for a constructor at the given index
     *
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context The context
     * @param argIndex The argument index
     * @return The resolved bean
     */
    @Internal
    protected Provider getBeanProviderForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, Class providedType, int argIndex) {
        Argument argument = getConstructor().getArguments()[argIndex];
        BeanResolutionContext.Path path = resolutionContext.getPath();
        path.pushConstructorResolve(this,  argument);
        try {
            Class type = argument.getType();
            Qualifier qualifier = resolveQualifier(argument);
            Provider beanProvider  = ((DefaultBeanContext)context).getBeanProvider(resolutionContext, providedType, qualifier);
            path.pop();
            return beanProvider;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, argument , e);
        }
    }

    private Object coerceToType(Collection beans, Class<? extends Iterable> componentType) throws Exception {
        if (componentType == Set.class) {
            return new HashSet<>(beans);
        } else if (componentType == Queue.class) {
            return new LinkedList<>(beans);
        } else if (componentType == List.class) {
            return new ArrayList<>(beans);
        } else if (!componentType.isInterface()) {
            Constructor<? extends Iterable> constructor = componentType.getConstructor(Collection.class);
            return constructor.newInstance(beans);
        } else {
            return null;
        }
    }

    private DefaultBeanDefinition addMethodInjectionPointInternal(
            Field field,
            Method method,
            LinkedHashMap<String, Class> arguments,
            Map<String, Class> qualifierTypes,
            Map<String, List<Class>> genericTypes,
            boolean requiresReflection,
            Collection<MethodInjectionPoint> methodInjectionPoints) {
        LinkedHashMap<String, Annotation> qualifiers = null;
        if(qualifierTypes != null && !qualifierTypes.isEmpty()) {
            qualifiers = new LinkedHashMap<>();
            if(field != null) {
                Map.Entry<String, Class> entry = qualifierTypes.entrySet().iterator().next();
                Annotation matchingAnnotation = findMatchingAnnotation(field.getAnnotations(), entry.getValue());
                if(matchingAnnotation != null) {
                    qualifiers.put(entry.getKey(), matchingAnnotation);
                }
                else {
                    qualifiers.put(entry.getKey(), null);
                }
            }
            else {
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                populateQualifiersFromParameterAnnotations(arguments, qualifierTypes, qualifiers, parameterAnnotations);
            }

        }
        DefaultMethodInjectionPoint methodInjectionPoint = new DefaultMethodInjectionPoint(this, method,requiresReflection, arguments, qualifiers, genericTypes);
        for (Argument argument : methodInjectionPoint.getArguments()) {
            requiredComponents.add(argument.getType());
        }
        methodInjectionPoints.add(methodInjectionPoint);
        return this;
    }

    private void populateQualifiersFromParameterAnnotations(LinkedHashMap<String, Class> argumentTypes, Map<String, Class> qualifierTypes, LinkedHashMap<String, Annotation> qualifiers, Annotation[][] parameterAnnotations) {
        int i = 0;
        for (Map.Entry<String, Class> entry : argumentTypes.entrySet()) {
            Annotation[] annotations = parameterAnnotations[i++];
            Annotation matchingAnnotation = null;
            if(annotations.length>0) {
                Class annotationType = qualifierTypes.get(entry.getKey());
                if(annotationType != null) {
                    matchingAnnotation = findMatchingAnnotation(annotations, annotationType);
                }
            }

            if(matchingAnnotation != null) {
                qualifiers.put(entry.getKey(), matchingAnnotation);
            }
            else {
                qualifiers.put(entry.getKey(), null);
            }
        }
    }

    private Annotation findMatchingAnnotation(Annotation[] annotations, Class annotationType) {
        for (Annotation annotation : annotations) {
            if(annotation.annotationType() == annotationType) {
                return annotation;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultBeanDefinition<?> that = (DefaultBeanDefinition<?>) o;

        return type != null ? type.equals(that.type) : that.type == null;
    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }
}
