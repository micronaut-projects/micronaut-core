package io.micronaut.context.env;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.value.PropertyResolver;

import java.util.Optional;

/**
 * The property expression resolver.
 *
 * @author Denis Stepanov
 * @since 3.4.1
 */
public interface PropertyExpressionResolver {

    /**
     * Resolve the value for the expression of the specified type.
     *
     * @param propertyResolver  The property resolver
     * @param conversionService The conversion service
     * @param expression        The expression
     * @param requiredType      The required typ
     * @param <T>               The type
     * @return The optional resolved value
     */
    <T> Optional<T> resolve(@NonNull PropertyResolver propertyResolver,
                            @NonNull ConversionService<?> conversionService,
                            @NonNull String expression,
                            @NonNull Class<T> requiredType);

}
