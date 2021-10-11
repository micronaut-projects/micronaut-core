package io.micronaut.aop.introduction

interface CrudRepo<E, ID> {

    Optional<E> findById(ID id)

}
