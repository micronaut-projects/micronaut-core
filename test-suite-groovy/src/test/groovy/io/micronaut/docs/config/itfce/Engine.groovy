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
package io.micronaut.docs.config.itfce

import javax.inject.Singleton

@Singleton
class Engine {
    private final EngineConfig config

    Engine(EngineConfig config) {// <1>
        this.config = config
    }

    int getCylinders() {
        return config.cylinders
    }

    String start() {// <2>
        return "$config.manufacturer Engine Starting V$config.cylinders [rodLength=${config.crankShaft.rodLength.orElse(6.0d)}]"
    }

    final EngineConfig getConfig() {
        return config
    }
}
