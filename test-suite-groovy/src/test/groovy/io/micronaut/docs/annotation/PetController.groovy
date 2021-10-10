package io.micronaut.docs.annotation

// tag::imports[]
import io.micronaut.http.annotation.Controller
import io.reactivex.Single
// end::imports[]


// tag::class[]
@Controller("/pets")
class PetController implements PetOperations {

    @Override
    Single<Pet> save(String name, int age) {
        Pet pet = new Pet()
        pet.setName(name)
        pet.setAge(age)
        // save to database or something
        return Single.just(pet)
    }
}
// end::class[]