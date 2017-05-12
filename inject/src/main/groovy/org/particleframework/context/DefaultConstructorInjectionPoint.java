package org.particleframework.context;

import org.particleframework.inject.ConstructorInjectionPoint;
import org.particleframework.context.exceptions.BeanInstantiationException;

import java.lang.reflect.Constructor;

/**
 * An injection point for a constructor
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultConstructorInjectionPoint<T> implements ConstructorInjectionPoint<T> {
    private final Constructor<T> constructor;

    DefaultConstructorInjectionPoint(Constructor<T> constructor) {
        this.constructor = constructor;
        this.constructor.setAccessible(true);
    }

    @Override
    public Class[] getComponentTypes() {
        return constructor.getParameterTypes();
    }

    @Override
    public Constructor<T> getConstructor() {
        return constructor;
    }

    @Override
    public T invoke(Object... args) {
        Class[] componentTypes = getComponentTypes();
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
                Class componentType = componentTypes[i];
                if(!componentType.isInstance(args[i])) {
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
}
