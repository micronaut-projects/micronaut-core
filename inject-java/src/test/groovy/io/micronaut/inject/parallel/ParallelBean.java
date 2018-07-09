package io.micronaut.inject.parallel;

import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

@Singleton
@Parallel
@Requires(property = "parallel.bean.enabled")
public class ParallelBean {
    public ParallelBean() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
