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
package io.micronaut.docs.config.immutable;


// tag::imports[]
import io.micronaut.context.annotation.*;
import io.micronaut.core.bind.annotation.Bindable;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.validation.constraints.*;
import java.util.Optional;
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
public class EngineConfig {

    private final String manufacturer;
    private final int cylinders;
    private final CrankShaft crankShaft;

    @ConfigurationInject // <2>
    public EngineConfig(
            @Bindable(defaultValue = "Ford") @NotBlank String manufacturer, // <3>
            @Min(1L) int cylinders, // <4>
            @NotNull CrankShaft crankShaft) {
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
    public static class CrankShaft { // <5>
        private final Double rodLength; // <6>

        @ConfigurationInject
        public CrankShaft(@Nullable Double rodLength) {
            this.rodLength = rodLength;
        }

        public Optional<Double> getRodLength() {
            return Optional.ofNullable(rodLength);
        }
    }
}
// end::class[]
