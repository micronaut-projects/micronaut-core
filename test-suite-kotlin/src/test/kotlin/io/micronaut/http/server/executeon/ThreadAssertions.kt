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
