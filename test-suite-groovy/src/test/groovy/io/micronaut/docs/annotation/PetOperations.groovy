package io.micronaut.docs.annotation

// tag::imports[]

import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.reactivex.Single

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
// end::imports[]

// tag::class[]
@Validated
interface PetOperations {
    // tag::save[]
    @Post
    Single<Pet> save(@NotBlank String name, @Min(1L) int age)
    // end::save[]
}
// end::class[]
