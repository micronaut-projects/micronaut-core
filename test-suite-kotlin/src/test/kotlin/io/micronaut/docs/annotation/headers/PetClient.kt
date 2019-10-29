package io.micronaut.docs.annotation.headers

import io.micronaut.docs.annotation.Pet
import io.micronaut.docs.annotation.PetOperations
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single

// tag::class[]
@Client("/pets")
@Header(name = "X-Pet-Client", value = "\${pet.client.id}")
interface PetClient : PetOperations {

    override fun save(name: String, age: Int): Single<Pet>

    @Get("/{name}")
    operator fun get(name: String): Single<Pet>
}
// end::class[]
