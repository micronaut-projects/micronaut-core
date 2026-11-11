package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.context.watch.ExecutableMethodChange;
import io.micronaut.context.watch.ExecutableMethodWatcher;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * A processor that implements the watcher interface but never registers a watch: it is adapted like any other.
 */
@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
@Requires(property = "spec.watchers", value = "true")
public class UnwatchedTickProcessor implements ExecutableMethodProcessor<Tick>, ExecutableMethodWatcher<Tick> {
    public final List<String> processed = new ArrayList<>();

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        processed.add(method.getMethodName());
    }

    @Override
    public void onChange(ExecutableMethodChange<Tick> change) {
        throw new AssertionError("never registered as a watcher");
    }
}
