package org.particleframework.context;

import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.MethodInjectionPoint;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

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
    private final BeanDefinition declaringBean;
    private final AnnotatedElement[] annotationElements;

    DefaultMethodInjectionPoint(BeanDefinition declaringBean,
                                Method method,
                                boolean requiresReflection,
                                Argument...arguments) {
        this.method = method;
        this.annotationElements = new AnnotatedElement[] { method, declaringBean};
        this.requiresReflection = requiresReflection;
        if (requiresReflection) {
            this.method.setAccessible(true);
        }
        this.arguments = arguments == null || arguments.length == 0 ? Argument.ZERO_ARGUMENTS : arguments;
        this.declaringBean = declaringBean;
    }

    DefaultMethodInjectionPoint(BeanDefinition declaringComponent,
                                Field field,
                                Method method,
                                boolean requiresReflection,
                                Argument...arguments) {
        this.method = method;
        this.annotationElements = new AnnotatedElement[] { field, method, declaringComponent};
        this.requiresReflection = requiresReflection;
        if(requiresReflection) {
            this.method.setAccessible(true);
        }
        this.arguments = arguments == null || arguments.length == 0 ? Argument.ZERO_ARGUMENTS : arguments;
        this.declaringBean = declaringComponent;
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
        return this.declaringBean;
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
    public AnnotatedElement[] getAnnotatedElements() {
        return this.annotationElements;
    }

}
