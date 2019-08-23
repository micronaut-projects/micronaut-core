package io.micronaut.docs.aop.around

import javax.inject.Singleton

// tag::example[]
@Singleton
open class NotNullExample {

    @NotNull
    open fun doWork(taskName: String?) {
        println("Doing job: $taskName")
    }
}
// end::example[]
