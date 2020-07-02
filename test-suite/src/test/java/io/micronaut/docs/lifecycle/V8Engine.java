/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.lifecycle;

// tag::imports[]
import javax.annotation.PostConstruct; // <1>
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class V8Engine implements Engine {
    private int cylinders = 8;
    private boolean initialized = false; // <2>

    @Override
    public String start() {
        if(!initialized) {
            throw new IllegalStateException("Engine not initialized!");
        }

        return "Starting V8";
    }

    @Override
    public int getCylinders() {
        return cylinders;
    }

    public boolean isIntialized() {
        return this.initialized;
    }

    @PostConstruct // <3>
    public void initialize() {
        this.initialized = true;
    }
}
// end::class[]