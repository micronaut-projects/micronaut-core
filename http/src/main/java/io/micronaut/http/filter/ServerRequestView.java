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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;

/**
 * A {@link HttpRequest#mutate() mutable view} of a server request that is not itself a
 * {@link ServerHttpRequest}, e.g. the view a filter method continued with: the server request
 * whose bytes are the body of the view, unless the body of the view was set, see
 * {@link BodyChangeAwareRequest}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface ServerRequestView {

    /**
     * The server request this is the mutable view of.
     *
     * @return The server request
     */
    ServerHttpRequest<?> serverRequest();
}
