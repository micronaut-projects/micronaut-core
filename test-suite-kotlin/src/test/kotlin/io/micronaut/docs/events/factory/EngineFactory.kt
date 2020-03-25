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
package io.micronaut.docs.events.factory

import io.micronaut.context.annotation.Factory

import javax.annotation.PostConstruct
import javax.inject.Singleton

// tag::class[]
@Factory
class EngineFactory {

    private var engine: V8Engine? = null
    private var rodLength = 5.7

    @PostConstruct
    fun initialize() {
        engine = V8Engine(rodLength) // <2>
    }

    @Singleton
    fun v8Engine(): Engine? {
        return engine// <3>
    }

    fun setRodLength(rodLength: Double) {
        this.rodLength = rodLength
    }
}
// end::class[]