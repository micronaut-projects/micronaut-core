package io.micronaut.docs.aop.scheduled;

import io.micronaut.scheduling.annotation.Scheduled;

import javax.inject.Singleton;

@Singleton
public class ScheduledExample {


    // tag::fixedRate[]
    @Scheduled(fixedRate = "5m")
    void everyFiveMinutes() {
        System.out.println("Executing everyFiveMinutes()");
    }
    // end::fixedRate[]

    // tag::fixedDelay[]
    @Scheduled(fixedDelay = "5m")
    void fiveMinutesAfterLastExecution() {
        System.out.println("Executing fiveMinutesAfterLastExecution()");
    }
    // end::fixedDelay[]

    // tag::cron[]
    @Scheduled(cron = "0 15 10 ? * MON" )
    void everyMondayAtTenFifteenAm() {
        System.out.println("Executing everyMondayAtTenFifteenAm()");
    }
    // end::cron[]

    // tag::initialDelay[]
    @Scheduled(initialDelay = "1m" )
    void onceOneMinuteAfterStartup() {
        System.out.println("Executing onceOneMinuteAfterStartup()");
    }
    // end::initialDelay[]

    // tag::configured[]
    @Scheduled( fixedRate = "${my.task.rate:5m}",
                initialDelay = "${my.task.delay:1m}" )
    void configuredTask() {
        System.out.println("Executing configuredTask()");
    }
    // end::configured[]
}
