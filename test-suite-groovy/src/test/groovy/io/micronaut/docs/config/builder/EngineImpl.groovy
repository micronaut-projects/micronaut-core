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
package io.micronaut.docs.config.builder

import javax.annotation.concurrent.Immutable

// tag::class[]
@Immutable
class EngineImpl implements Engine {
    private final int cylinders
    private final String manufacturer
    private final CrankShaft crankShaft
    private final SparkPlug sparkPlug

    EngineImpl(String manufacturer, int cylinders, CrankShaft crankShaft, SparkPlug sparkPlug) {
        this.crankShaft = crankShaft
        this.cylinders = cylinders
        this.manufacturer = manufacturer
        this.sparkPlug = sparkPlug
    }

    @Override
    int getCylinders() {
        return cylinders
    }

    @Override
    String start() {
        "${manufacturer} Engine Starting V${cylinders} [rodLength=${crankShaft.rodLength.orElse(6.0d)}, sparkPlug=${sparkPlug}]"
    }

    static Builder builder() {
        return new Builder()
    }

    static final class Builder {
        private String manufacturer = "Ford"
        private int cylinders

        Builder withManufacturer(String manufacturer) {
            this.manufacturer = manufacturer
            this
        }

        Builder withCylinders(int cylinders) {
            this.cylinders = cylinders
            this
        }

        EngineImpl build(CrankShaft.Builder crankShaft, SparkPlug.Builder sparkPlug) {
            new EngineImpl(manufacturer, cylinders, crankShaft.build(), sparkPlug.build())
        }
    }
}
// end::class[]