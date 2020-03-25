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
package io.micronaut.docs.config.value;

import io.micronaut.context.annotation.Value;

// tag::imports[]
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class EngineImpl implements Engine {

    @Value("${my.engine.cylinders:6}") // <1>
    protected int cylinders;

    @Override
    public int getCylinders() {
        return this.cylinders;
    }

    public String start() {// <2>
        return "Starting V" + getCylinders() + " Engine";
    }

}
// end::class[]