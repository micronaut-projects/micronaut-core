package io.micronaut.annotation.processing.test;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

public class TestJavaBeans {

    @Singleton
    public static class ScheduledProtectedBean {
        @Scheduled(initialDelay = "1y", fixedDelay = "1y")
        protected void protectedExecutable() {
        }
    }

    @Singleton
    public static class ScheduledPublicBean {
        @Scheduled(initialDelay = "1y", fixedDelay = "1y")
        public void protectedExecutable() {
        }
    }

    @Singleton
    public static class ScheduledDefaultBean {
        @Scheduled(initialDelay = "1y", fixedDelay = "1y")
        void protectedExecutable() {
        }
    }
}
