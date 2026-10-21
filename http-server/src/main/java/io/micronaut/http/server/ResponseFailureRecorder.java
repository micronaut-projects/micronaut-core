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
package io.micronaut.http.server;

import io.micronaut.core.annotation.Internal;

/**
 * Implemented by a server request that reports the failure of its response to the callbacks of
 * {@link io.micronaut.http.ServerHttpRequest#whenResponseCompleted}.
 *
 * @since 5.3.0
 */
@Internal
public interface ResponseFailureRecorder {
    /**
     * Remember why the response to this request failed, e.g. the error that is answered with the
     * default error response. The first failure wins.
     *
     * @param cause The failure
     */
    void responseFailed(Throwable cause);
}
