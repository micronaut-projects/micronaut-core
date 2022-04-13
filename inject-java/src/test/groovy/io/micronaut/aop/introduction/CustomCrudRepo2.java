package io.micronaut.aop.introduction;

import java.util.Optional;

@RepoDef
public interface CustomCrudRepo2 {

    Optional<CustomEntity> custom1(Long aLong);

    @Marker
    Optional<CustomEntity> custom2(Long aLong);
}
