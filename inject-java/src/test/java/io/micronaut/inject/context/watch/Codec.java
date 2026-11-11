package io.micronaut.inject.context.watch;

public interface Codec<T> {
    T decode(String value);
}
