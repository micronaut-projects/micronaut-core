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
package io.micronaut.http.form;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import org.reactivestreams.Publisher;

/**
 * A request that is <i>capable</i> of parsing form data, though the request does not necessarily
 * have to contain form data.
 *
 * @param <B> The body type
 */
@Experimental
public interface FormCapableHttpRequest<B> extends ServerHttpRequest<B>, LifecycleHttpRequest<B> {
    /**
     * Get the raw form field publisher. Can only be subscribed to once.
     *
     * @return The raw form fields
     * @throws IllegalStateException If this request does not contain a form body
     */
    Publisher<RawFormField> getRawFormFields() throws IllegalStateException;

    /**
     * Check whether this request contains a form body (url encoded or multipart).
     *
     * @return {@code true} if this is a form request
     */
    boolean hasFormBody();
}
