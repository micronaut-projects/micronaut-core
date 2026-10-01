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
package io.micronaut.http.server.executeon

import org.junit.jupiter.api.Assertions

internal const val IO = "io-executor-thread-"
internal const val LOOP = "default-eventLoopGroup"

internal fun assertOnIoExecutor(thread: String, what: String) =
    Assertions.assertTrue(thread.startsWith(IO), "$what did not run on the IO executor: '$thread'")

internal fun assertNotOnIoExecutor(thread: String, what: String) =
    Assertions.assertFalse(thread.startsWith(IO), "$what unexpectedly ran on the IO executor: '$thread'")

// AUTO and BLOCKING pick virtual threads on JDK 21+ and the IO executor on 17, so only "not the event loop" holds
internal fun assertOffloaded(thread: String, what: String) =
    Assertions.assertFalse(thread.startsWith(LOOP), "$what ran on the event loop: '$thread'")

internal fun assertOnEventLoop(thread: String, what: String) =
    Assertions.assertTrue(thread.startsWith(LOOP), "$what did not run on the event loop: '$thread'")
