package io.micronaut.inject.provider.injectionpointowner;

/**
 * A bean an {@link EachSeedConsumer} is created for, registered once per name by {@link SeedFactory}.
 *
 * @param name The name of the seed
 */
public record Seed(String name) {
}
