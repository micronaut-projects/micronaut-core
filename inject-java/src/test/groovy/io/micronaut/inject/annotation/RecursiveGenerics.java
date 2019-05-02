package io.micronaut.inject.annotation;

public abstract class RecursiveGenerics<T extends RecursiveGenerics<T>> {
}
