package io.micronaut.aop.introduction

abstract class AbstractCrudRepo<E, ID> {

   abstract Optional<E> findById(ID id)

}
