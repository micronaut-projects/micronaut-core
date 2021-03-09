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
package io.micronaut.docs.inject.generics;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


@MicronautTest
public class VehicleSpec {
    @Inject Vehicle vehicle;
    @Inject List<Engine<V6>> v6Engines;

    @Test
    public void testStartVehicle() {
        assertEquals("Starting V8", vehicle.start());
        assertEquals(1, v6Engines.size());
        assertEquals(6, v6Engines.iterator().next().getCylinders());
    }
}
