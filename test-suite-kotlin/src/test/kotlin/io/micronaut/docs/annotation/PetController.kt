package io.micronaut.docs.annotation

// tag::imports[]
import io.micronaut.http.annotation.Controller
import io.reactivex.Single

// end::imports[]


// tag::class[]
@Controller("/pets")
open class PetController : PetOperations {

    override fun save(name: String, age: Int): Single<Pet> {
        val pet = Pet()
        pet.name = name
        pet.age = age
        // save to database or something
        return Single.just(pet)
    }
}
// end::class[]