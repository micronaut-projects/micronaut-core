package io.micronaut.aop.introduction

@RepoDef
abstract class AbstractCustomCrudRepo implements CrudRepo<String, Long> {

    @Override
    @Marker
    abstract Optional<String> findById(Long aLong)
}
