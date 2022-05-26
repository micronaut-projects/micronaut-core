package io.micronaut.aop.introduction

@RepoDef
abstract class AbstractCustomAbstractCrudRepo extends AbstractCrudRepo<String, Long> {

    @Override
    @Marker
    abstract Optional<String> findById(Long aLong)
}
