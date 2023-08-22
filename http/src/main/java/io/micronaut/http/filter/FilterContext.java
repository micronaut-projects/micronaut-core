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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

import java.util.Objects;

/**
 * The filter context.
 *
 * @param request           The request
 * @param response          The response
 * @param propagatedContext The propagated context
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
record FilterContext(@NonNull HttpRequest<?> request,
                     @Nullable HttpResponse<?> response,
                     @NonNull PropagatedContext propagatedContext) {

    FilterContext(HttpRequest<?> request, PropagatedContext propagatedContext) {
        this(request, null, propagatedContext);
    }

    FilterContext withRequest(@NonNull HttpRequest<?> request) {
        if (this.request == request) {
            return this;
        }
        if (response != null) {
            throw new IllegalStateException("Cannot modify the request after response is set!");
        }
        Objects.requireNonNull(request);
        return new FilterContext(request, response, propagatedContext);
    }

    FilterContext withResponse(@NonNull HttpResponse<?> response) {
        if (this.response == response) {
            return this;
        }
        Objects.requireNonNull(response);
        return new FilterContext(request, response, propagatedContext);
    }

    FilterContext withPropagatedContext(@NonNull PropagatedContext propagatedContext) {
        if (this.propagatedContext == propagatedContext) {
            return this;
        }
        Objects.requireNonNull(propagatedContext);
        return new FilterContext(request, response, propagatedContext);
    }

}
