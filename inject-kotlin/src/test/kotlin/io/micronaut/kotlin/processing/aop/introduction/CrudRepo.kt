package io.micronaut.kotlin.processing.aop.introduction

import java.util.*

interface CrudRepo<E, ID> {

    fun findById(id: ID): Optional<E>
}
