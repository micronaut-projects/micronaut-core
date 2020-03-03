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
package io.micronaut.docs.inject.qualifiers.named

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Singleton
class Vehicle {
    final Engine engine

    @Inject Vehicle(@Named('v8') Engine engine) { // <4>
        this.engine = engine
    }

    String start() {
        engine.start() // <5>
    }
}
// end::class[]