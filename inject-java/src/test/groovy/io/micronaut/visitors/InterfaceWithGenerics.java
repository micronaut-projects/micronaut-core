package io.micronaut.visitors;

public interface InterfaceWithGenerics<T, ID>  {
    <S extends T> S save(S entity);
}
