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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The runtime-neutral {@link ProxyHttpClient#discard discard} of a proxied response that is not
 * relayed: the response is closed if it carries bytes, and a closeable body is closed.
 *
 * @author Sergio del Amo
 * @since 5.3.0
 */
@Internal
final class ProxyResponses {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyHttpClient.class);

    private ProxyResponses() {
    }

    /**
     * Release what the given response holds. Never throws.
     *
     * @param response The response that will not be relayed
     */
    static void discard(HttpResponse<?> response) {
        try {
            HttpResponse<?> current = response;
            while (!(current instanceof ByteBodyHttpResponse<?>) && current instanceof HttpResponseWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            }
            if (current instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                byteBodyResponse.close();
            }
            if (current.getBody().orElse(null) instanceof AutoCloseable body) {
                body.close();
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to discard the proxied response {}", response, e);
            }
        }
    }
}
