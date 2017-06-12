package org.particleframework.core.convert;

import java.util.Optional;

/**
 * A type converter for converting from one type to another. Implementations should be stateless and thread safe.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @param <S> The source type
 * @param <T> The target type
 */
public interface TypeConverter<S, T> {

    /**
     * Converts from the given source object type to the target type
     *
     * @param targetType The target type being converted to
     * @param object The object type
     * @return The converted type or empty if the conversion is not possible
     */
    Optional<T> convert(Class<T> targetType, S object);
}

