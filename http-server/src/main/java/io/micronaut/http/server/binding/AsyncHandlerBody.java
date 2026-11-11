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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.ReleasableRequestBody;

import java.util.concurrent.CompletionStage;

/**
 * The body an asynchronous handler receives, as its route sees it: when the handler completed,
 * the server releases what the handler's reads of the body left open, e.g. the parts of a form it
 * did not read, before the response is written. The {@link AsyncRequestBodyArgumentBinder} adds
 * the body of a controller method to the request, see
 * {@link io.micronaut.http.BasicHttpAttributes#addRouteBody}, and the
 * {@link io.micronaut.http.server.RouteExecutor} releases it once the value or stage the method
 * returned completed, normally or exceptionally, or the method threw, or, for a streamed
 * response, once its stream ended. A request filter method
 * releases its bodies when the stage it returned completed, before the filter chain continues.
 * What is still open when the request ends, e.g. the body of a route that was not invoked, is
 * released then.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface AsyncHandlerBody extends ReleasableRequestBody {

    /**
     * Release what the read of the body left open: the parts of a form, the elements of the
     * body, or an operation on the body that is still running, e.g. a file being written, which
     * is aborted and deleted. The reads of the copies of the body are released too.
     *
     * @return Completes when released, exceptionally when releasing failed
     */
    @Override
    CompletionStage<Void> releaseBody();
}
