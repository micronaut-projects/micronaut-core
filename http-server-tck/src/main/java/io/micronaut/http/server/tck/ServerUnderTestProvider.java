/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.tck;

import io.micronaut.core.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides a server to test.
 * @author Sergio del Amo
 * @since 3.8.0
 */
@FunctionalInterface
public interface ServerUnderTestProvider {

    /**
     *
     * @param properties Properties supplied to application context started.
     * @return The server under test.
     */
    @NonNull
    ServerUnderTest getServer(Map<String, Object> properties);

    /**
     *
     * @param specName value of {@literal spec.name} property used to avoid bean pollution.
     * @param properties Properties supplied to application context started.
     * @return Server under test
     */
    @NonNull
    default ServerUnderTest getServer(String specName, Map<String, Object> properties) {
        Map<String, Object> props = properties != null ? new HashMap<>(properties) : new HashMap<>();
        if (specName != null) {
            props.put("spec.name", specName);
        }
        return getServer(props);
    }

    /**
     *
     * @param specName value of {@literal spec.name} property used to avoid bean pollution.
     * @return Server under test
     */
    @NonNull
    default ServerUnderTest getServer(String specName) {
        return getServer(specName, Collections.emptyMap());
    }
}
