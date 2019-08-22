package io.micronaut.docs.aop.around

import javax.inject.Singleton

// tag::example[]
@Singleton
class NotNullExample {

    @NotNull
    void doWork(String taskName) {
        println("Doing job: " + taskName)
    }
}
// end::example[]
