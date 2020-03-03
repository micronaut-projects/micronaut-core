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
package io.micronaut.docs.config.property;

// tag::imports[]
import io.micronaut.context.annotation.Property

import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class Engine {

    @Property(name = "my.engine.cylinders") // <1>
    protected int cylinders // <2>

    @Property(name = "my.engine.manufacturer") //<3>
    String manufacturer

    int getCylinders() {
        cylinders
    }
}
// end::class[]
