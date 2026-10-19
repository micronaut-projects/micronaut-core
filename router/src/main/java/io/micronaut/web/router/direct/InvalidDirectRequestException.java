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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Experimental;

/**
 * Thrown by a {@link DirectRequest} whose request target or query is not valid, e.g. a malformed
 * percent escape, found when it is first read: a server runtime may validate them lazily, so that
 * a request no direct route reads is never scanned. Such a request is never answered by a direct
 * route: the lookup does not match it, and a route whose function reads it declines it, so the
 * runtime answers it on its ordinary path, e.g. with {@code 400}. It has no stack trace.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public class InvalidDirectRequestException extends RuntimeException {

    /**
     * @param cause The error of the validation
     */
    public InvalidDirectRequestException(IllegalArgumentException cause) {
        super(cause.getMessage(), cause, false, false);
    }
}
