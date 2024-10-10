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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.ByteBody;

import java.io.Closeable;

/**
 * Special response type that contains the encoded response bytes. Responses of this type must also
 * be closed if their {@link #byteBody()} is not used.
 *
 * @param <B> The original (non-encoded) body type
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Experimental
public interface ByteBodyHttpResponse<B> extends HttpResponse<B>, Closeable {
    /**
     * The body bytes.
     *
     * @return The bytes
     */
    @NonNull
    ByteBody byteBody();

    /**
     * Close this response.
     */
    @Override
    void close();
}
