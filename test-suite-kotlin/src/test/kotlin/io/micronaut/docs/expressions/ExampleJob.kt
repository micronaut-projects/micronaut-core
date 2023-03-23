package io.micronaut.docs.expressions

import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

@Singleton
class ExampleJob {
    private var jobRan = false
    @Scheduled(
        fixedRate = "1s",
        condition = "#{!jobControl.paused}") // <1>
    fun run(jobControl: ExampleJobControl) {
        println("Job Running")
        jobRan = true
    }

    fun hasJobRun(): Boolean {
        return jobRan
    }
}

@Singleton
class ExampleJobControl { // <2>
    var paused = true

    fun unpause() {
        paused = false
    }

    fun pause() {
        paused = true
    }
}
