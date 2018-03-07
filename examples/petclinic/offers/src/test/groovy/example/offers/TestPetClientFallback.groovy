/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.offers

import example.api.v1.Pet
import example.offers.client.v1.PetClient
import io.reactivex.Maybe
import io.reactivex.Single
import io.micronaut.retry.annotation.Fallback

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
@Fallback
@Singleton
class TestPetClientFallback implements PetClient {

    private Map<String, Pet> pets = [:]

    void addPet(Pet pet) {
        pets.put(pet.getSlug(), pet)
    }

    @Override
    Maybe<Pet> find(String slug) {
        Pet pet = pets.get(slug)
        if(pet != null) {
            return Maybe.just(pet)
        }
        return Maybe.empty()
    }

    @Override
    Single<List<Pet>> list() {
        return Single.just(Collections.emptyList())
    }
}
