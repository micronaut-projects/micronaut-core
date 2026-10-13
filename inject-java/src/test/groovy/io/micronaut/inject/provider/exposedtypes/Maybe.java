package io.micronaut.inject.provider.exposedtypes;

/**
 * A bean that is built only for an injection point that is not named {@code absent}.
 *
 * @param <T> The type argument of the injection point
 */
public record Maybe<T>(String injectedAt) {
}
