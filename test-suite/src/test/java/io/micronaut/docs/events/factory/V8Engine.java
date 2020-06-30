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
package io.micronaut.docs.events.factory;

// tag::class[]
public class V8Engine implements Engine {
    private final int cylinders = 8;
    private double rodLength; // <1>

    public V8Engine(double rodLength) {
        this.rodLength = rodLength;
    }

    @Override
    public String start() {
        return "Starting V" + String.valueOf(getCylinders()) + " [rodLength=" + String.valueOf(getRodLength()) + "]";
    }

    @Override
    public final int getCylinders() {
        return cylinders;
    }

    public double getRodLength() {
        return rodLength;
    }

    public void setRodLength(double rodLength) {
        this.rodLength = rodLength;
    }
}
// end::class[]