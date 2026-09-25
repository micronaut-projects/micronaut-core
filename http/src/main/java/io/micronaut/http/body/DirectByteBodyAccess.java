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
import org.jspecify.annotations.Nullable;

/**
 * A request that can give access to the bytes of the body it was received with, e.g. a server
 * request or a request {@link io.micronaut.http.HttpRequest#mutate() mutated} from one, so that an
 * HTTP client can relay them unchanged.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface DirectByteBodyAccess {
    /**
     * The bytes of the body this request was received with.
     *
     * @return The bytes, or {@code null} if there are none to relay, e.g. because the body of this
     * request was replaced
     */
    @Nullable
    default ByteBody byteBodyDirect() {
        return null;
    }
}
