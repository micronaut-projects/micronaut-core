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
internal class EngineFactory {

    @Prototype
    fun v8Engine(injectionPoint: InjectionPoint<*>, crankShaft: CrankShaft): Engine { // <1>
        val cylinders = injectionPoint
                .annotationMetadata
                .intValue(Cylinders::class.java).orElse(8) // <2>
        return when (cylinders) { // <3>
            6 -> V6Engine(crankShaft)
            8 -> V8Engine(crankShaft)
            else -> throw IllegalArgumentException("Unsupported number of cylinders specified: $cylinders")
        }
    }
}
// tag::class[]
