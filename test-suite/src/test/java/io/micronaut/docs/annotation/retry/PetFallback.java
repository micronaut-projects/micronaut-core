package io.micronaut.docs.annotation.retry;

import io.micronaut.docs.annotation.Pet;
import io.micronaut.docs.annotation.PetOperations;
import io.micronaut.retry.annotation.Fallback;
import io.reactivex.Single;

// tag::class[]
@Fallback
public class PetFallback implements PetOperations {
    @Override
    public Single<Pet> save(String name, int age) {
        Pet pet = new Pet();
        pet.setAge(age);
        pet.setName(name);
        return Single.just(pet);
    }
}
// end::class[]
