package org.particleframework.config;

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ValueResolver;
import org.particleframework.core.type.Argument;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * A property resolver is capable of resolving properties from an underlying property source
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertyResolver extends ValueResolver {


    /**
     * Whether the given property is contained within this resolved
     * @param name The name of the property
     * @return True if it is
     */
    boolean containsProperty(String name);
    /**
     * <p>Resolve the given property for the given name, type and generic type arguments.</p>
     *
     * <p>Implementers can choose to implement more intelligent type conversion by analyzing the typeArgument.</p>
     *
     *
     * @param name The name
     * @param requiredType The required type
     * @param context The {@link ConversionContext} to apply  to any conversion
     * @param <T> The concrete type
     * @return An optional containing the property value if it exists
     */
    <T> Optional<T> getProperty(String name, Class<T> requiredType, ConversionContext context);


    /**
     * @see ValueResolver#get(CharSequence, Class)
     */
    @Override
    default <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        return getProperty(name.toString(), requiredType);
    }

    /**
     * @see ValueResolver#get(CharSequence, Class)
     */
    @Override
    default <T> T get(CharSequence name, Class<T> requiredType, T defaultValue) {
        return getProperty(name.toString(), requiredType, defaultValue);
    }

    /**
     * @see ValueResolver#get(CharSequence, Argument)
     */
    @Override
    default <T> Optional<T> get(CharSequence name, Argument<T> requiredType) {
        return getProperty(name.toString(), requiredType.getType(), ConversionContext.of(requiredType));
    }

    /**
     * Resolve the given property for the given name
     *
     * @param name The name
     * @param requiredType The required type
     * @param <T> The concrete type
     * @return An optional containing the property value if it exists
     */
    default <T> Optional<T> getProperty(String name, Class<T> requiredType) {
        return getProperty(name, requiredType, ConversionContext.DEFAULT);
    }

    /**
     * Resolve the given property for the given name
     *
     * @param name The name
     * @param requiredType The required type
     * @param defaultValue The default value
     * @param <T> The concrete type
     * @return An optional containing the property value if it exists
     */
    default <T> T getProperty(String name, Class<T> requiredType, T defaultValue) {
        return getProperty(name, requiredType).orElse(defaultValue);
    }

    /**
     * Resolve the given property for the given name
     *
     * @param name The name of the property
     * @param requiredType The required type
     * @param <T> The concrete type
     * @return The value of the property
     */
    default <T> T getRequiredProperty(String name, Class<T> requiredType) throws PropertyNotFoundException {
        return getProperty(name, requiredType).orElseThrow(() ->
            new PropertyNotFoundException(name, requiredType)
        );
    }
}
