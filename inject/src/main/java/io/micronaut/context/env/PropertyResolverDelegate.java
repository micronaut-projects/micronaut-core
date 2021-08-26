package io.micronaut.context.env;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.PropertyNotFoundException;
import io.micronaut.core.value.PropertyResolver;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Internal
public interface PropertyResolverDelegate extends PropertyResolver {

    PropertyResolver getPropertyResolverDelegate();

    @Override
    default boolean containsProperty(@NonNull String name) {
        return getPropertyResolverDelegate().containsProperty(name);
    }

    @Override
    default boolean containsProperties(@NonNull String name) {
        return getPropertyResolverDelegate().containsProperties(name);
    }

    @Override
    default @NonNull
    <T> Optional<T> getProperty(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext) {
        return getPropertyResolverDelegate().getProperty(name, conversionContext);
    }

    @Override
    default @NonNull
    Collection<String> getPropertyEntries(@NonNull String name) {
        return getPropertyResolverDelegate().getPropertyEntries(name);
    }

    @Override
    default @NonNull
    <T> Optional<T> getProperty(@NonNull String name, @NonNull Argument<T> argument) {
        return getPropertyResolverDelegate().getProperty(name, argument);
    }

    @Override
    default @NonNull
    Map<String, Object> getProperties(@NonNull String name) {
        return getPropertyResolverDelegate().getProperties(name);
    }

    @Override
    default @NonNull
    Map<String, Object> getProperties(@Nullable String name, @Nullable StringConvention keyFormat) {
        return getPropertyResolverDelegate().getProperties(name, keyFormat);
    }

    @Override
    default @NonNull
    <T> Optional<T> getProperty(@NonNull String name, @NonNull Class<T> requiredType, @NonNull ConversionContext context) {
        return getPropertyResolverDelegate().getProperty(name, requiredType, context);
    }

    @Override
    default @NonNull
    <T> Optional<T> get(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext) {
        return getPropertyResolverDelegate().get(name, conversionContext);
    }

    @Override
    default @NonNull
    <T> Optional<T> getProperty(@NonNull String name, @NonNull Class<T> requiredType) {
        return getPropertyResolverDelegate().getProperty(name, requiredType);
    }

    @Override
    default @Nullable
    <T> T getProperty(@NonNull String name, @NonNull Class<T> requiredType, @Nullable T defaultValue) {
        return getPropertyResolverDelegate().getProperty(name, requiredType, defaultValue);
    }

    @Override
    default @NonNull
    <T> T getRequiredProperty(@NonNull String name, @NonNull Class<T> requiredType) throws PropertyNotFoundException {
        return getPropertyResolverDelegate().getRequiredProperty(name, requiredType);
    }
}
