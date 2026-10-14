package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A processor of an annotation that is not processed on startup, so that its definition is deprecated, which received
 * the pool, so that recreating the pool destroys it as a dependent.
 */
@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
@Requires(property = "buzz-processor.enabled", value = "true")
public class BuzzProcessor implements ExecutableMethodProcessor<Buzz> {
    public final Pool pool;
    public final List<String> processed = new CopyOnWriteArrayList<>();

    public BuzzProcessor(Pool pool) {
        this.pool = pool;
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        processed.add(method.getMethodName());
    }
}
