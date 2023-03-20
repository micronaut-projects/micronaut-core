package io.micronaut.docs.expressions;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
public class ExampleJob {
    private boolean jobRan = false;

    @Scheduled(
        fixedRate = "1s",
        condition = "#{ #jobControl.paused != true }") // <1>
    void run(ExampleJobControl jobControl) {
        System.out.println("Job Running");
        this.jobRan = true;
    }

    public boolean hasJobRun() {
        return jobRan;
    }
}

@Singleton
class ExampleJobControl { // <2>
    private boolean paused = true;

    public boolean isPaused() {
        return paused;
    }

    public void unpause() {
        paused = false;
    }

    public void pause() {
        paused = true;
    }
}
