package io.micronaut.visitors;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Optional;

public interface InterfaceWithGenerics<ET, ID>  {
    <S extends ET> S save(S entity);

    <S extends ET> Iterable<S> saveAll(@Valid @NotNull @NonNull Iterable<S> entities);

    Optional<ET> find(ID id);

    void deleteAll(Iterable<? extends ET> iterable);
}
