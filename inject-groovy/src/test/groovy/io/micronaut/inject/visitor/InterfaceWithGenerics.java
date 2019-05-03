package io.micronaut.inject.visitor;

import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public interface InterfaceWithGenerics<ET, ID>  {
    <S extends ET> S save(S entity);

    <S extends ET> Iterable<S> saveAll(@Valid @NotNull @Nonnull Iterable<S> entities);
}

