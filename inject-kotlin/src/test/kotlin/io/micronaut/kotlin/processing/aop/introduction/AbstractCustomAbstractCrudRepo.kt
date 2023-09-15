package io.micronaut.kotlin.processing.aop.introduction

import java.util.*

@RepoDef
abstract class AbstractCustomAbstractCrudRepo : AbstractCrudRepo<String, Long>() {

    @Marker
    abstract override fun findById(id: Long): Optional<String>
}
