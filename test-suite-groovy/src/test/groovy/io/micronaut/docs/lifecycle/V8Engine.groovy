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
package io.micronaut.docs.lifecycle

// tag::imports[]
import javax.annotation.PostConstruct // <1>
import javax.inject.Singleton
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Singleton
class V8Engine implements Engine {
    int cylinders = 8
    boolean initialized = false // <2>

    String start() {
        if(!initialized) throw new IllegalStateException("Engine not initialized!")

        return "Starting V8"
    }

    @PostConstruct // <3>
    void initialize() {
        this.initialized = true
    }
}
// end::class[]