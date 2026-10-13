package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * A bean with type arguments: its definition has no generated candidate check, so it is a candidate for
 * the types it exposes only, which never include {@code Object}.
 */
@Singleton
@Named("codec")
@Requires(property = "spec.name", value = "BeanWatchTest")
public class StringCodec implements Codec<String> {
    @Override
    public String decode(String value) {
        return value;
    }
}
