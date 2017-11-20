package org.particleframework.core.type;

import org.particleframework.core.annotation.AnnotationSource;
import org.particleframework.core.annotation.AnnotationUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Create a new return type from the given type and arguments
     * @param type The type
     * @param typeArguments The type arguments
     * @param <T1>
     * @return A {@link ReturnType}
     */
    static <T1> ReturnType<T1> of(Class<T1> type, Argument<?>...typeArguments) {
        Map<String, Argument<?>> argumentMap = new LinkedHashMap<>(typeArguments.length);
        for (Argument<?> argument : typeArguments) {
            argumentMap.put(argument.getName(), argument);
        }
        return new ReturnType<T1>() {
            @Override
            public Class<T1> getType() {
                return type;
            }

            @Override
            public AnnotatedElement[] getAnnotatedElements() {
                return AnnotationUtil.ZERO_ANNOTATED_ELEMENTS;
            }

            @Override
            public Map<String, Argument<?>> getTypeVariables() {
                return argumentMap;
            }
        };
    }
}
