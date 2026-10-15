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
 * The attributes of a response that tell the server how to write it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ServerResponseAttributes {

    /**
     * A {@link Boolean} attribute of a response: {@code true} to write its body as it is, never
     * compressed by the server, e.g. a response a proxy relays, whose upstream chose not to
     * compress it and whose {@code Content-Length} must stay the upstream's.
     */
    public static final String SKIP_COMPRESSION = "micronaut.http.server.skip-compression";

    private ServerResponseAttributes() {
    }
}
