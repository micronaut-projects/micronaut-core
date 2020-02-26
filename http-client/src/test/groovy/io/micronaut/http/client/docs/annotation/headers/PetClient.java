/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.client.docs.annotation.headers;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.docs.annotation.Pet;
import io.micronaut.http.client.docs.annotation.PetOperations;
import io.reactivex.Single;

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class[]
@Client("/pets")
@Header(name="X-Pet-Client", value="${pet.client.id}")
public interface PetClient extends PetOperations {

    @Override
    Single<Pet> save(String name, int age);

    @Get("/{name}")
    Single<Pet> get(String name);
}
// end::class[]
