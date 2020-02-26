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
    private V8Engine engine
    double rodLength = 5.7

    @PostConstruct
    void initialize() {
        engine = new V8Engine(rodLength: rodLength) // <2>
    }

    @Singleton
    Engine v8Engine() {
        return engine // <3>
    }
}
// tag::class[]
