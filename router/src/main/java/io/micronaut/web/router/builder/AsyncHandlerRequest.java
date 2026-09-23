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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;

import java.util.concurrent.CompletionStage;

/**
 * The request an asynchronous handler receives, as its route sees it: when the stage the handler
 * returned completes, the route releases what the handler's read of the body left open, e.g. the
 * parts of a form it did not read, before the response is written.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface AsyncHandlerRequest {

    /**
     * Release what the read of the body left open: the parts of a form, the elements of the
     * body, or an operation on the body that is still running.
     *
     * @return Completes when released, exceptionally when releasing failed
     */
    CompletionStage<Void> releaseBody();
}
