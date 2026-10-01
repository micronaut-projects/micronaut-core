package io.micronaut.reflection;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * A bean whose nullable values declare a default, a type the processors never saw. Its nullable annotation
 * targets the parameters, which reflection reads; a type use one it does not.
 */
@Singleton
public class Pacer {

    final @Nullable Duration warnWait;
    final @Nullable String label;

    @Inject
    public Pacer(@Value("${pacer.warn-wait}") @Bindable(defaultValue = "2s") @Nullable Duration warnWait,
                 @Value("${pacer.label}") @Nullable String label) {
        this.warnWait = warnWait;
        this.label = label;
    }
}
