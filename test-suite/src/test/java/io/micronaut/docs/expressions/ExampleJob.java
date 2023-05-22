package io.micronaut.docs.expressions;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
public class ExampleJob {
    private boolean jobRan = false;
    private boolean paused = true;


    @Scheduled(
        fixedRate = "1s",
        condition = "#{!this.paused}") // <1>
    void run() {
        System.out.println("Job Running");
        this.jobRan = true;
    }

    public boolean isPaused() {
        return paused;
    } // <2>

    public boolean hasJobRun() {
        return jobRan;
    }

    public void unpause() {
        paused = false;
    }

    public void pause() {
        paused = true;
    }

}

