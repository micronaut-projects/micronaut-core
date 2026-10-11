package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.context.scope.Refreshable

import java.util.concurrent.atomic.AtomicInteger

@Refreshable("cache")
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec")
class Cache {
    static final AtomicInteger CREATED = new AtomicInteger()
    final int number = CREATED.incrementAndGet()

    int number() { number }
}
