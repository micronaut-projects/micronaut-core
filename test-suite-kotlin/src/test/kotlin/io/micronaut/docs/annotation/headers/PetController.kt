package io.micronaut.docs.annotation.headers

import io.micronaut.docs.annotation.Pet
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header

@Controller("/pets")
class PetController {

    @Get("/{name}")
    operator fun get(name: String, @Header("X-Pet-Client") clientId: String): HttpResponse<Pet> {
        val pet = Pet()
        pet.name = name
        pet.age = Integer.valueOf(clientId)
        return HttpResponse.ok(pet)
                .header("X-Pet-Client", clientId)
    }
}
