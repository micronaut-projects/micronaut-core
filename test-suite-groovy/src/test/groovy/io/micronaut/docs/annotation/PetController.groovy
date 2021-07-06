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
package io.micronaut.docs.annotation

// tag::imports[]
import io.micronaut.http.annotation.Controller
import org.reactivestreams.Publisher
import io.micronaut.core.async.annotation.SingleResult
import reactor.core.publisher.Mono
// end::imports[]

// tag::class[]
@Controller("/pets")
class PetController implements PetOperations {

    @Override
    @SingleResult
    Publisher<Pet> save(String name, int age) {
        Pet pet = new Pet(name: name, age: age)
        // save to database or something
        return Mono.just(pet)
    }
}
// end::class[]
