package io.micronaut.docs.annotation

// tag::imports[]
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single
// end::imports[]

// tag::class[]
@Client("/pets") // <1>
interface PetClient extends PetOperations { // <2>

    @Override
    Single<Pet> save(String name, int age) // <3>
}
// end::class[]
