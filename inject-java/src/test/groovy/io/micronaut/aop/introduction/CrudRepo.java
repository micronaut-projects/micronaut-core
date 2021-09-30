package io.micronaut.aop.introduction;

import java.util.Optional;

public interface CrudRepo<E, ID> {

    Optional<E> findById(ID id);

}
