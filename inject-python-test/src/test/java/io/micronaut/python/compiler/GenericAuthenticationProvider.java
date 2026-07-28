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
package io.micronaut.python.compiler;

/**
 * Generic authentication provider used by Python bridge signature tests.
 *
 * @param <T> The request context type
 * @param <I> The identity type
 * @param <S> The secret type
 */
public interface GenericAuthenticationProvider<T, I, S> {

    /**
     * Authenticate the request.
     *
     * @param requestContext The request context
     * @param authRequest The authentication request
     * @return The authentication result
     */
    String authenticate(T requestContext, GenericAuthenticationRequest<I, S> authRequest);
}
