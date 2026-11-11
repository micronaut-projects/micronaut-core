package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * A processor written before watches existed: it is fed additions only, through the adapter.
 */
@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
public class LegacyTickProcessor implements ExecutableMethodProcessor<Tick> {
    public final List<String> processed = new ArrayList<>();

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        processed.add(method.getMethodName());
    }
}
