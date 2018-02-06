package org.particleframework.core.value;

import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.type.Argument;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A property resolver is capable of resolving properties from an underlying property source
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertyResolver extends ValueResolver<String> {


    /**
     * <p>Whether the given property is contained within this resolver.</p>
     *
     * <p>Note that this method will return false for nested properties. In other words given a key of <tt>foo.bar</tt> this method will
     * return <tt>false</tt> for: <code>resolver.containsProperty("foo")</code></p>
     *
     * <p>To check for nested properties using {@link #containsProperties(String)} instead.</p>
     *
     * @param name The name of the property
     * @return True if it is
     */
    boolean containsProperty(String name);

    /**
     * Whether the given property or any nested properties exist for the key given key within this resolver
     *
     * @param name The name of the property
     * @return True if it is
     */
    boolean containsProperties(String name);

    /**
     * <p>Resolve the given property for the given name, type and generic type arguments.</p>
     *
     * <p>Implementers can choose to implement more intelligent type conversion by analyzing the typeArgument.</p>
     *
     *
     * @param name The name
     * @param conversionContext The conversion context
     * @param <T> The concrete type
     * @return An optional containing the property value if it exists
     */
    <T> Optional<T> getProperty(String name, ArgumentConversionContext<T> conversionContext);

    /**
     * <p>Resolve the given property for the given name, type and generic type arguments.</p>
     *
     * <p>Implementers can choose to implement more intelligent type conversion by analyzing the typeArgument.</p>
     *
     *
     * @param name The name
     * @param argument The required type
     * @param <T> The concrete type
     * @return An optional containing the property value if it exists
     */
    default <T> Optional<T> getProperty(String name, Argument<T> argument) {
        return getProperty(name, ConversionContext.of(argument));
    }
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
    default <T> Optional<T> getProperty(String name, Class<T> requiredType, ConversionContext context) {
        return getProperty(name, context.with(Argument.of(requiredType)));
    }

    @Override
    default <T> Optional<T> get(String name, ArgumentConversionContext<T> conversionContext) {
        return getProperty(name, conversionContext);
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

//    /**
//     * Resolve the given property for the given name
//     *
//     * @param name The name
//     * @return An optional containing the property value if it exists
//     */
//    default Optional<Object> getProperty(String name) {
//        return getProperty(name, Object.class);
//    }
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

    /**
     * Builds a property name for the given property path
     * @param path The path
     * @return The property name
     */
    static String nameOf(String...path) {
        return Arrays.stream(path).collect(Collectors.joining("."));
    }
}
