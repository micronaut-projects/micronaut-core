package io.micronaut.inject.context.watch;

import io.micronaut.context.WatchableBeanContext;
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
 * A processor that is a watcher and registers itself as the watcher of its own method watch: the adapter stands aside.
 */
@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
@Requires(property = "spec.watchers", value = "true")
public class SelfWatchingTickProcessor implements ExecutableMethodProcessor<Tick>, ExecutableMethodWatcher<Tick> {
    public final List<String> processed = new ArrayList<>();
    public final List<ExecutableMethodChange<Tick>> changes = new ArrayList<>();

    public SelfWatchingTickProcessor(WatchableBeanContext context) {
        context.methods(Tick.class).watch(this);
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        processed.add(method.getMethodName());
    }

    @Override
    public void onChange(ExecutableMethodChange<Tick> change) {
        changes.add(change);
    }
}
