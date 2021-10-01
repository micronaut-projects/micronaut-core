package io.micronaut.aop.introduction;

import java.util.Optional;

public abstract class AbstractCrudRepo<E, ID> {

   public abstract Optional<E> findById(ID id);

}
