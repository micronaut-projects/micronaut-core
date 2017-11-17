package org.particleframework.core.type;

import org.particleframework.core.annotation.AnnotationSource;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collection;
import java.util.List;

/**
 * Models a return type of an {@link Executable} method in Particle
 *
 * @author Graeme Rocher
 * @since 1.0
 * @param <T> The concrete type
 */
public interface ReturnType<T> extends TypeVariableResolver, AnnotationSource {
    /**
     * @return The type of the argument
     */
    Class<T> getType();

    /**
     * @return The return type as an argument
     */
    default Argument<T> asArgument() {
        Collection<Argument<?>> values = getTypeVariables().values();
        return Argument.of(getType(), values.toArray(new Argument[values.size()]));
    }
}
