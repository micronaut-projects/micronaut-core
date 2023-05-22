package io.micronaut.kotlin.processing.aop.introduction

import java.util.*

abstract class AbstractCrudRepo<E, ID> {

    abstract fun findById(id: ID): Optional<E>
}
