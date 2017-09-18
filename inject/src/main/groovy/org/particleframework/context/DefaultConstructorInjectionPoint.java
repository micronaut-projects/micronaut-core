package org.particleframework.context;

import org.particleframework.core.annotation.Internal;
import org.particleframework.inject.Argument;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ConstructorInjectionPoint;
import org.particleframework.context.exceptions.BeanInstantiationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An injection point for a constructor
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultConstructorInjectionPoint<T> implements ConstructorInjectionPoint<T> {
    private final Constructor<T> constructor;
    private final Argument[] arguments;
    private final BeanDefinition declaringComponent;
    private final boolean requiresReflection;

    DefaultConstructorInjectionPoint(BeanDefinition declaringComponent, Constructor<T> constructor, Map<String, Class> arguments, Map<String, Annotation> qualifiers, Map<String, List<Class>> genericTypes) {
        this.declaringComponent = declaringComponent;
        this.constructor = constructor;
        this.requiresReflection = Modifier.isPrivate(constructor.getModifiers());
        Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
        this.arguments = DefaultArgument.from(arguments, qualifiers, genericTypes, index -> {
            if(index < parameterAnnotations.length) {
                return parameterAnnotations[index];
            }
            return new Annotation[0];
        });
    }

    @Override
    public BeanDefinition getDeclaringBean() {
        return this.declaringComponent;
    }

    @Override
    public boolean requiresReflection() {
        return requiresReflection;
    }

    @Override
    public Class getDeclaringType() {
        return getDeclaringBean().getType();
    }

    @Override
    public Argument[] getArguments() {
        return arguments;
    }

    @Override
    public T invoke(Object... args) {
        this.constructor.setAccessible(true);
        Argument[] componentTypes = getArguments();
        if(componentTypes.length == 0) {
            try {
                return constructor.newInstance();
            } catch (Throwable e) {
                throw new BeanInstantiationException("Cannot instantiate bean of type ["+constructor.getDeclaringClass().getName()+"] using constructor ["+constructor+"]:" + e.getMessage(), e);
            }
        }
        else {
            if(componentTypes.length != args.length) {
                throw new BeanInstantiationException("Invalid bean argument count specified. Required: "+componentTypes.length+" . Received: " + args.length);
            }

            for (int i = 0; i < componentTypes.length; i++) {
                Argument componentType = componentTypes[i];
                if(!componentType.getType().isInstance(args[i])) {
                    throw new BeanInstantiationException("Invalid bean argument received ["+args[i]+"] at position ["+i+"]. Required type is: " + componentType.getName());
                }
            }
            try {
                return constructor.newInstance(args);
            } catch (Throwable e) {
                throw new BeanInstantiationException("Cannot instantiate bean of type ["+constructor.getDeclaringClass().getName()+"] using constructor ["+constructor+"]:" + e.getMessage(), e);
            }
        }
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return constructor.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return constructor.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return constructor.getDeclaredAnnotations();
    }
}
