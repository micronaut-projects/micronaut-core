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
    var paused = true
    private var jobRan = false
    @Scheduled(
        fixedRate = "1s",
        condition = "#{!this.paused}") // <1>
    fun run() {
        println("Job Running")
        jobRan = true
    }

    fun hasJobRun(): Boolean {
        return jobRan
    }

    fun unpause() {
        paused = false
    }

    fun pause() {
        paused = true
    }
}
//end::clazz[]
