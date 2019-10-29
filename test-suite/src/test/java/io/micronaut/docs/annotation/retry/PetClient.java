package io.micronaut.docs.annotation.retry;

import io.micronaut.docs.annotation.Pet;
import io.micronaut.docs.annotation.PetOperations;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.annotation.Retryable;
import io.reactivex.Single;

// tag::class[]
@Client("/pets")
@Retryable
public interface PetClient extends PetOperations {

    @Override
    Single<Pet> save(String name, int age);
}
// end::class[]

