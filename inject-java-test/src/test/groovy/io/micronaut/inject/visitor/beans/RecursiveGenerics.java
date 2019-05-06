package io.micronaut.inject.visitor.beans;

public abstract class RecursiveGenerics<T extends RecursiveGenerics<T>> {
}

