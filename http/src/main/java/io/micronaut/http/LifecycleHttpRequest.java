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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;

/**
 * A server HTTP request that cleans up resources at the end of its lifecycle.
 *
 * @param <B> The request body
 * @since 5.0.0
 */
@Experimental
public interface LifecycleHttpRequest<B> extends HttpRequest<B> {
    /**
     * Mark a resource for disposal when the request lifecycle ends. The task must be non-blocking.
     *
     * @param dispose The task to run for disposal
     */
    void addDisposalResource(Runnable dispose);
}
