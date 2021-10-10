package io.micronaut.docs.annotation.headers

import io.micronaut.docs.annotation.Pet
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header

@Controller("/pets")
class PetController {

    @Get("/{name}")
    HttpResponse<Pet> get(String name, @Header("X-Pet-Client") String clientId) {
        Pet pet = new Pet()
        pet.setName(name)
        pet.setAge(Integer.valueOf(clientId))
        return HttpResponse.ok(pet)
                           .header("X-Pet-Client", clientId)
    }
}
