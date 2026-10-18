package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.reload.ClassChangeEvent;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A listener of the class change event, which records when it ran.
 */
@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
public class ClassChangeOrder implements ApplicationEventListener<ClassChangeEvent> {
    public static final List<String> SEEN = new CopyOnWriteArrayList<>();

    @Override
    public void onApplicationEvent(ClassChangeEvent event) {
        SEEN.add("listener");
    }
}
