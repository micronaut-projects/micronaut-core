package io.micronaut.http.client.docs.annotation.headers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.docs.annotation.Pet;
import io.reactivex.Single;

@Controller("/pets")
public class PetController {

    @Get("/{name}")
    public HttpResponse<Pet> get(String name, @Header("X-Pet-Client") String clientId) {
        Pet pet = new Pet();
        pet.setName(name);
        pet.setAge(Integer.valueOf(clientId));
        return HttpResponse.ok(pet)
                           .header("X-Pet-Client", clientId);
    }
}
