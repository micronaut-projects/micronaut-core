package io.micronaut.inject.provider.injectionpoint;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
public class Consumer {

    @Inject
    InjectedAt<String> greeting;

    @Inject
    @Named("special")
    InjectedAt<String> qualified;

    final InjectedAt<Integer> count;

    InjectedAt<Long> total;

    public Consumer(InjectedAt<Integer> count) {
        this.count = count;
    }

    @Inject
    void setTotal(InjectedAt<Long> total) {
        this.total = total;
    }
}
