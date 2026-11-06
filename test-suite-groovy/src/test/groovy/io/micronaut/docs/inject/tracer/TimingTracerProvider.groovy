/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.inject.tracer

// tag::imports[]
import io.micronaut.context.BeanResolutionTracer
import io.micronaut.context.BeanResolutionTracerProvider
import io.micronaut.context.env.Environment
// end::imports[]

// tag::class[]
class TimingTracerProvider implements BeanResolutionTracerProvider {

    @Override
    boolean isEnabled(Activation activation) {
        activation.discoveryEnabled ||
            activation.environmentNames.contains(Environment.DEVELOPMENT) // <1>
    }

    @Override
    BeanResolutionTracer createTracer(Activation activation) {
        new TimingTracer() // <2>
    }
}
// end::class[]
