package io.micronaut.kotlin.processing.aop.introduction

import jakarta.validation.constraints.NotNull

@RepoDef
interface MyRepo2 : DeleteByIdCrudRepo<Int> {

    override fun deleteById(@NotNull id: Int)
}
