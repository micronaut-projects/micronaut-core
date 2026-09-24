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
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;

/**
 * A {@link HttpRequest#mutate() mutable view} of a request that knows whether its body was set,
 * see {@link MutableHttpRequest#body(Object)}, even to {@code null}: the body of a view whose
 * body was set is that object, e.g. none when a filter cleared it, and not the bytes of the
 * request.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface BodyChangeAwareRequest {

    /**
     * Whether the body of the view was set, which may be {@code null}.
     *
     * @return {@code true} if the body was set
     */
    boolean isBodySet();

    /**
     * Whether a filter set the body of the given request or of a request it wraps, down to the
     * server request whose bytes are its body: its body is then the object the filter set, and
     * not those bytes.
     *
     * @param request The request
     * @return {@code true} if the body was set
     */
    static boolean isBodySet(HttpRequest<?> request) {
        HttpRequest<?> current = request;
        while (true) {
            if (current instanceof BodyChangeAwareRequest aware) {
                if (aware.isBodySet()) {
                    return true;
                }
            } else if (current instanceof ServerHttpRequest<?>) {
                // the bytes of this request are the body
                return false;
            }
            if (current instanceof HttpRequestWrapper<?> wrapper) {
                current = wrapper.getDelegate();
            } else {
                return false;
            }
        }
    }
}
