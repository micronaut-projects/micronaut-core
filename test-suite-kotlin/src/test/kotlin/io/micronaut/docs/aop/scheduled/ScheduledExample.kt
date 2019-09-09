package io.micronaut.docs.aop.scheduled

import io.micronaut.scheduling.annotation.Scheduled

import javax.inject.Singleton

@Singleton
class ScheduledExample {

    // tag::fixedRate[]
    @Scheduled(fixedRate = "5m")
    internal fun everyFiveMinutes() {
        println("Executing everyFiveMinutes()")
    }
    // end::fixedRate[]

    // tag::fixedDelay[]
    @Scheduled(fixedDelay = "5m")
    internal fun fiveMinutesAfterLastExecution() {
        println("Executing fiveMinutesAfterLastExecution()")
    }
    // end::fixedDelay[]

    // tag::cron[]
    @Scheduled(cron = "0 15 10 ? * MON")
    internal fun everyMondayAtTenFifteenAm() {
        println("Executing everyMondayAtTenFifteenAm()")
    }
    // end::cron[]

    // tag::initialDelay[]
    @Scheduled(initialDelay = "1m")
    internal fun onceOneMinuteAfterStartup() {
        println("Executing onceOneMinuteAfterStartup()")
    }
    // end::initialDelay[]

    // tag::configured[]
    @Scheduled(fixedRate = "\${my.task.rate:5m}",
            initialDelay = "\${my.task.delay:1m}")
    internal fun configuredTask() {
        println("Executing configuredTask()")
    }
    // end::configured[]
}
