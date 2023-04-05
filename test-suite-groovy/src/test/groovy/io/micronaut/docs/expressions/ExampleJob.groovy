package io.micronaut.docs.expressions

import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

@Singleton
class ExampleJob {
    private boolean jobRan = false

    @Scheduled(
            fixedRate = "1s",
            condition = '#{!jobControl.paused}') // <1>
    void run(ExampleJobControl jobControl) {
        System.out.println("Job Running")
        this.jobRan = true
    }

    boolean hasJobRun() {
        return jobRan
    }
}

@Singleton
class ExampleJobControl { // <2>
    private boolean paused = true

    boolean isPaused() {
        return paused
    }

    void unpause() {
        paused = false
    }

    void pause() {
        paused = true
    }
}
