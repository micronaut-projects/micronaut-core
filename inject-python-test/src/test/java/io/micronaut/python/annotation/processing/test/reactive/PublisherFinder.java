package io.micronaut.python.annotation.processing.test.reactive;

import org.reactivestreams.Publisher;

import java.util.function.Consumer;

public interface PublisherFinder<T> {
    Publisher<T> find(String id, Consumer<T> callback);

    default Publisher<T> find(String id) {
        return find(id, null);
    }
}
