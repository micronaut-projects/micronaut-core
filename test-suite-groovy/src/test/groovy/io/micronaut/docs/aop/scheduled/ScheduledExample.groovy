/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.aop.scheduled

import io.micronaut.scheduling.annotation.Scheduled

import javax.inject.Singleton

@Singleton
class ScheduledExample {

    // tag::fixedRate[]
    @Scheduled(fixedRate = "5m")
    void everyFiveMinutes() {
        println "Executing everyFiveMinutes()"
    }
    // end::fixedRate[]

    // tag::fixedDelay[]
    @Scheduled(fixedDelay = "5m")
    void fiveMinutesAfterLastExecution() {
        println "Executing fiveMinutesAfterLastExecution()"
    }
    // end::fixedDelay[]

    // tag::cron[]
    @Scheduled(cron = "0 15 10 ? * MON")
    void everyMondayAtTenFifteenAm() {
        println "Executing everyMondayAtTenFifteenAm()"
    }
    // end::cron[]

    // tag::initialDelay[]
    @Scheduled(initialDelay = "1m")
    void onceOneMinuteAfterStartup() {
        println "Executing onceOneMinuteAfterStartup()"
    }
    // end::initialDelay[]

    // tag::configured[]
    @Scheduled(fixedRate = '${my.task.rate:5m}',
            initialDelay = '${my.task.delay:1m}')
    void configuredTask() {
        println "Executing configuredTask()"
    }
    // end::configured[]
}
