package org.particleframework.inject;

import org.particleframework.core.annotation.AnnotationSource;

/**
 * A constructor injection point
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConstructorInjectionPoint<T> extends CallableInjectionPoint {

    /**
     * Invoke the constructor
     *
     * @param args The arguments
     * @return The new value
     */
    T invoke(Object... args);
}
