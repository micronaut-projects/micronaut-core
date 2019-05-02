package io.micronaut.inject.visitor;

import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public interface InterfaceWithGenerics<T, ID>  {
    <S extends T> S save(S entity);

    <S extends T> Iterable<S> saveAll(@Valid @NotNull @Nonnull Iterable<S> entities);
}

