/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;

/**
 * User events of {@link PipeliningServerHandler}, fired from the head of the pipeline, that tell
 * the handlers before it whether the request body is still read: e.g. a completed HTTP/3
 * response stops the rest of the request only once nobody reads it.
 *
 * @since 5.3.0
 */
@Internal
public enum RequestBodyEvent {
    /**
     * The request body streams to a consumer that may still read it after the response.
     */
    STREAMING,
    /**
     * The request body is not read any more: it was discarded, or it ended.
     */
    DONE
}
