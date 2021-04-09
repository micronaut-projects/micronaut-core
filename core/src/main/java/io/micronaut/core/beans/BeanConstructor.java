package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Described;
import io.micronaut.core.type.Argument;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Models a bean constructor.
 *
 * @param <T> The bean type
 * @since 3.0.0
 * @author graemerocher
 */
public interface BeanConstructor<T> extends AnnotationMetadataProvider, Described {
    /**
     * Returns the bean type.
     *
     * @return The underlying bean type
     */
    @NonNull Class<T> getDeclaringBeanType();

    /**
     * @return The constructor argument types.
     */
    @NonNull Argument<?>[] getArguments();

    /**
     * Instantiate an instance.
     * @param parameterValues The parameter values
     * @return The instance, never null.
     */
    @NonNull T instantiate(Object... parameterValues);

    /**
     * The description of the constructor.
     * @return The description
     */
    default String getDescription() {
        return getDescription(true);
    }

    /**
     * The description of the constructor.
     * @param simple Whether to return a simple representation without package names
     * @return The description
     */
    default String getDescription(boolean simple) {
        String args = Arrays.stream(getArguments())
                .map(arg -> arg.getTypeString(simple) + " " + arg.getName())
                .collect(Collectors.joining(","));
        return getDeclaringBeanType().getSimpleName() + "(" + args + ")";
    }
}
