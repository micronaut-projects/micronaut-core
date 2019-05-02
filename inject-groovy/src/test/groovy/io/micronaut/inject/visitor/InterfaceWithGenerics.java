package io.micronaut.inject.visitor;

public interface InterfaceWithGenerics<T, ID>  {
    <S extends T> S save(S entity);
}

