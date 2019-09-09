package io.micronaut.docs.aop.scheduled

import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler

import javax.inject.Inject
import javax.inject.Named

class TaskSchedulerInjectExample {
    // tag::inject[]
    @Inject
    @Named(TaskExecutors.SCHEDULED)
    TaskScheduler taskScheduler
    // end::inject[]
}
