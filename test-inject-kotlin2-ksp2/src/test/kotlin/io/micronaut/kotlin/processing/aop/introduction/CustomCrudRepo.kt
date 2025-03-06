package io.micronaut.kotlin.processing.aop.introduction

import java.util.*

@RepoDef
interface CustomCrudRepo : CrudRepo<String, Long> {

    @Marker
    override fun findById(id: Long): Optional<String>
}
