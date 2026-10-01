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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.util.Optional;

/**
 * Adapted from the {@code io.micronaut.docs.config.itfce} example in the {@code test-suite}
 * project.
 */
@ConfigurationProperties("my.itfce.engine")
public interface InterfaceEngineConfig {

    @Bindable(defaultValue = "Ford")
    String getManufacturer();

    int getCylinders();

    CrankShaft getCrankShaft();

    @ConfigurationProperties("crank-shaft")
    interface CrankShaft {
        Optional<Double> getRodLength();
    }
}
