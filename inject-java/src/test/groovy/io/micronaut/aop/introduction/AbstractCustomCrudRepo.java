package io.micronaut.aop.introduction;

import java.util.Optional;

@RepoDef
public abstract class AbstractCustomCrudRepo implements CrudRepo<String, Long> {

    @Override
    @Marker
    public abstract Optional<String> findById(Long aLong);
}
