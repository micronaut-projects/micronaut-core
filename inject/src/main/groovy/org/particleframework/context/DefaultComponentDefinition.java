package org.particleframework.context;

import org.particleframework.context.exceptions.DependencyInjectionException;
import org.particleframework.context.exceptions.NoSuchBeanException;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.*;
import org.particleframework.core.annotation.Internal;

import javax.inject.Provider;
import javax.inject.Scope;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * Default implementation of the {@link ComponentDefinition} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultComponentDefinition<T> implements ComponentDefinition<T> {

    private final Annotation scope;
    private final boolean singleton;
    private final Class<T> type;
    private final ConstructorInjectionPoint<T> constructor;
    private final Collection<Class> requiredComponents = new HashSet<>(3);
    protected final List<MethodInjectionPoint> methodInjectionPoints = new ArrayList<>(3);
    protected final List<FieldInjectionPoint> fieldInjectionPoints = new ArrayList<>(3);
    protected final List<MethodInjectionPoint> postConstructMethods = new ArrayList<>(1);
    protected final List<MethodInjectionPoint> preDestroyMethods = new ArrayList<>(1);


    protected DefaultComponentDefinition(  Annotation scope,
                                           boolean singleton,
                                           Class<T> type,
                                           Constructor<T> constructor,
                                           LinkedHashMap<String, Class> arguments) {
        this.scope = scope;
        this.singleton = singleton;
        this.type = type;
        this.constructor = new DefaultConstructorInjectionPoint<>(this,constructor, arguments);
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
    public Iterable<MethodInjectionPoint> getRequiredProperties() {
        return Collections.unmodifiableCollection(methodInjectionPoints);
    }

    @Override
    public Iterable<FieldInjectionPoint> getRequiredFields() {
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

    public T inject(Context context, T bean) {
        return (T) injectBean(new DefaultComponentResolutionContext(context, this), context, bean, false);
    }

    protected Object injectBean(Context context, Object bean) {
        return injectBean(new DefaultComponentResolutionContext(context, this), context, bean, true);
    }

    /**
     * Adds an injection point for a field. Typically called by a dynamically generated subclass.
     *
     * @param field The field
     * @return this component definition
     */
    protected DefaultComponentDefinition addInjectionPoint(Field field) {
        requiredComponents.add(field.getType());
        fieldInjectionPoints.add(new DefaultFieldInjectionPoint(this,field));
        return this;
    }

    /**
     * Adds an injection point for a method. Typically called by a dynamically generated subclass.
     *
     * @param method The method
     * @return this component definition
     */
    protected DefaultComponentDefinition addInjectionPoint(Method method, LinkedHashMap<String, Class> arguments) {
        Collection<MethodInjectionPoint> methodInjectionPoints = this.methodInjectionPoints;
        return addMethodInjectionPointInternal(method, arguments, methodInjectionPoints);
    }

    protected DefaultComponentDefinition addPostConstruct(Method method, LinkedHashMap<String, Class> arguments) {
        return addMethodInjectionPointInternal(method, arguments, postConstructMethods);
    }

    protected DefaultComponentDefinition addPreDestroy(Method method, LinkedHashMap<String, Class> arguments) {
        return addMethodInjectionPointInternal(method, arguments, preDestroyMethods);
    }

    protected Object injectBean(ComponentResolutionContext resolutionContext, Context context, Object bean, boolean onlyNonPublic) {
        DefaultContext defaultContext = (DefaultContext) context;
        ComponentResolutionContext.Path path = resolutionContext.getPath();
        for (FieldInjectionPoint fieldInjectionPoint : getRequiredFields()) {
            Field field = fieldInjectionPoint.getField();
            if(Modifier.isPrivate(field.getModifiers())) {
                Class componentType = fieldInjectionPoint.getType();

                Class genericType = GenericTypeUtils.resolveGenericTypeArgument(field);

                Object value;
                if (componentType.isArray()) {
                    Class arrayType = componentType.getComponentType();
                    path.pushFieldResolve(this, fieldInjectionPoint);
                    Collection beans = (Collection)defaultContext.getBeansOfType(resolutionContext,arrayType);
                    Object[] newArray = (Object[]) Array.newInstance(arrayType, beans.size());
                    int i = 0;
                    for (Object foundBean : beans) {
                        newArray[i++] = foundBean;
                    }
                    value = newArray;
                } else if (Iterable.class.isAssignableFrom(componentType)) {
                    if (genericType != null) {
                        path.pushFieldResolve(this, fieldInjectionPoint);
                        Collection beans = (Collection)defaultContext.getBeansOfType(resolutionContext, genericType);
                        if (componentType.isInstance(beans)) {
                            value = beans;
                        } else {
                            try {
                                value = coerceToType(beans, componentType);
                            } catch (Exception e) {
                                throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, e);
                            }
                        }
                    } else {
                        throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, "Cannot inject Iterable with missing generic type arguments for field");
                    }
                } else if(Provider.class.isAssignableFrom(componentType)) {
                    if (genericType != null) {
                        path.pushFieldResolve(this, fieldInjectionPoint);
                        value = defaultContext.getBeanProvider(resolutionContext, genericType);
                        path.pop();
                    } else {
                        throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, "Cannot inject Iterable with missing generic type arguments for field");
                    }
                } else {
                    Object beanValue;
                    try {
                        path.pushFieldResolve(this, fieldInjectionPoint);
                        beanValue = defaultContext.getBean(resolutionContext, componentType);
                    } catch (NoSuchBeanException e) {
                        throw new DependencyInjectionException(resolutionContext, fieldInjectionPoint, e);
                    }
                    value = beanValue;
                }
                path.pop();
                fieldInjectionPoint.set(bean, value);
            }
        }


        for (MethodInjectionPoint methodInjectionPoint : getRequiredProperties()) {
            if (onlyNonPublic && java.lang.reflect.Modifier.isPrivate(methodInjectionPoint.getMethod().getModifiers())) {
                Argument[] methodArgumentTypes = methodInjectionPoint.getArguments();
                Object[] methodArgs = new Object[methodArgumentTypes.length];
                for (int i = 0; i < methodArgumentTypes.length; i++) {
                    Argument argument = methodArgumentTypes[i];
                    path.pushMethodArgumentResolve(this, methodInjectionPoint, argument);
                    Class argumentType = argument.getType();
                    if (argumentType.isArray()) {
                        methodArgs[i] = defaultContext.getBeansOfType(resolutionContext, argumentType.getComponentType());
                    } else {
                        methodArgs[i] = defaultContext.getBean(resolutionContext, argumentType);
                    }
                    path.pop();
                }
                methodInjectionPoint.invoke(bean, methodArgs);
            }
        }

        return bean;
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
    protected Object getBeanForMethodArgument(ComponentResolutionContext resolutionContext, Context context, int methodIndex, int argIndex) {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        ComponentResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        try {
            Object bean = ((DefaultContext)context).getBean(resolutionContext, argument.getType());
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, argument, e);
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
    protected Object getBeanForField(ComponentResolutionContext resolutionContext, Context context, int fieldIndex) {
        FieldInjectionPoint injectionPoint = fieldInjectionPoints.get(fieldIndex);
        ComponentResolutionContext.Path path = resolutionContext.getPath();
        path.pushFieldResolve(this, injectionPoint);
        Class beanType = injectionPoint.getType();

        try {
            Object bean = ((DefaultContext)context).getBean(resolutionContext, beanType);
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException(resolutionContext, injectionPoint, e);
        }

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
    protected Object getBeanForConstructorArgument(ComponentResolutionContext resolutionContext, Context context, int argIndex) {
        Argument argument = getConstructor().getArguments()[argIndex];
        ComponentResolutionContext.Path path = resolutionContext.getPath();
        path.pushContructorResolve(this,  argument);
        try {
            Object bean = ((DefaultContext)context).getBean(resolutionContext, argument.getType());
            path.pop();
            return bean;
        } catch (NoSuchBeanException e) {
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
    protected Provider getBeanProviderForMethodArgument(ComponentResolutionContext resolutionContext, Context context, Class providedType, int methodIndex, int argIndex) {
        MethodInjectionPoint injectionPoint = methodInjectionPoints.get(methodIndex);
        Argument argument = injectionPoint.getArguments()[argIndex];
        ComponentResolutionContext.Path path = resolutionContext.getPath();
        path.pushMethodArgumentResolve(this, injectionPoint, argument);
        try {
            Provider beanProvider = ((DefaultContext)context).getBeanProvider(resolutionContext, providedType);
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
    protected Provider getBeanProviderForConstructorArgument(ComponentResolutionContext resolutionContext, Context context, Class providedType, int argIndex) {
        Argument argument = getConstructor().getArguments()[argIndex];
        ComponentResolutionContext.Path path = resolutionContext.getPath();
        path.pushContructorResolve(this,  argument);
        try {
            Class type = argument.getType();
            Provider beanProvider  = ((DefaultContext)context).getBeanProvider(resolutionContext, providedType);
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
        } else if (!componentType.isInterface()) {
            Constructor<? extends Iterable> constructor = componentType.getConstructor(Collection.class);
            return constructor.newInstance(beans);
        } else {
            return null;
        }
    }
    private DefaultComponentDefinition addMethodInjectionPointInternal(Method method, LinkedHashMap<String, Class> arguments, Collection<MethodInjectionPoint> methodInjectionPoints) {
        DefaultMethodInjectionPoint methodInjectionPoint = new DefaultMethodInjectionPoint(this, method, arguments);
        for (Argument argument : methodInjectionPoint.getArguments()) {
            requiredComponents.add(argument.getType());
        }
        methodInjectionPoints.add(methodInjectionPoint);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultComponentDefinition<?> that = (DefaultComponentDefinition<?>) o;

        return type != null ? type.equals(that.type) : that.type == null;
    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }
}
