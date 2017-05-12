package org.particleframework.context;

import org.particleframework.context.exceptions.DependencyInjectionException;
import org.particleframework.context.exceptions.NoSuchBeanException;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.ComponentDefinition;
import org.particleframework.inject.ConstructorInjectionPoint;
import org.particleframework.inject.FieldInjectionPoint;
import org.particleframework.inject.MethodInjectionPoint;
import org.particleframework.core.annotation.Internal;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Default implementation of the {@link ComponentDefinition} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultComponentDefinition<T> implements ComponentDefinition<T> {

    private final Class<T> type;
    private final ConstructorInjectionPoint<T> constructor;
    private final Collection<Class> requiredComponents = new HashSet<>(3);
    private final Collection<MethodInjectionPoint> methodInjectionPoints = new ArrayList<>(3);
    private final Collection<FieldInjectionPoint> fieldInjectionPoints = new ArrayList<>(3);
    private final Collection<MethodInjectionPoint> postConstructMethods = new ArrayList<>(1);
    private final Collection<MethodInjectionPoint> preDestroyMethods = new ArrayList<>(1);


    protected DefaultComponentDefinition(  Class<T> type,
                                            Constructor<T> constructor) {
        this.type = type;
        this.constructor = new DefaultConstructorInjectionPoint<>(constructor);
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
    public T inject(Context context, T bean) {
        return injectBean(context, bean, false);
    }

    protected DefaultComponentDefinition addInjectionPoint(Method method) {
        Collection<MethodInjectionPoint> methodInjectionPoints = this.methodInjectionPoints;
        return addMethodInjectionPointInternal(method, methodInjectionPoints);
    }

    protected DefaultComponentDefinition addInjectionPoint(Field field) {
        requiredComponents.add(field.getType());
        fieldInjectionPoints.add(new DefaultFieldInjectionPoint(field));
        return this;
    }

    protected DefaultComponentDefinition addPostConstruct(Method method) {
        return addMethodInjectionPointInternal(method, postConstructMethods);
    }

    protected DefaultComponentDefinition addPreDestroy(Method method) {
        return addMethodInjectionPointInternal(method, preDestroyMethods);
    }

    protected T injectBean(Context context, T bean, boolean onlyNonPublic) {
        for (FieldInjectionPoint fieldInjectionPoint : getRequiredFields()) {
            Class componentType = fieldInjectionPoint.getComponentType();
            Field field = fieldInjectionPoint.getField();
            Class genericType = GenericTypeUtils.resolveGenericTypeArgument(field);

            Object value;
            if (componentType.isArray()) {
                Class arrayType = componentType.getComponentType();
                Collection beans = (Collection)context.getBeansOfType(arrayType);
                Object[] newArray = (Object[]) Array.newInstance(arrayType, beans.size());
                int i = 0;
                for (Object foundBean : beans) {
                    newArray[i++] = foundBean;
                }
                value = newArray;
            } else if (Iterable.class.isAssignableFrom(componentType)) {
                if (genericType != null) {
                    Collection beans = (Collection)context.getBeansOfType(genericType);
                    if (componentType.isInstance(beans)) {
                        value = beans;
                    } else {
                        try {
                            value = coerceToType(beans, componentType);
                        } catch (Exception e) {
                            throw new DependencyInjectionException("Could not coerce beans to concrete type for field: " + field, e);
                        }
                    }
                } else {
                    throw new DependencyInjectionException("Cannot inject Iterable with missing generic type arguments for field: " + field);
                }
            } else {
                Object beanValue;
                try {
                    beanValue = context.getBean(componentType);
                } catch (NoSuchBeanException e) {
                    throw new DependencyInjectionException("Failed to inject value for field [" + field.getName() + "] of class: " + field.getDeclaringClass().getName(), e);
                }
                value = beanValue;
            }
            fieldInjectionPoint.set(bean, value);
        }


        for (MethodInjectionPoint methodInjectionPoint : getRequiredProperties()) {
            if (onlyNonPublic && !java.lang.reflect.Modifier.isPublic(methodInjectionPoint.getMethod().getModifiers())) {
                Class[] methodArgumentTypes = methodInjectionPoint.getComponentTypes();
                Object[] methodArgs = new Object[methodArgumentTypes.length];
                for (int i = 0; i < methodArgumentTypes.length; i++) {
                    Class argument = methodArgumentTypes[i];
                    if (argument.isArray()) {
                        methodArgs[i] = context.getBeansOfType(argument.getComponentType());
                    } else {
                        methodArgs[i] = context.getBean(argument);
                    }
                }
                methodInjectionPoint.invoke(bean, methodArgs);
            }
        }

        return bean;
    }

    protected <B> B getBeanForMethodArgument(Context context, Class<B> dependencyType, Class dependencyOwner, String methodName, String argName) {
        try {
            return context.getBean(dependencyType);
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException("Failed to inject value for parameter ["+argName+"] of method [" + methodName + "] of class: " + dependencyOwner.getName() , e);
        }
    }

    protected <B> B getBeanForConstructorArgument(Context context, Class<B> dependencyType, Class dependencyOwner, String argName) {
        try {
            return context.getBean(dependencyType);
        } catch (NoSuchBeanException e) {
            throw new DependencyInjectionException("Failed to inject value for parameter ["+argName+"] of class: " + dependencyOwner.getName() , e);
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
    private DefaultComponentDefinition addMethodInjectionPointInternal(Method method, Collection<MethodInjectionPoint> methodInjectionPoints) {
        DefaultMethodInjectionPoint methodInjectionPoint = new DefaultMethodInjectionPoint(method);
        requiredComponents.addAll(Arrays.asList(methodInjectionPoint.getComponentTypes()));
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
