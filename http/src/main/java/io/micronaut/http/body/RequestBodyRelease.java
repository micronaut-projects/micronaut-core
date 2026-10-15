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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The release of a {@link ReleasableRequestBody} when the flow of the method that was invoked
 * with it ends, see {@link ReleasableRequestBody#releaseAfter}: the flow completed, or it was
 * cancelled, whichever is first. The body is released once: a flow that is cancelled while it
 * completes, or after it completed, does not release again.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
final class RequestBodyRelease {
    private static final Logger LOG = LoggerFactory.getLogger(ReleasableRequestBody.class);

    private final ReleasableRequestBody body;
    private final AtomicBoolean started = new AtomicBoolean();
    private final CompletableFuture<Void> released = new CompletableFuture<>();

    RequestBodyRelease(ReleasableRequestBody body) {
        this.body = body;
    }

    /**
     * Release the body because the flow completed, unless it is released, or being released,
     * already.
     *
     * @return Completes when released, exceptionally when releasing failed
     */
    CompletionStage<Void> whenCompleted() {
        if (started.compareAndSet(false, true)) {
            release();
        }
        return released;
    }

    /**
     * Release the body because the flow was cancelled, unless it is released, or being released,
     * already. Nobody waits for the result of a cancelled flow: a failure to release is logged.
     */
    void whenCancelled() {
        if (started.compareAndSet(false, true)) {
            release();
            released.whenComplete((ignored, error) -> {
                if (error != null && LOG.isWarnEnabled()) {
                    LOG.warn("Failed to release what the reads of a request body left open when the method that was invoked with it was cancelled", error);
                }
            });
        }
    }

    private void release() {
        CompletionStage<Void> stage;
        try {
            stage = body.releaseBody();
        } catch (Throwable e) {
            released.completeExceptionally(e);
            return;
        }
        stage.whenComplete((ignored, error) -> {
            if (error != null) {
                released.completeExceptionally(error);
            } else {
                released.complete(null);
            }
        });
    }
}
