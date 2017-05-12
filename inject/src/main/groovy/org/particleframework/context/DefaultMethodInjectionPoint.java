package org.particleframework.context;

import org.particleframework.inject.MethodInjectionPoint;
import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.core.annotation.Internal;

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

    DefaultMethodInjectionPoint(Method method) {
        this.method = method;
        this.method.setAccessible(true);
    }

    @Override
    public String getName() {
        return method.getName();
    }

    @Override
    public Class[] getComponentTypes() {
        return method.getParameterTypes();
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object invoke(Object instance, Object... args) {
        Class[] componentTypes = getComponentTypes();
        if(componentTypes.length != args.length) {
            throw new BeanInstantiationException("Invalid bean argument count specified. Required: "+componentTypes.length+" . Received: " + args.length);
        }

        for (int i = 0; i < componentTypes.length; i++) {
            Class componentType = componentTypes[i];
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
}
