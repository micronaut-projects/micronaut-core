package org.particleframework.core.convert;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * A service for allowing conversion from one type to another
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConversionService<Impl extends ConversionService> {

    /**
     * Attempts to convert the given object to the given target type. If conversion fails or is not possible an empty {@link Optional} is returned
     *
     * @param object The object to convert
     * @param targetType The target type
     * @param <T> The generic type
     *
     * @return The optional
     */
    default <T> Optional<T> convert(Object object, Class<T> targetType) {
        return convert(object, targetType, Collections.emptyMap());
    }

    /**
     * Attempts to convert the given object to the given target type. If conversion fails or is not possible an empty {@link Optional} is returned
     *
     * @param object The object to convert
     * @param targetType The target type
     * @param typeArguments The type arguments in the case where the target type accepts generic arguments such as collections etc.
     * @param <T> The generic type
     *
     * @return The optional
     */
    <T> Optional<T> convert(Object object, Class<T> targetType, Map<String, Class> typeArguments);

    /**
     * Adds a type converter
     *
     * @param sourceType The source type
     * @param targetType The target type
     * @param typeConverter The type converter
     *
     * @return This conversion service
     */
    <S, T> Impl addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter);
}
