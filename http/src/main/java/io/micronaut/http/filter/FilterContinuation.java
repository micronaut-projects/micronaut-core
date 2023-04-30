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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;

/**
 * A filter continuation gives can be declared as a parameter on a
 * {@link io.micronaut.http.annotation.RequestFilter filter method}. The filter method gets
 * "delayed" access to the parameter it is requesting. For example, a request filter can declare a
 * continuation that returns the response. When the filter calls the continuation, other downstream
 * filters and the final request call will be executed. The continuation will return once the
 * response has been received and processed by downstream filters.<br>
 * A continuation can either return the value immediately (e.g.
 * {@code FilterContinuation<HttpResponse<?>>}), in which case the call to {@link #proceed()} will
 * block, or it can return a reactive wrapper (e.g.
 * {@code FilterContinuation<Publisher<HttpResponse<?>>>}). With a reactive wrapper,
 * {@link #proceed()} will not block, and downstream processing will happen asynchronously (after
 * the reactive stream is subscribed to).
 *
 * @param <R> The type to return in {@link #proceed()}
 */
@Experimental
public interface FilterContinuation<R> {
    /**
     * Update the request for downstream processing.
     *
     * @param request The new request
     * @return This continuation, for call chaining
     */
    @NonNull
    FilterContinuation<R> request(@NonNull HttpRequest<?> request);

    /**
     * Proceed processing downstream of this filter. If {@link R} is not a reactive type, this
     * method will block until downstream processing completes, and may throw an exception if there
     * is a failure. <b>Blocking netty event loop threads can lead to bugs, so any filter that
     * may block in the netty HTTP server should use
     * {@link io.micronaut.scheduling.annotation.ExecuteOn} to avoid running on the event loop.</b>
     * <br>
     * If {@link R} is a reactive type, this method will return immediately. Downstream processing
     * will happen when the reactive stream is subscribed to, and the reactive stream will produce
     * the downstream result when available.
     *
     * @return The downstream result, or reactive stream wrapper thereof
     */
    @NonNull
    R proceed();
}
