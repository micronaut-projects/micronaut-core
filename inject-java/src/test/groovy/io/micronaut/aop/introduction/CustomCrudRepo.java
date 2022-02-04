package io.micronaut.aop.introduction;

import java.util.Optional;

@RepoDef
public interface CustomCrudRepo extends CrudRepo<String, Long> {

    @Override
    @Marker
    Optional<String> findById(Long aLong);
}
