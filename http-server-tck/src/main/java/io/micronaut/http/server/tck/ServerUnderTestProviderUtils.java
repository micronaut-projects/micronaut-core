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

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Utility class to retrieve a {@link ServerUnderTestProvider} via a Service Loader.
 * @author Sergio del Amo
 * @since 3.8.0
 */
public final class ServerUnderTestProviderUtils {

    private ServerUnderTestProviderUtils() {
    }

    /**
     *
     * @return The first {@link ServerUnderTestProvider} loaded via a Service loader.
     * @throws ConfigurationException if it cannot load any {@link ServerUnderTestProvider}.
     */
    @NonNull
    public static ServerUnderTestProvider getServerUnderTestProvider() {
        Iterator<ServerUnderTestProvider> it = ServiceLoader.load(ServerUnderTestProvider.class).iterator();
        if (it.hasNext()) {
            return it.next();
        }
        throw new ConfigurationException("No ServiceUnderTestProvider present");
    }
}
