package io.micronaut.docs.inject.retainable

//tag::imports[]
import jakarta.inject.Singleton
//end::imports[]

//tag::class[]
@MinLength(3)
@MaxLength(9)
@Singleton
class CodeValidator {
}
//end::class[]
