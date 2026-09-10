/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.jdt.beans;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Immutable configuration, adapted from the {@code io.micronaut.docs.config.immutable} example in
 * the {@code test-suite} project.
 */
@ConfigurationProperties("my.engine")
public class EngineConfig {

    private final String manufacturer;
    private final int cylinders;
    private final CrankShaft crankShaft;

    @ConfigurationInject
    public EngineConfig(
        @Bindable(defaultValue = "Ford") String manufacturer,
        int cylinders,
        CrankShaft crankShaft) {
        this.manufacturer = manufacturer;
        this.cylinders = cylinders;
        this.crankShaft = crankShaft;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public int getCylinders() {
        return cylinders;
    }

    public CrankShaft getCrankShaft() {
        return crankShaft;
    }

    @ConfigurationProperties("crank-shaft")
    public static class CrankShaft {

        private final Double rodLength;

        @ConfigurationInject
        public CrankShaft(@Nullable Double rodLength) {
            this.rodLength = rodLength;
        }

        public Optional<Double> getRodLength() {
            return Optional.ofNullable(rodLength);
        }
    }
}
