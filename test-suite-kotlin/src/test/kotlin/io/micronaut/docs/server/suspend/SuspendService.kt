package io.micronaut.docs.server.suspend

import io.micronaut.retry.annotation.Retryable
import kotlinx.coroutines.delay
import java.lang.RuntimeException
import javax.inject.Singleton

@Singleton
open class SuspendService {

    var counter1: Int = 0
    var counter2: Int = 0

    @Retryable
    open suspend fun delayedCalculation1(): String {
        if (counter1 != 2) {
            delay(1)
            counter1++
            throw RuntimeException("error $counter1")
        }
        delay(1)
        return "delayedCalculation1"
    }

    @Retryable
    open suspend fun delayedCalculation2(): String {
        if (counter2 != 2) {
            delay(1)
            counter2++
            throw RuntimeException("error $counter2")
        }
        delay(1)
        return "delayedCalculation2"
    }

}