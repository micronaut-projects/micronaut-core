/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;

import java.util.Optional;

/**
 * Client-related attribute accessors.
 *
 * @author Jonas Konrad
 * @since 4.8.0
 */
@SuppressWarnings("removal")
public final class ClientAttributes {
    private ClientAttributes() {
    }

    /**
     * Set the client service ID.
     *
     * @param request   The request
     * @param serviceId The client service ID
     * @see io.micronaut.http.BasicHttpAttributes#getServiceId(HttpRequest)
     */
    public static void setServiceId(@NonNull HttpRequest<?> request, @NonNull String serviceId) {
        request.setAttribute(HttpAttributes.SERVICE_ID, serviceId);
    }

    /**
     * Get the invocation context.
     *
     * @param request The request
     * @return The invocation context, if present
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @NonNull
    public static Optional<MethodInvocationContext<?, ?>> getInvocationContext(@NonNull HttpRequest<?> request) {
        return (Optional) request.getAttribute(HttpAttributes.INVOCATION_CONTEXT, MethodInvocationContext.class);
    }

    /**
     * Set the invocation context.
     *
     * @param request           The request
     * @param invocationContext The invocation context
     */
    public static void setInvocationContext(@NonNull HttpRequest<?> request, @NonNull MethodInvocationContext<?, ?> invocationContext) {
        request.setAttribute(HttpAttributes.INVOCATION_CONTEXT, invocationContext);
    }
}
