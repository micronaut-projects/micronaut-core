package io.micronaut.docs.aop.around;

import javax.inject.Singleton;

// tag::example[]
@Singleton
public class NotNullExample {

    @NotNull
    void doWork(String taskName) {
        System.out.println("Doing job: " + taskName);
    }
}
// end::example[]
