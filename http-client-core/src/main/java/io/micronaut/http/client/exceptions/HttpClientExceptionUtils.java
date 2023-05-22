/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.exceptions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;

/**
 * Utility Class to work with {@link HttpClientException}.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
public final class HttpClientExceptionUtils {

    private HttpClientExceptionUtils() {

    }

    /**
     * Sets {@link HttpClientException#setServiceId(String)} for a {@link HttpClientException}.
     * @param exc HTTP Client Exception
     * @param clientId Client Identifier
     * @param configuration HttpClientConfiguration
     * @return an HTTP Client Exception
     * @param <E> HTTP Client Exception
     */
    public static <E extends HttpClientException> E populateServiceId(E exc,
                                                                   @Nullable String clientId,
                                                                   @Nullable HttpClientConfiguration configuration) {
        if (clientId != null) {
            exc.setServiceId(clientId);
        } else if (configuration instanceof ServiceHttpClientConfiguration clientConfiguration) {
            exc.setServiceId(clientConfiguration.getServiceId());
        }
        return exc;
    }
}
