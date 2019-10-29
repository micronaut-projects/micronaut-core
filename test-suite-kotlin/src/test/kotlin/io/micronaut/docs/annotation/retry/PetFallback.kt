package io.micronaut.docs.annotation.retry

import io.micronaut.docs.annotation.Pet
import io.micronaut.docs.annotation.PetOperations
import io.micronaut.retry.annotation.Fallback
import io.reactivex.Single

// tag::class[]
@Fallback
open class PetFallback : PetOperations {
    override fun save(name: String, age: Int): Single<Pet> {
        val pet = Pet()
        pet.age = age
        pet.name = name
        return Single.just(pet)
    }
}
// end::class[]
