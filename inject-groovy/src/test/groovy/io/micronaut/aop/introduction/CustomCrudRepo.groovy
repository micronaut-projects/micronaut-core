package io.micronaut.aop.introduction

@RepoDef
interface CustomCrudRepo extends CrudRepo<String, Long> {

    @Override
    @Marker
    Optional<String> findById(Long aLong)
}
