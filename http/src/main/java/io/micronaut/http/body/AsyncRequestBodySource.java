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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * A request received by a server that gives the {@link AsyncRequestBody} of the requests that
 * wrap it, see {@link AsyncRequestBody#of(HttpRequest)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface AsyncRequestBodySource {

    /**
     * @param request The request whose body is read: this request, or a request that wraps it
     * @return The body, or {@code null} if this request cannot give one
     */
    @Nullable AsyncRequestBody asyncRequestBody(HttpRequest<?> request);
}
