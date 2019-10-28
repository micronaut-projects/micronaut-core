package io.micronaut.docs.annotation.retry

import io.micronaut.docs.annotation.Pet
import io.micronaut.docs.annotation.PetOperations
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.Single

// tag::class[]
@Client("/pets")
@Retryable
interface PetClient : PetOperations {

    override fun save(name: String, age: Int): Single<Pet>
}
// end::class[]

