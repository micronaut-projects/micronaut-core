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
    fun save(@NotBlank name: String, @Min(1L) age: Int): Single<Pet>
    // end::save[]
}
// end::class[]
