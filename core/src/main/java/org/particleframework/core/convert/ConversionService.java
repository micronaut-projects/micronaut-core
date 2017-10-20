package org.particleframework.core.convert;

import java.util.Optional;
import java.util.function.Function;

/**
 * A service for allowing conversion from one type to another
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConversionService<Impl extends ConversionService> {

    /**
     * The default shared conversion service
     */
    ConversionService<?> SHARED = new DefaultConversionService();

    /**
     * Adds a type converter
     *
     * @param sourceType    The source type
     * @param targetType    The target type
     * @param typeConverter The type converter
     * @param <S> The source generic type
     * @param <T> The target generic type

     * @return This conversion service
     */
    <S, T> Impl addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> typeConverter);

    /**
     * Adds a type converter
     *
     * @param sourceType    The source type
     * @param targetType    The target type
     * @param typeConverter The type converter
     * @param <S> The source generic type
     * @param <T> The target generic type
     * @return This conversion service
     */
    <S, T> Impl addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter);

    /**
     * Attempts to convert the given object to the given target type. If conversion fails or is not possible an empty {@link Optional} is returned
     *
     * @param object        The object to convert
     * @param targetType    The target type
     * @param context       The conversion context
     * @param <T>           The generic type
     * @return The optional
     */
    <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context);
    /**
     * Attempts to convert the given object to the given target type. If conversion fails or is not possible an empty {@link Optional} is returned
     *
     * @param object     The object to convert
     * @param targetType The target type
     * @param <T>        The generic type
     * @return The optional
     */
    default <T> Optional<T> convert(Object object, Class<T> targetType) {
        return convert(object, targetType, ConversionContext.DEFAULT);
    }

    /**
     * Attempts to convert the given object to the given target type. If conversion fails or is not possible an empty {@link Optional} is returned
     *
     * @param object        The object to convert
     * @param context       The {@link ArgumentConversionContext}
     * @param <T>           The generic type
     * @return The optional
     */
    default <T> Optional<T> convert(Object object, ArgumentConversionContext<T> context) {
        return convert(object, context.getArgument().getType(), context);
    }
}
