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
package io.micronaut.context.propagation;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContextConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Applies the {@code micronaut.propagation} configuration to {@link PropagatedContextConfiguration}.
 */
@Context
@Internal
@ConfigurationProperties(PropagatedContextConfigurationProperties.PREFIX)
public final class PropagatedContextConfigurationProperties {

    public static final String PREFIX = "micronaut";

    private PropagatedContextConfiguration.Mode propagation = PropagatedContextConfiguration.Mode.THREAD_LOCAL;

    public PropagatedContextConfiguration.Mode getPropagation() {
        return propagation;
    }

    public void setPropagation(PropagatedContextConfiguration.Mode propagation) {
        this.propagation = propagation;
    }

    @PostConstruct
    void apply() {
        PropagatedContextConfiguration.set(propagation);
    }

    @PreDestroy
    void reset() {
        PropagatedContextConfiguration.reset();
    }

}
