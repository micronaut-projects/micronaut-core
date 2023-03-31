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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * <p>Base type for a representation of an HTTP request body.</p>
 * <p>Exactly one HttpBody holds control over a request body at a time. When a transformation of
 * the body is performed, e.g. multipart processing, the new HttpBody takes control and the old one
 * becomes invalid. The new body will be available via {@link #next()}.</p>
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public interface HttpBody {
    /**
     * Release this body and any downstream representations.
     */
    void release();

    /**
     * Get the next representation this body was transformed into, if any.
     *
     * @return The next representation, or {@code null} if this body has not been transformed
     */
    @Nullable
    HttpBody next();
}
