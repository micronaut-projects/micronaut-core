package io.micronaut.inject.any;

public interface Dog<T> {
    String getRace();

    Class<T> getType();
}
