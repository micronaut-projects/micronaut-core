package org.particleframework.context;

import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.inject.Argument;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.MethodInjectionPoint;
import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.core.annotation.Internal;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an injection point for a method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultMethodInjectionPoint implements MethodInjectionPoint {
    private final Method method;
    private final Argument[] arguments;
    private final boolean requiresReflection;
    private final BeanDefinition declaringComponent;

    DefaultMethodInjectionPoint(BeanDefinition declaringComponent,
                                Method method,
                                boolean requiresReflection,
                                Map<String, Class> arguments,
                                Map<String, Annotation> qualifiers,
                                Map<String, List<Class>> genericTypes) {
        this.method = method;
        this.requiresReflection = requiresReflection;
        if (requiresReflection) {
            this.method.setAccessible(true);
        }
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        this.arguments = DefaultArgument.from(arguments, qualifiers, genericTypes, index -> {
            if(index < parameterAnnotations.length) {
                return parameterAnnotations[index];
            }
            return new Annotation[0];
        });
        this.declaringComponent = declaringComponent;
    }

    DefaultMethodInjectionPoint(BeanDefinition declaringComponent,
                                Field field,
                                Method method,
                                boolean requiresReflection,
                                Map<String, Class> arguments,
                                Map<String, Annotation> qualifiers,
                                Map<String, List<Class>> genericTypes) {
        this.method = method;
        this.requiresReflection = requiresReflection;
        if(requiresReflection) {
            this.method.setAccessible(true);
        }
        Annotation[] annotations = field.getAnnotations();
        this.arguments = DefaultArgument.from(arguments, qualifiers, genericTypes, index -> {
            if(index == 0) {
                return annotations;
            }
            return new Annotation[0];
        });
        this.declaringComponent = declaringComponent;
    }

    @Override
    public boolean requiresReflection() {
        return requiresReflection;
    }

    @Override
    public boolean isPreDestroyMethod() {
        return method.getAnnotation(PreDestroy.class) != null;
    }

    @Override
    public boolean isPostConstructMethod() {
        return method.getAnnotation(PostConstruct.class) != null;
    }

    @Override
    public BeanDefinition getDeclaringBean() {
        return this.declaringComponent;
    }

    @Override
    public String getName() {
        return method.getName();
    }

    @Override
    public Argument[] getArguments() {
        return arguments;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object invoke(Object instance, Object... args) {
        Argument[] componentTypes = getArguments();
        if(componentTypes.length != args.length) {
            throw new BeanInstantiationException("Invalid bean argument count specified. Required: "+componentTypes.length+" . Received: " + args.length);
        }

        for (int i = 0; i < componentTypes.length; i++) {
            Class componentType = componentTypes[i].getType();
            if(!componentType.isInstance(args[i])) {
                throw new BeanInstantiationException("Invalid bean argument received ["+args[i]+"] at position ["+i+"]. Required type is: " + componentType.getName());
            }
        }
        try {
            return method.invoke(instance, args);
        } catch (Throwable e) {
            throw new BeanInstantiationException("Cannot inject arguments for method ["+method+"] using arguments ["+ Arrays.asList(args)+"]:" + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "Injection Point: " + method.toGenericString();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return method.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return method.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return method.getDeclaredAnnotations();
    }
}
