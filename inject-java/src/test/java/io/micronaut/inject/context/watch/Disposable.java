package io.micronaut.inject.context.watch;

import java.util.concurrent.atomic.AtomicInteger;

public interface Disposable {
    AtomicInteger destroyed();
}
