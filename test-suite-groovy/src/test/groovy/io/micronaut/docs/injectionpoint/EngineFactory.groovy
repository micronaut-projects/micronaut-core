/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.docs.injectionpoint

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.InjectionPoint

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Factory
class EngineFactory {

    @Prototype
    Engine v8Engine(InjectionPoint<Engine> injectionPoint, CrankShaft crankShaft) { // <1>
        final int cylinders = injectionPoint
                .getAnnotationMetadata()
                .intValue(Cylinders.class).orElse(8) // <2>
        switch (cylinders) { // <3>
            case 6:
                return new V6Engine(crankShaft)
            case 8:
                return new V8Engine(crankShaft)
            default:
                throw new IllegalArgumentException("Unsupported number of cylinders specified: $cylinders")
        }
    }
}
// tag::class[]
