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
package io.micronaut.http.server.multipart;

import io.micronaut.core.annotation.Internal;
import org.reactivestreams.Publisher;

/**
 * A publisher of form fields that releases the fields it holds itself: those buffered when its
 * subscriber cancels or it fails, and those offered afterwards. Its fields are read without a
 * Reactor discard hook, which a generic publisher, e.g. one that delegates to a Reactor
 * publisher, needs to release the fields queued behind the one being read.
 *
 * @param <T> The type of the fields
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface ReleasingFieldPublisher<T> extends Publisher<T> {
}
