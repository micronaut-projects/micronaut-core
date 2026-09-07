package io.micronaut.docs.expressions

import io.micronaut.context.annotation.Requires
//tag::imports[]
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
//end::imports[]

@Requires(property = "spec.name", value = "ExampleJobTest")
//tag::clazz[]
@Singleton
class ExampleJob {
    private var jobRan = false

    var paused = true // <2>
        private set

    @Scheduled(
        fixedRate = "1s",
        condition = "#{!this.paused}") // <1>
    fun run() {
        println("Job Running")
        jobRan = true
    }

    fun hasJobRun(): Boolean = jobRan

    fun unpause() {
        paused = false
    }

    fun pause() {
        paused = true
    }
}
//end::clazz[]
