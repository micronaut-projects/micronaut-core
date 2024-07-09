/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

/**
 * Context for filter method execution.
 *
 * @param mutablePropagatedContext The propagation context
 * @param request                  The request
 * @param response                 The response, or {@code null} for request filters or when there is a failure
 * @param failure                  For response filters, optional failure
 * @param continuation             If a {@link MethodFilter#continuationCreator()} was specified, that continuation
 * @author Jonas Konrad
 * @since 4.6.0
 */
@Internal
public record FilterMethodContext(
    MutablePropagatedContext mutablePropagatedContext,
    HttpRequest<?> request,
    @Nullable HttpResponse<?> response,
    @Nullable Throwable failure,
    @Nullable MethodFilter.InternalFilterContinuation<?> continuation) {
}
