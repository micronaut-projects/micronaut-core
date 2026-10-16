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
package io.micronaut.dev;

import io.micronaut.core.annotation.Internal;

import java.io.IOException;

/**
 * The calls {@link FsEventStreams} makes to FSEvents and to the run loop its streams are scheduled on:
 * {@link JnaFsEvents} makes them to CoreServices through JNA. {@link FsEventStreams} decides on which thread, and in
 * which order, they are made.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
interface FsEventsApi {

    /**
     * Called once, on the run loop thread, before any other call.
     *
     * @return The run loop of the calling thread
     */
    RunLoop currentRunLoop();

    /**
     * Creates a stream of the events under a tree. Called on the run loop thread.
     *
     * @param realPath The real path of the tree
     * @param latency How long FSEvents holds the events that follow a first one, in seconds
     * @param events Told of the events of the stream, on the run loop thread, from the stream's start until its release
     * @return The stream, neither scheduled nor started
     * @throws IOException if the stream cannot be created
     */
    NativeStream create(String realPath, double latency, FsEventStreams.Events events) throws IOException;

    /**
     * The run loop of the run loop thread.
     */
    interface RunLoop {

        /**
         * Runs the loop, reporting the events of the streams scheduled on it, until it is stopped or the time passed.
         * Called on the run loop thread.
         *
         * @param seconds How long to run at most
         */
        void run(double seconds);

        /**
         * Stops the current run of the loop, or the next one. Called from any thread.
         */
        void stop();
    }

    /**
     * A stream, which holds what the native side calls back until it is released.
     */
    interface NativeStream {

        /**
         * Schedules the stream on a run loop.
         *
         * @param runLoop The run loop of the run loop thread
         */
        void schedule(RunLoop runLoop);

        /**
         * @return Whether the stream started
         */
        boolean start();

        /**
         * Stops a stream that started.
         */
        void stop();

        /**
         * Unschedules the stream from its run loop.
         */
        void invalidate();

        /**
         * Releases the stream: the native side no longer calls back.
         */
        void release();
    }
}
