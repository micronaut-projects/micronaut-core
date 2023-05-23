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
    boolean paused = true // <2>
    private boolean jobRan = false

    @Scheduled(
            fixedRate = "1s",
            condition = '#{!this.paused}') // <1>
    void run() {
        println("Job Running")
        this.jobRan = true
    }

    boolean hasJobRun() {
        return jobRan
    }

    void unpause() {
        paused = false
    }

    void pause() {
        paused = true
    }
}
//end::clazz[]
