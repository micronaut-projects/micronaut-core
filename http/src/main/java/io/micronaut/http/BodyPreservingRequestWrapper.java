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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;

/**
 * Marks a {@link HttpRequestWrapper} that never replaces the body of the request it wraps: the
 * bytes of the wrapped request are its body, whatever its {@code getBody()} methods return, e.g.
 * a view that hides a body decoded before so that it is read again from the bytes.
 *
 * <p>{@link HttpRequestWrapper#replacesBody(HttpRequest)} does not call {@code getBody()} on
 * such a wrapper. Without the marker, the body of a wrapper whose class overrides
 * {@code getBody()} is compared by identity with the body of the request it wraps, which decodes
 * that body. Decoding may consume the bytes of the request, e.g. the input stream of a servlet
 * request, so that they cannot be read again, or produce a new object on each call, so that a
 * wrapper that kept the body looks like one that replaced it.</p>
 *
 * @since 5.3.0
 */
@Internal
public interface BodyPreservingRequestWrapper {
}
