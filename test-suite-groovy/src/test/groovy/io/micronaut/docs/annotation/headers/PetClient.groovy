package io.micronaut.docs.annotation.headers

import io.micronaut.docs.annotation.Pet
import io.micronaut.docs.annotation.PetOperations
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single

// tag::class[]
@Client("/pets")
@Header(name="X-Pet-Client", value='${pet.client.id}')
interface PetClient extends PetOperations {

    @Override
    Single<Pet> save(String name, int age)

    @Get("/{name}")
    Single<Pet> get(String name)
}
// end::class[]
