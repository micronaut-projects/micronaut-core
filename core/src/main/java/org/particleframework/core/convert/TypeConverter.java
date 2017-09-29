package org.particleframework.core.convert;

import java.util.Optional;
import java.util.function.Function;

/**
 * A type converter for converting from one type to another. Implementations should be stateless and thread safe.
 *
 * @param <S> The source type
 * @param <T> The target type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface TypeConverter<S, T> {

    /**
     * Converts from the given source object type to the target type
     *
     * @param object     The object type
     * @param targetType The target type being converted to
     * @return The converted type or empty if the conversion is not possible
     */
    default Optional<T> convert(S object, Class<T> targetType) {
        return convert(object, targetType, ConversionContext.DEFAULT);
    }

    /**
     * Converts from the given source object type to the target type. Implementers should take care to return {@link Optional#empty()}
     * in case the object is not convertible by catching any necessary exceptions and failing gracefully.
     *
     * @param object     The object type
     * @param targetType The target type being converted to
     * @param context The {@link ConversionContext}
     *
     * @return The converted type or empty if the conversion is not possible
     */
    Optional<T> convert(S object, Class<T> targetType, ConversionContext context);

    /**
     * Creates a new {@link TypeConverter} for the give source type, target type and conversion function
     *
     * @param sourceType The source type
     * @param targetType The target type
     * @param converter The converter function
     * @param <ST> The source generic type
     * @param <TT> The target generic type
     * @return The converter instance
     */
    static <ST, TT> TypeConverter<ST, TT> of(Class<ST> sourceType, Class<TT> targetType, Function<ST, TT> converter) {
        return (object, targetType1, context) -> Optional.of(converter.apply(object));
    }
}

