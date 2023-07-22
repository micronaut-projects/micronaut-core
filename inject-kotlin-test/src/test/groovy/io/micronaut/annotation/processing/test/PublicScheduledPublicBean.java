package io.micronaut.annotation.processing.test;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
public class PublicScheduledPublicBean {
    @Scheduled(initialDelay = "1y", fixedDelay = "1y")
    public void protectedExecutable() {
    }
}
