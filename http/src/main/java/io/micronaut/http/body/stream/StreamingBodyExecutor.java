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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;

import java.util.concurrent.Executor;

/**
 * The thread a {@link io.micronaut.http.body.ByteBodyFactory#createStreamingBody streaming body}
 * must be fed on: the {@link BufferConsumer} of the body of a runtime with an event loop (Netty)
 * may only be called on that loop, so a producer on another thread hands its calls to this
 * executor. Tasks run in the order they were submitted.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface StreamingBodyExecutor extends Executor {

    /**
     * Whether the current thread is an event loop thread, which must not block: this executor's
     * thread, or the thread of another event loop of the runtime.
     *
     * @return {@code true} if blocking the current thread would block an event loop
     */
    boolean isEventLoopThread();
}
