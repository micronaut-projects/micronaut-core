package io.micronaut.inject.executable.placeholder;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
@Requires(property = "spec.name", value = "ExecutableMethodContextIsolationSpec")
public class TaggedProcessor implements ExecutableMethodProcessor<Tagged> {

    public final List<String> seen = new CopyOnWriteArrayList<>();

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        seen.add(method.stringValue(Tagged.class).orElse(null));
    }
}
