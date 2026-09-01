package io.micronaut.inject.lifecycle.registrationclose;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Factory
public class ElementFactory {

    static final AtomicInteger CONTAINER_CREATIONS = new AtomicInteger();

    @Bean
    @Singleton
    List<ContainedElement> elements() {
        CONTAINER_CREATIONS.incrementAndGet();
        return List.of(new ContainedElement(), new ContainedElement());
    }

    @Bean
    @Singleton
    List<StoppableElement> stoppableElements() {
        return List.of(new StoppableElement());
    }
}
