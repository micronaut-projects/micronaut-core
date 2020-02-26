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
package io.micronaut.docs.ioc.validation.pojo

// tag::imports[]
import io.micronaut.docs.ioc.validation.Person

import javax.inject.Singleton
import javax.validation.Valid
// end::imports[]

// tag::class[]
@Singleton
class PersonService {
    void sayHello(@Valid Person person) {
        println "Hello $person.name"
    }
}
// end::class[]

