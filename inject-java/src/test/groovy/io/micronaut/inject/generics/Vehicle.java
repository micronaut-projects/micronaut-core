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
package io.micronaut.inject.generics;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class Vehicle {
    private final Engine<V8> engine;

    @Inject
    List<Engine<V6>> v6Engines;

    private Engine<V8> anotherV8;

    @Inject
    public Vehicle(Engine<V8> engine) {// <4>
        this.engine = engine;
    }

    public String start() {
        return engine.start();// <5>
    }

    @Inject
    public void setAnotherV8(Engine<V8> anotherV8) {
        this.anotherV8 = anotherV8;
    }

    public Engine<V8> getAnotherV8() {
        return anotherV8;
    }

    public Engine<V8> getEngine() {
        return engine;
    }
}
